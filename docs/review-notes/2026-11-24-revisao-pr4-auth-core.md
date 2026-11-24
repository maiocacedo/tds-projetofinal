# Revisão guiada do PR #4 — Núcleo de Autenticação (T1–T5)

> Notas da conversa de revisão de 2026-11-24, arquivo por arquivo, sobre o PR #4 (`feat/checkpoint-3-auth-core` → `main`).
> Objetivo: entender **o que cada peça faz** e **por que foi feita assim**.
> Referências: spec `docs/superpowers/specs/2026-11-23-checkpoint-3-security-design.md`, rotas `docs/api-routes.md`.

---

## Visão geral: os passos de uma requisição

```
  PASSO 1: conseguir o token          PASSO 2: usar o token               PASSO 3: acessar a casa
  POST /oauth/token                   /api/...                            /api/houses/{id}/...
  ────────────────────────            ─────────────────────────           ──────────────────────────
  client ok? usuário/senha ok?        token válido? tem permissão?        o usuário mora nessa casa?
        │                                   │                                   │
  erro → OAuthException               erro → JsonAuthHandlers             erro → HouseAccessValidator
         400 invalid_grant ...               401 Authentication required        + GlobalExceptionHandler
         401 invalid_client                  403 Insufficient permission        403 User does not belong...
```

Cada passo tem o seu tratador de erro. As **duas camadas de autorização** são:
1. **O que você pode fazer:** permissão global no banco + scope do token, checadas pelo `@PreAuthorize`.
2. **Em qual casa:** pertencimento à casa, checado pelo `HouseAccessValidator` nos services.

---

## Por que o `/api/auth/login` deixou de existir?

O login **não sumiu, mudou de endereço**. Agora é `POST /oauth/token` com `grant_type=password`, o *Password Flow* do OAuth 2 visto nos tópicos 03 e 04 da disciplina.

```
Antes:  POST /api/auth/login   {"email","senha"}                         → { token }
Agora:  POST /oauth/token      grant_type=password&username=&password=
        + Authorization: Basic (client)                                  → { access_token, ... } + cookie de refresh
```

Foi a decisão Q1 da rodada 2 do grilling (opção A: remover). Os motivos:
- **Uma porta só para proteger.** O `/oauth/token` autentica o client, valida o scope, cria o refresh/cookie e relê as permissões do banco. Um segundo endpoint teria de repetir tudo isso, e qualquer diferença vira brecha.
- O login antigo emitia o JWT do sistema que foi removido (`JwtTokenProvider`).
- É o modelo das aulas.

Custo: é uma **breaking change**. Postman e testes já foram atualizados; o cadastro (`/api/auth/register`) continua igual.

**Conclusão da conversa:** as camadas a mais de segurança estão *dentro* da única porta (client, scope, BCrypt com tempo constante, parâmetros só no corpo, permissões lidas do banco, refresh rotativo). Ter uma porta só garante que ninguém pula nenhuma dessas etapas.

---

## Modelo de permissões

### `User.permissions` (`@ManyToMany`)
```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(name = "user_permissions",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
private Set<Permission> permissions = new HashSet<>();
```
- Relação **muitos-para-muitos** entre `users` e `permissions`, guardada na tabela intermediária `user_permissions` (migration V3).
- **`LAZY`**: as permissões só são carregadas se alguém chamar `getPermissions()`.
- **`Set`**: sem duplicatas (`Permission` define `equals`/`hashCode` pelo nome).
- Usado no **cadastro** (8 permissões padrão), no **admin** (substitui o conjunto) e no **bootstrap** (9 permissões ao admin).
- Para emitir o JWT, o código usa `UserRepository.findPermissionNamesByUserId`: um único SELECT que traz só os nomes.

### O papel de um repository (`PermissionRepository`)
- O repository é a camada que **fala com o banco**. Com Spring Data JPA, basta declarar a **interface**, e o Spring gera a implementação.
- `JpaRepository` já traz `save`, `findById`, `findAll`, `count`, `delete`.
- Métodos **derivados do nome** viram SQL:

| Método | SQL gerado | Uso |
|---|---|---|
| `findByNameIn(names)` | `WHERE name IN (...)` | Admin: validar e buscar as permissões pedidas |
| `findByNameNot(name)` | `WHERE name <> ?` | Cadastro: todas menos `ROLE_ADMIN_USERS` |

- Quando o nome não basta, usa-se `@Query` (ex.: o JOIN em `findPermissionNamesByUserId`).

---

## Tokens

### `AccessTokenService.issue`: fabrica o JWT
1. Lê do banco **naquele momento** as permissões do usuário. Por isso a revogação feita pelo admin vale no próximo token.
2. Junta os scopes: `"read write"`.
3. Pega a hora de um `Clock` injetado, o que permite controlar o tempo nos testes.
4. Monta as claims: `sub` (id), `iat`, `exp` (+15 min), `scope`, `authorities`, `client_id`.
5. Assina com **HS256** usando `JWT_SECRET`.

Na volta, o Resource Server confere a assinatura e o `exp`, transforma `scope` em `SCOPE_*` e `authorities` em `ROLE_*`, e usa o `sub` como `authentication.getName()`.

> O payload do JWT é **assinado, não criptografado**: qualquer um lê, ninguém altera. Por isso ele não carrega dados sensíveis.

### `OAuthTokenService`: o orquestrador (papel de um **service**)
Ele decide **para quem** é o token e **com quais scopes**; quem fabrica são os outros services.
- **`passwordGrant`** (login): valida a entrada → resolve os scopes (subconjunto dos do client; o mobile só tem `read`) → confere a senha com BCrypt. O `dummyHash` faz o BCrypt rodar mesmo com e-mail inexistente, para o tempo de resposta não denunciar quais e-mails existem → emite refresh (família nova) e access token.
- **`refreshGrant`**: rotaciona o refresh e emite um access token novo com o **mesmo usuário e scope** da emissão original, com as permissões relidas do banco.
- **`revoke`**: logout.
- **`TokenResult.toString()`** mascara os tokens, para um log acidental não expor credenciais.

**Papel de cada camada:**

| Camada | Papel |
|---|---|
| Controller | Fala HTTP: recebe, extrai, devolve |
| **Service** | **Decide**: regras de negócio e orquestração |
| Repository | Fala com o banco |

### `RefreshTokenService`: o "bixão" do refresh token
Cuida do ciclo de vida inteiro do refresh:
- **`issue`**: 32 bytes aleatórios; **só o hash SHA-256 vai para o banco**; grava dono, client, scope, validade (7 dias) e **família** (todos os refresh de um mesmo login).
- **`rotate`**: checagens **em ordem**:
  1. existe? → 2. revogado? → 3. **já usado? → revoga a família inteira** (sinal de roubo) → 4. expirado? → 5. mesmo client? (recusa **sem** revogar) → 6. **marca como usado de forma atômica** → 7. emite o próximo, na mesma família.
- **`revoke`**: logout; revoga a família (só se o token for daquele client). É idempotente.

```
login   → R1
refresh → usa R1 → nasce R2
refresh → usa R2 → nasce R3
alguém reusa R1  → 🚨 família inteira morre → login de novo
```

Achados do Gauntlet que moldaram esse código:
- o UPDATE atômico também exige `revoked_at IS NULL` e não expirado, senão um logout no meio de uma corrida deixava a sessão viva;
- a checagem de reuso vem **antes** da de client, senão um token roubado, apresentado por outro client, não disparava a revogação.

### `RefreshTokenCookieFactory`: só embala
- **Não gera** token e **não roda de tempos em tempos**. Monta o `Set-Cookie`:
  `refresh_token=...; HttpOnly; SameSite=Strict; Path=/oauth/token; Max-Age=7 dias; Secure (COOKIE_SECURE)`.
- `clear()` monta o cookie vazio com `Max-Age=0` (logout).
- A renovação é **sob demanda**: o cliente (frontend) chama `grant_type=refresh_token` quando o access token expira.

### `TokenController`: o recepcionista
Confere o formulário e **diz para onde vai**:
```
POST /oauth/token
   ├── grant_type=password       → OAuthTokenService.passwordGrant
   ├── grant_type=refresh_token  → OAuthTokenService.refreshGrant   (refresh lido do cookie)
   └── outro                     → erro
DELETE /oauth/token/revoke       → OAuthTokenService.revoke
```
- Valida o formato: parâmetros **só no corpo** (nada na URL) e **sem repetição**.
- Na volta, anexa o cookie montado pela factory + JSON + `Cache-Control: no-store`.
- Tem `@ExceptionHandler` local para `OAuthException`.

---

## Clients e erros

### `ClientAuthenticator`: autentica o **app** (não o usuário)
- **No startup:** valida a configuração e **impede a API de subir** se houver lista vazia, `clientId` ou `clientSecret` em branco, scopes vazios ou id duplicado. A mensagem de erro mostra só o id, nunca o secret.
- **A cada `/oauth/token`:** lê o `Basic base64(client_id:secret)`, compara o secret em **tempo constante** (`MessageDigest.isEqual`) e responde o mesmo `401 invalid_client` para client inexistente e secret errado.
- Valida só contra os clients das propriedades, **nunca** contra a tabela de usuários.

Ordem no `/oauth/token`: `ClientAuthenticator` (app confiável?) → `OAuthTokenService` (usuário e senha?) → `AccessTokenService` (emite).

### `OAuthException`: erros do **passo 1** (conseguir o token)
| Fábrica | HTTP | Quando |
|---|---|---|
| `invalidClient` | 401 | client ou secret inválido |
| `invalidGrant` | 400 | senha errada, refresh inválido, reusado ou expirado |
| `invalidScope` | 400 | scope além do permitido ao client |
| `unsupportedGrantType` | 400 | `grant_type` desconhecido |
| `invalidRequest` | 400 | parâmetro faltando, na URL ou repetido |

Formato OAuth: `{"error": "...", "error_description": "..."}`. Senha errada é **400** (problema da credencial do usuário); **401** fica para o client.

### `JsonAuthHandlers`: erros do **passo 2** (usar o token)
- `commence`: **401** `{"error":"Authentication required"}` + `WWW-Authenticate: Bearer` (ou `Bearer error="invalid_token"`).
- `handle`: **403** `{"error":"Insufficient permission"}`.
- **Por que não fica no `GlobalExceptionHandler`:** o 401 nasce nos **filtros** do Spring Security, antes do controller; o `@RestControllerAdvice` nem fica sabendo. A `AccessDeniedException` do `@PreAuthorize` também precisa subir até o filtro, por isso é **proibido** um handler genérico de `Exception` (transformaria 403 em 500).
- Ganhos: **isolamento** (segurança separada do negócio), **segurança** (resposta curta, sem stack trace nem pista do motivo) e **padrão** (`{"error": ...}` em toda a API).

---

## Administração

### `AdminBootstrap`: cria o admin **na inicialização** (não verifica requisições)
É um `ApplicationRunner`: roda **uma vez**, ao subir.

| Situação | Ação |
|---|---|
| `ADMIN_PASSWORD` vazio | Não faz nada (não há admin) |
| Conta não existe | **Cria** com BCrypt e as 9 permissões |
| Conta existe e já é admin | Garante as 9 permissões, **sem mexer na senha** |
| Conta existe e **não** é admin | **Recusa** promover (`WARN`) |

O último caso é a correção do **achado bloqueante** do Gauntlet. Sem ela, alguém registraria `admin@nossoteto.local` antes do admin existir e viraria admin no próximo restart, com a própria senha.

Quem **verifica** se alguém é admin nas requisições é o `@PreAuthorize("hasAuthority('ROLE_ADMIN_USERS') ...")` do `AdminController`.

### `AdminService`: executa o que o admin pede
O controller faz a triagem (é admin? tem scope?); o service **executa**:
- `listUsers`: lista usuários e permissões (sem hash de senha).
- `replacePermissions`: substitui o conjunto, com as regras: lista não nula, usuário existe, nomes não vazios e existentes no catálogo, e o **admin não pode tirar o próprio `ROLE_ADMIN_USERS`**. Lista vazia `[]` equivale a suspender o usuário.
- A mudança vale **no próximo token** (até 15 min), porque o access token atual continua válido até expirar.

---

## Profiles: Basic Auth

### `BasicSecurityConfig`: segurança **alternativa** por HTTP Basic (tópicos 01 e 02)
- Só carrega com `@Profile("basic-security")`: `mvn spring-boot:run -Dspring-boot.run.profiles=basic-security`.
- E-mail e senha em **toda** requisição; sem token, sem expiração, sem logout.
- `DatabaseUserDetailsService`: busca por e-mail, confere com BCrypt e devolve o **id** como username (os controllers continuam iguais), com permissões + `SCOPE_read`/`SCOPE_write`.
- Entry point próprio: `401` + `WWW-Authenticate: Basic realm="nosso-teto"`.

### `@Profile("!basic-security")`
O `!` significa **"não"**: carregue quando o `basic-security` **não** estiver ativo. Ou seja, na execução normal **o Basic não é usado**, e o OAuth (`OAuthSecurityConfig`, `TokenController`, etc.) está ligado. Os dois modos se excluem para nunca disputarem as mesmas requisições. Classes sem `@Profile` (ex.: `CommonSecurityConfig`) carregam nos dois.

O Basic **não é código morto**: o `BasicSecurityProfileIntegrationTest` o testa no CI a cada PR.

### O Basic é um plano B se a API cair?
**Não.** Ele vive no **mesmo processo** (cai junto), o profile é escolhido **só no startup** (não há troca automática) e, mesmo trocado à mão, **rebaixaria** a segurança e quebraria os clientes (que usam `/oauth/token`). Disponibilidade vem de infraestrutura: várias instâncias, reinício automático, health check, réplica do banco. **Ainda não existe no projeto** e foi registrado em `docs/entrega-final-ideias.md` para a entrega final.

---

## Configuração: `application.properties`
O painel de configuração: tudo que muda entre ambientes fica aqui, fora do código.
- `${VAR}` = obrigatória; `${VAR:padrão}` = usa o padrão se faltar.
- `spring.config.import=optional:file:.env[.properties]` importa o `.env` (ignorado pelo Git). Variáveis de ambiente reais têm prioridade.
- **Banco:** MySQL local (`root/root` por padrão), `ddl-auto=validate` (o Hibernate só confere) e Flyway (quem cria as tabelas).
- **Tokens:** `JWT_SECRET` (obrigatória), TTL de 15 min / 7 dias, `COOKIE_SECURE`.
- **Clients:** `nosso-teto-web` (`read,write`) e `nosso-teto-mobile` (`read`).
- **Admin, CORS e criptografia:** `ADMIN_EMAIL`/`ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `APP_CRYPTO_KEY` (obrigatória e **imutável** depois do primeiro uso).
- Nos testes, `src/test/resources/application.properties` substitui este arquivo (H2, segredos fixos), e por isso o CI roda sem MySQL e sem `.env`.

---

## Também decidido nesta conversa
- **`.env`** para os segredos locais (`.env.example` versionado, `.env` ignorado). Commit `cbd2154`.
- **README** com o projeto, as decisões e como executar. Commit `da75727`.
- **Backlog da entrega final** (07/12): `docs/entrega-final-ideias.md`, só com ideias, a discutir. Commit `2de8b0c`.
