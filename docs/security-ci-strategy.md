# Estratégia de Segurança, CI e Testes E2E - Nosso Teto

Este documento define os pilares técnicos para garantir a qualidade, segurança e entrega contínua do projeto, alinhado aos Requisitos Não Funcionais (RNFs) levantados.

## 1. Segurança e Autorização (OAuth 2 Resource Server + Permissões + Criptografia)

### 1.1. Autenticação OAuth 2 + JWT (Stateless)
- A API atua como **Spring Security OAuth 2 Resource Server**, validando **JSON Web Tokens (JWT)** assinados com **HMAC-SHA256 (`HS256`)** usando a chave obrigatória `JWT_SECRET` (mínimo 32 bytes, validada no startup e sem valor padrão em `application.properties`).
- A emissão e renovação de tokens ocorrem no endpoint próprio `POST /oauth/token` (`application/x-www-form-urlencoded`, parâmetros aceitos **somente no corpo** e nunca repetidos), com autenticação do client via `Authorization: Basic` validada em tempo constante (`MessageDigest.isEqual`) contra os clients configurados (`nosso-teto-web` com scopes `read write`; `nosso-teto-mobile` com scope `read`).
- **Grants suportados:**
  - `password`: recebe `username` (e-mail) e `password` (verificado com **BCrypt** em tempo constante) e `scope` opcional (subconjunto dos scopes do client).
  - `refresh_token`: lê o refresh token diretamente do cookie `refresh_token` e executa rotação atômica.
- **TTLs:** Access Token curto de **15 minutos** (`JWT_ACCESS_TTL=PT15M`) e Refresh Token de **7 dias** (`JWT_REFRESH_TTL=P7D`).
- **Refresh Token em cookie e detecção de reuso:** o valor bruto (32 bytes aleatórios via `SecureRandom` em base64url) trafega apenas no cookie `refresh_token` (`HttpOnly; SameSite=Strict; Path=/oauth/token`; `Secure` controlado por `COOKIE_SECURE`). Apenas o hash **SHA-256** é persistido na tabela `refresh_tokens`. A rotação usa compare-and-set atômico (`used_at IS NULL`); qualquer tentativa de reuso revoga toda a família de tokens (`noRollbackFor`) e responde `400 invalid_grant`.
- **Logout (`DELETE /oauth/token/revoke`):** autenticado com Basic do client, revoga a família do refresh token do cookie de forma idempotente (`204 No Content`) e limpa o cookie (`Max-Age=0`).
- `/api/auth/register` permanece público (atribuindo as 8 permissões padrão de usuário); o endpoint legado `/api/auth/login` foi removido.

### 1.2. Profile Alternativo (`basic-security`)
- Quando `SPRING_PROFILES_ACTIVE=basic-security` está ativo, os endpoints `/oauth/**` ficam desativados e `/api/**` autentica via **HTTP Basic** diretamente contra a tabela `users` (`DatabaseUserDetailsService`), utilizando `user.id` como `username` e atribuindo as permissões do banco somadas a `SCOPE_read` e `SCOPE_write`. Falhas retornam `401` JSON com `WWW-Authenticate: Basic realm="nosso-teto"`.

### 1.3. Autorização em Duas Camadas (Permissões, Scopes e Multi-Tenancy)
- **Camada 1 (`@PreAuthorize` + `@EnableMethodSecurity`):** cada endpoint de `/api/**` exige a permissão granular persistida nas tabelas `permissions` / `user_permissions` (catálogo de 9 permissões) e o scope correspondente do client (`SCOPE_read` ou `SCOPE_write`). As permissões são relidas do banco a cada emissão de JWT (inclusive no `refresh_token`).
  - Requisições sem token válido recebem `401 {"error":"Authentication required"}`; requisições autenticadas sem permissão ou scope recebem `403 {"error":"Insufficient permission"}`.
  - A administração de permissões ocorre em `GET /api/admin/users` e `PUT /api/admin/users/{id}/permissions` (`ROLE_ADMIN_USERS`), com `AdminBootstrap` que cria o administrador apenas se `ADMIN_PASSWORD` estiver definido e **nunca** promove uma conta pré-existente que não possua `ROLE_ADMIN_USERS`.
- **Camada 2 (Multi-Tenancy lógico por casa):** após passar pela camada de permissão e scope, os services (`HouseAccessValidator`) validam se o `user_id` autenticado (`authentication.getName()`) mora ou administra a `{houseId}` acessada, retornando `403` caso tente operar dados de outra casa.

### 1.4. CORS com Credenciais e Criptografia em Repouso
- **CORS:** configurado em `CommonSecurityConfig` para todas as filter chains (`/oauth/**` e `/api/**`) com `allowCredentials=true` e lista explícita de origens via `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:5173`, nunca `*`).
- **Criptografia em repouso (AES-256-GCM):** os campos sensíveis `allergies` e `bio` de `user_profiles` (migrados para `MEDIUMTEXT` na `V4`) são cifrados via `AesGcmFieldCipher` / `EncryptedStringConverter` com IV aleatório de 12 bytes por cifragem e tag de autenticação de 128 bits (formato `v1:<base64(IV || ciphertext || tag)>`), utilizando a chave obrigatória `APP_CRYPTO_KEY` (32 bytes em Base64).

### 1.5. Segurança de Anexos (Comprovantes Financeiros)
- Comprovantes de despesas não são arquivos públicos: ficam armazenados fora da raiz pública (pasta configurada em `app.upload.dir`, padrão `uploads`).
- O endpoint `GET /api/houses/{houseId}/expenses/{expenseId}/attachment` exige `ROLE_MANAGE_EXPENSES`, `SCOPE_read` e validação de morador da casa no `HouseAccessValidator` antes de fazer o stream do arquivo.

## 2. Continuous Integration (CI)

Utilizamos o **GitHub Actions** (`.github/workflows/ci.yml`) para impedir que código quebrado ou abaixo do critério de cobertura seja mesclado na `main`.

### 2.1. Pipeline de Backend (Spring Boot)
Disparado no `push` para `main` e em todo `pull_request` (job `build` em `ubuntu-latest`):
1. Checkout do código (`actions/checkout@v4`).
2. Setup Java **JDK 17** (Temurin) com cache do Maven (`actions/setup-java@v4`).
3. Execução de `mvn -B verify`:
   - Compilação e execução dos **Testes Unitários e de Integração** (JUnit 5 + MockMvc + H2 + Flyway).
   - **Checkstyle** (`maven-checkstyle-plugin` 3.3.1, `google_checks.xml`) em **modo aviso** (`failOnViolation=false`).
   - **JaCoCo** (`0.8.12`) com **gate obrigatório de 80% de cobertura de linhas** (`BUNDLE`/`LINE`/`COVEREDRATIO >= 0.80`) — o build falha abaixo de 80%.
4. Upload dos relatórios `target/site/jacoco` (`jacoco-report`) e `target/checkstyle-result.xml` (`checkstyle-report`) como artifacts (`if: always()`).

## 3. Estratégia de Testes E2E (End-to-End)

Embora os Checkpoints atuais sejam focados na API, a plataforma utilizará **Cypress** ou **Playwright** (quando o frontend iniciar) para simular o comportamento de um usuário real no navegador.

### 3.1. Cenários Críticos (Golden Paths)
Os testes E2E focarão nos fluxos que não podem falhar:
- **Cenário 1 (Match):** Cadastrar Usuário A -> Cadastrar Usuário B -> Usuário A cria Casa -> Usuário B pede Match -> Usuário A aprova -> Usuário B entra na casa.
- **Cenário 2 (Gestão):** Usuário logado -> Cadastra Despesa -> Confere divisão matemática (50/50) -> Marca como Paga -> Sistema atualiza o saldo.
- **Cenário 3 (Segurança):** Tentar acessar URL direta da dashboard de uma casa sem estar logado -> Ser redirecionado para o Login.

## 4. Como rodar localmente

Copie `.env.example` para `.env` na raiz do projeto e preencha ao menos `JWT_SECRET` e `APP_CRYPTO_KEY` (o próprio arquivo traz o comando PowerShell para gerar cada valor). O `.env` é ignorado pelo Git e importado pela API via `spring.config.import=optional:file:.env[.properties]`; variáveis de ambiente do sistema, se definidas, têm prioridade. Depois suba a API com `mvn spring-boot:run` a partir da raiz do projeto.

> ⚠️ `APP_CRYPTO_KEY` não pode mudar depois do primeiro uso: `allergies` e `bio` já cifradas ficariam ilegíveis.

Variáveis disponíveis:

| Variável | Obrigatória | Exemplo / padrão |
|---|---|---|
| `JWT_SECRET` | sim | ≥ 32 bytes |
| `APP_CRYPTO_KEY` | sim | `openssl rand -base64 32` |
| `ADMIN_PASSWORD` | não (sem ela não há admin) | — |
| `ADMIN_EMAIL` | não | `admin@nossoteto.local` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | não | `PT15M` / `P7D` |
| `COOKIE_SECURE` | não | `false` (local) · `true` (produção) |
| `CORS_ALLOWED_ORIGINS` | não | `http://localhost:5173` |
| `WEB_CLIENT_SECRET` / `MOBILE_CLIENT_SECRET` | não | `web-dev-secret` / `mobile-dev-secret` |
| `SPRING_PROFILES_ACTIVE` | não | `basic-security` para Basic Auth |

Exemplo no PowerShell:

```powershell
$env:JWT_SECRET = "dev-jwt-secret-with-at-least-32-bytes!"
$env:APP_CRYPTO_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
$env:ADMIN_PASSWORD = "admin-dev-password"
mvn spring-boot:run
```
