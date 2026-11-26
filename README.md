# Nosso Teto — Gestão e Match de Moradias Compartilhadas

API REST para quem divide moradia (repúblicas, apartamentos compartilhados). Ela ataca as duas principais fontes de atrito:

1. **Encontrar com quem morar.** Perfis detalhados, busca de casas com cálculo de compatibilidade e um fluxo de *match* (pedido de entrada + votação dos moradores).
2. **Organizar a casa.** Despesas divididas automaticamente ou por proposta votada, tarefas domésticas rotativas, lista de mercado colaborativa e um mural (dashboard) da casa.

Quem já mora junto pode usar só a parte de gestão.

> Projeto final da disciplina **Tecnologia em Desenvolvimento de Sistemas** — UTFPR, Campus Santa Helena (2026.2).
> Esta é a **API (backend)**. O frontend em React será entregue no próximo checkpoint.

---

## Sumário

- [Funcionalidades](#funcionalidades)
- [Stack](#stack)
- [Arquitetura](#arquitetura)
- [Segurança](#segurança)
- [Principais decisões](#principais-decisões)
- [Como executar](#como-executar)
- [Testes e CI](#testes-e-ci)
- [Testando a API com o Postman](#testando-a-api-com-o-postman)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Documentação complementar](#documentação-complementar)
- [Processo de desenvolvimento](#processo-de-desenvolvimento)

---

## Funcionalidades

| Módulo | O que faz |
|---|---|
| **Usuários e perfis** | Cadastro e perfil detalhado: orçamento, pets, fumante, profissão, biografia, alergias, nível de organização, tolerância a visitas, mobília própria. |
| **Casas e compatibilidade** | Cadastro de casas com regras e vagas, busca com filtros (aluguel máximo, pets, fumantes) e nota de compatibilidade de 0 a 100 entre candidato e casa. |
| **Match e votação** | O candidato pede para entrar e os moradores votam. A regra de votação é configurável por casa: unanimidade ou maioria. Ao ser aceito, o candidato pode cancelar os outros pedidos. |
| **Despesas** | Lançamento com comprovante (anexo acessível só aos moradores) e divisão igualitária automática. Divisões customizadas (percentual ou valor fixo) só valem se aprovadas por votação. Cada morador marca a própria parcela como paga. |
| **Tarefas domésticas** | Tarefas fixas ou rotativas, ciclo semanal e registro de atraso no fechamento do ciclo. |
| **Mercado** | Lista colaborativa: qualquer morador adiciona itens, assume a compra e marca como comprado. |
| **Dashboard** | Mural da casa com pendências financeiras e tarefas da semana. |
| **Administração** | Um admin da plataforma lista usuários e concede ou revoga permissões. |

A lista completa de rotas está em [`docs/api-routes.md`](docs/api-routes.md).

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.2 (Web, Data JPA, Security, OAuth2 Resource Server) |
| Banco de dados | MySQL 8, com migrations versionadas pelo **Flyway** |
| Persistência | Hibernate / JPA (`ddl-auto=validate`: o schema vem só das migrations) |
| Autenticação | OAuth 2 (Password Flow + Refresh Token) com **JWT** HS256 |
| Testes | JUnit 5, Mockito, MockMvc, spring-security-test, H2 (modo MySQL) |
| Qualidade | JaCoCo (gate de 80% de linhas), Checkstyle (Google, modo aviso) |
| CI | GitHub Actions |
| Testes de API | Postman (collection versionada no repositório) |

---

## Arquitetura

API REST em camadas MVC:

```
controller  →  service  →  repository  →  model (entidades JPA)
    ↑             ↑
   dto      HouseAccessValidator (o usuário pertence à casa?)
```

- **`controller`**: endpoints REST. Recebem DTOs, extraem o usuário autenticado (`authentication.getName()`) e delegam ao service.
- **`service`**: regras de negócio (votação, divisão de despesas, rotação de tarefas, compatibilidade).
- **`repository`**: Spring Data JPA.
- **`security`**: OAuth 2, JWT, refresh token, permissões, CORS e criptografia de campos.
- **`exception`**: `GlobalExceptionHandler` padroniza os erros como `{"error": "..."}` (400, 403 e 404).

O banco evolui por migrations Flyway em `src/main/resources/db/migration`:

| Migration | Conteúdo |
|---|---|
| `V1` | Usuários e casas |
| `V2` | Perfis, match, votos, despesas, divisões, tarefas e mercado |
| `V3` | Permissões (`permissions`, `user_permissions`) e refresh tokens |
| `V4` | `allergies` e `bio` em `MEDIUMTEXT` (comportam o texto cifrado) |

---

## Segurança

A segurança tem **duas camadas de autorização**, que operam juntas:

1. **O que você pode fazer.** São permissões globais, guardadas no banco, combinadas com o *scope* do token. Cada endpoint exige uma permissão do módulo e o scope `read` (leitura) ou `write` (escrita):
   ```java
   @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
   ```
2. **Em qual casa.** O `HouseAccessValidator` garante que o usuário só acesse dados da casa onde mora. Os papéis de morador e de admin da casa são checados no service.

### Fluxo de autenticação

```
POST /oauth/token  (Basic do client + grant_type=password, username, password)
   → 200 { access_token, token_type: "Bearer", expires_in: 900, scope, user_id }
   → Set-Cookie: refresh_token=...; HttpOnly; SameSite=Strict; Path=/oauth/token

GET /api/...  (Authorization: Bearer <access_token>)

POST /oauth/token  (grant_type=refresh_token; refresh lido do cookie)
   → novo access token + novo refresh no cookie (rotação)

DELETE /oauth/token/revoke  → logout: revoga a sessão e apaga o cookie
```

| Item | Como funciona |
|---|---|
| **Access token** | JWT HS256 de 15 min. Carrega `sub` (id do usuário), `scope` e `authorities` (permissões). |
| **Refresh token** | Opaco e aleatório (32 bytes), válido por 7 dias. Só o hash SHA-256 vai para o banco; o token em si vive apenas no cookie `HttpOnly`. |
| **Rotação e reuso** | Cada refresh gera um novo token e invalida o anterior com um UPDATE atômico. Se um token já usado for reapresentado (sinal de roubo), **todas** as sessões daquela família são revogadas. |
| **Logout** | Revoga a família no banco (não basta apagar o cookie). |
| **Clients OAuth** | `nosso-teto-web` (scopes `read write`) e `nosso-teto-mobile` (apenas `read`). |
| **Permissões** | 9 permissões por módulo. Usuário novo recebe 8; só o admin tem `ROLE_ADMIN_USERS`. Revogar uma permissão vale a partir do próximo token. |
| **Basic Auth** | Disponível pelo profile `basic-security`, alternativo ao OAuth. |
| **CORS** | Lista explícita de origens com credenciais (padrão: `http://localhost:5173`, o frontend React). |
| **Senhas** | BCrypt. |
| **Dados sensíveis** | `allergies` e `bio` cifradas no banco com **AES-256-GCM**. |
| **Erros** | `401` + `WWW-Authenticate` sem token válido; `403 {"error":"Insufficient permission"}` sem permissão ou scope. |

A matriz completa de permissões por endpoint está em [`docs/api-routes.md`](docs/api-routes.md#10-matriz-de-permissões-e-scopes).

---

## Principais decisões

| Decisão | Por quê |
|---|---|
| **OAuth 2 com Resource Server do Spring + endpoint `/oauth/token` próprio** | O módulo OAuth2 legado das aulas não roda no Spring Boot 3, e o Spring Authorization Server removeu o *password grant*. Implementar o endpoint mantém o fluxo visto em aula (client, grant types, scopes) sobre bibliotecas atuais. |
| **Refresh token opaco e persistido, em vez de JWT stateless** | Permite logout real (revogação no banco) e detecção de reuso, como recomenda a OAuth 2.0 Security BCP. |
| **Refresh em cookie `HttpOnly` + `SameSite=Strict`** | JavaScript não consegue ler o token, o que reduz o impacto de XSS, e o cookie só é enviado ao `/oauth/token`. |
| **Uma permissão por módulo + scope `read`/`write`** | Demonstra o mesmo mecanismo das aulas com 9 permissões em vez de ~25. O scope separa leitura de escrita, e o client mobile, só com `read`, mostra isso na prática. |
| **Autorização em duas camadas** | Permissão global não resolve "em qual casa"; isso é contextual e fica no service. |
| **Admin criado na inicialização (`ADMIN_PASSWORD`), não por migration** | Migration SQL não consegue gerar hash BCrypt nem ler variáveis de ambiente. O bootstrap **nunca** promove uma conta pré-existente, o que evita que alguém registre o e-mail do admin antes dele existir. |
| **Segredos sem valor padrão** | Sem `JWT_SECRET` (≥ 32 bytes) e `APP_CRYPTO_KEY`, a API não sobe. Isso evita rodar com chave conhecida por descuido. |
| **AES-256-GCM com prefixo de versão (`v1:`)** | Atende ao RNF09 (dados pessoais sensíveis). O IV é aleatório por valor e a tag autentica o dado. Valores antigos em texto puro são lidos e recifrados no próximo save, sem migração de dados. |
| **CI com gate de cobertura desde o início** | Todo PR roda a suíte completa. A `main` é protegida e exige o check verde. |

---

## Como executar

### Pré-requisitos

- **JDK 17 ou superior** (`java -version`)
- **Maven 3.9+** (`mvn -v`)
- **MySQL 8** rodando em `localhost:3306`. O banco `nossoteto_db` é criado automaticamente.

Não tem MySQL instalado? Suba um com Docker:

```bash
docker run -d --name nossoteto-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8.0
```

### 1. Clonar

```bash
git clone https://github.com/maiocacedo/tds-projetofinal.git
cd tds-projetofinal
```

### 2. Criar o `.env`

A API lê os segredos de um arquivo `.env` na **raiz do projeto**. Esse arquivo é ignorado pelo Git. Copie o modelo:

```powershell
# Windows (PowerShell)
Copy-Item .env.example .env
```

```bash
# Linux / macOS / Git Bash
cp .env.example .env
```

Depois **preencha** as variáveis no `.env`:

| Variável | Obrigatória | O que é |
|---|---|---|
| `JWT_SECRET` | **Sim** | Segredo do JWT, com **32 bytes ou mais**. Pode ser trocado depois; só invalida os tokens já emitidos. |
| `APP_CRYPTO_KEY` | **Sim** | Chave AES-256 em base64, com **exatamente 32 bytes**. ⚠️ **Nunca troque depois do primeiro uso**: alergias e biografias já gravadas ficariam ilegíveis. Guarde uma cópia em lugar seguro. |
| `ADMIN_PASSWORD` | Não | Senha do admin da plataforma (`admin@nossoteto.local`). Sem ela, não existe admin. |
| `ADMIN_EMAIL` | Não | E-mail do admin (padrão `admin@nossoteto.local`). |
| `CORS_ALLOWED_ORIGINS` | Não | Origens do frontend (padrão `http://localhost:5173`). |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | Não | Duração dos tokens (padrão `PT15M` / `P7D`). |
| `COOKIE_SECURE` | Não | `false` em desenvolvimento local (http); `true` em produção (https). |
| `WEB_CLIENT_SECRET` / `MOBILE_CLIENT_SECRET` | Não | Secrets dos clients OAuth (padrão `web-dev-secret` / `mobile-dev-secret`). |

**Gerando os valores:**

```powershell
# Windows (PowerShell): copie cada saída para o .env
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)   # JWT_SECRET
$k = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($k); [Convert]::ToBase64String($k)   # APP_CRYPTO_KEY
```

```bash
# Linux / macOS / Git Bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # APP_CRYPTO_KEY
```

Um `.env` preenchido fica assim (valores **ilustrativos**, gere os seus):

```properties
JWT_SECRET=q3V0b2RlLXNlZ3JlZG8tYWxlYXRvcmlvLWNvbS1tYWlzLWRlLTMyLWJ5dGVzLWFxdWk=
APP_CRYPTO_KEY=Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6cXV4Zm9vYmFyYmE=
ADMIN_PASSWORD=MinhaSenhaDeAdmin123
```

> **MySQL com outro usuário/senha?** O padrão é `root` / `root`. Para mudar, acrescente ao `.env` as chaves do Spring, que têm prioridade sobre o `application.properties`:
> ```properties
> spring.datasource.username=meu_usuario
> spring.datasource.password=minha_senha
> ```
> Variáveis de ambiente do sistema com o mesmo nome também funcionam e têm prioridade sobre o `.env`.

### 3. Rodar a API

A partir da **raiz do projeto**, que é onde fica o `.env`:

```bash
mvn spring-boot:run
```

Ou gere o jar e execute:

```bash
mvn -DskipTests package
java -jar target/nosso-teto-api-0.0.1-SNAPSHOT.jar
```

A API sobe em **http://localhost:8080**. Na primeira execução, o Flyway cria todas as tabelas (V1 a V4) e, se `ADMIN_PASSWORD` estiver definido, o admin é criado. O log mostra `Platform admin created for admin@nossoteto.local`.

> Rodando pela IDE (IntelliJ/VS Code), confira se o *working directory* da configuração de execução é a raiz do projeto, senão o `.env` não é encontrado.

### 4. Primeira requisição

```bash
# 1. Cadastrar um usuário
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nome":"Ana","email":"ana@exemplo.com","senha":"senha123"}'

# 2. Obter o token (Basic do client "nosso-teto-web:web-dev-secret")
curl -X POST http://localhost:8080/oauth/token \
  -u nosso-teto-web:web-dev-secret \
  -d grant_type=password -d username=ana@exemplo.com -d password=senha123

# 3. Usar o access_token retornado
curl http://localhost:8080/api/users/me -H "Authorization: Bearer <access_token>"
```

### Alternativa: profile Basic Auth

Para usar HTTP Basic (e-mail e senha em cada requisição) em vez do OAuth:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=basic-security
curl http://localhost:8080/api/users/me -u ana@exemplo.com:senha123
```

---

## Testes e CI

```bash
mvn verify
```

Esse comando roda os testes unitários e de integração (H2 em modo MySQL, sem precisar de banco nem de `.env`), gera o relatório de cobertura em `target/site/jacoco/index.html` e **falha se a cobertura de linhas ficar abaixo de 80%**. O relatório do Checkstyle sai em `target/checkstyle-result.xml` (apenas aviso).

Para rodar uma classe específica:

```bash
mvn test -Dtest=RefreshTokenServiceTest
```

**CI:** o workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) roda `mvn -B verify` com JDK 17 em todo push para `main` e em todo pull request, e publica os relatórios de cobertura e Checkstyle como artifacts. A branch `main` é protegida: só aceita merge com o check `build` verde.

---

## Testando a API com o Postman

1. Importe [`NossoTeto.postman_collection.json`](NossoTeto.postman_collection.json).
2. Nas variáveis da collection, preencha **`adminPassword`** com o mesmo valor de `ADMIN_PASSWORD` do seu `.env`.
3. Com a API rodando, execute a collection **na ordem** (Collection Runner). Os scripts salvam tokens e IDs automaticamente.

| Pasta | Conteúdo |
|---|---|
| 1 | Cadastro e token OAuth (morador e candidato) |
| 2–8 | Perfis, casas, match, despesas, tarefas, mercado e dashboard |
| 9 | **Segurança**: refresh por cookie, 401 sem token, client mobile bloqueado na escrita (403), scope inválido, client errado, admin revoga permissão (403), logout e refresh após logout |

Também dá para rodar pela linha de comando com o [Newman](https://www.npmjs.com/package/newman):

```bash
npx newman run NossoTeto.postman_collection.json --env-var "adminPassword=<sua senha de admin>"
```

> Rode contra um banco **novo**: a collection cadastra e-mails fixos.

---

## Estrutura do projeto

```
.
├── .github/workflows/ci.yml          # pipeline de CI
├── .env.example                      # modelo das variáveis (copie para .env)
├── NossoTeto.postman_collection.json # collection do Postman
├── docs/                             # requisitos, rotas, schema, segurança, specs e planos
├── pom.xml
└── src
    ├── main
    │   ├── java/com/nossoteto
    │   │   ├── controller/           # endpoints REST
    │   │   ├── dto/                  # objetos de entrada/saída
    │   │   ├── exception/            # exceções e handler global
    │   │   ├── model/                # entidades JPA e enums
    │   │   ├── repository/           # Spring Data JPA
    │   │   ├── security/             # OAuth 2, JWT, refresh, permissões, CORS
    │   │   │   └── crypto/           # AES-256-GCM dos campos sensíveis
    │   │   └── service/              # regras de negócio
    │   └── resources
    │       ├── application.properties
    │       └── db/migration/         # migrations Flyway V1–V4
    └── test                          # testes unitários e de integração
```

---

## Documentação complementar

| Documento | Conteúdo |
|---|---|
| [`docs/requirements.md`](docs/requirements.md) | Requisitos funcionais e não funcionais, casos de uso |
| [`docs/api-routes.md`](docs/api-routes.md) | Todas as rotas, contrato OAuth e matriz de permissões |
| [`docs/database-schema.md`](docs/database-schema.md) | Modelo de dados |
| [`docs/security-ci-strategy.md`](docs/security-ci-strategy.md) | Estratégia de segurança, CI e variáveis de ambiente |
| [`docs/superpowers/specs/`](docs/superpowers/specs/) | Especificações de design de cada checkpoint |
| [`docs/superpowers/plans/`](docs/superpowers/plans/) | Planos de implementação de cada checkpoint |

---

## Processo de desenvolvimento

- **Spec antes do código.** Cada checkpoint começa com uma especificação de design e um plano de implementação em tarefas pequenas, versionados em `docs/superpowers/`.
- **TDD.** Cada tarefa escreve primeiro os testes que falham e só depois a implementação.
- **Conventional Commits** e **pull requests** revisados antes do merge na `main` protegida.
- **Revisão adversarial (Gauntlet Loop).** No Checkpoint 3, as tarefas críticas de segurança foram implementadas por um agente e revisadas por outro, isolado e somente leitura, até a aprovação sem achados. A revisão encontrou e corrigiu, entre outros, uma escalada de privilégio no bootstrap do admin, uma condição de corrida entre logout e refresh, e o estouro do limite de 64 KB do `TEXT` no MySQL causado pela criptografia.

---

**Autor:** Caio Macedo Lima da Cruz — equipe MaioCacedo · Professor: Giuvane Conti
