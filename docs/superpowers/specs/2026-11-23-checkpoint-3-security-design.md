# Especificação de Design — Checkpoint 3: Segurança da API (Nosso Teto)

**Data:** 2026-11-23
**Entrega:** 2026-11-30
**Escopo:** Basic Auth via Profile, OAuth 2 (Password Flow + Refresh Token) com JWT em Resource Server, refresh token em cookie com rotação, logout/revogação, CORS, permissões de acesso persistidas no banco (DESAFIO), criptografia de dados sensíveis (RNF09) e pipeline de CI.

Tópicos da disciplina cobertos: 01 Basic Auth · 02 Profile · 03/04 OAuth 2 + Password Flow · 05/06 JWT · 07 Access + Refresh · 08 Refresh no Cookie · 09 Refresh na requisição · 10 CORS · 11 Usuário no banco · 12 Permissões · 13 DESAFIO · 14 Logout.

---

## 1. Decisões (sessão de grilling 2026-11-23)

| # | Decisão |
|---|---|
| D1 | OAuth 2 via **Spring Resource Server** (`spring-boot-starter-oauth2-resource-server`) + endpoint próprio `POST /oauth/token` implementando `grant_type=password` e `grant_type=refresh_token`. Motivo: o `spring-security-oauth2` legado não roda no Boot 3 e o Spring Authorization Server removeu o grant `password`. |
| D2 | Profile `basic-security` (HTTP Basic contra o banco) alternativo; o padrão (nenhum profile) é OAuth. |
| D3 | Permissões granulares **por módulo** persistidas (`permissions`, `user_permissions`) + **scopes** (`read`/`write`) distinguindo leitura e escrita. Autorização por casa continua no `HouseAccessValidator`. |
| D4 | Refresh token **opaco, persistido como hash SHA-256**, entregue em cookie `HttpOnly` + `SameSite=Strict`, **rotacionado** a cada uso, com **detecção de reuso** (revoga a família inteira). |
| D5 | TTL: access token 15 min (`JWT_ACCESS_TTL`), refresh 7 dias (`JWT_REFRESH_TTL`). |
| D6 | CORS com credenciais; origens via `app.cors.allowed-origins` (padrão `http://localhost:5173`, React/Vite). |
| D7 | AES-256-GCM em `user_profiles.allergies` e `user_profiles.bio`; secret JWT padrão removida de `application.properties`. |
| D8 | `/api/auth/login` **removido**; `/api/auth/register` permanece público. |
| D9 | Dois clients: `nosso-teto-web` (`read write`) e `nosso-teto-mobile` (`read`). |
| D10 | Admin da plataforma (`ROLE_ADMIN_USERS`) com `GET /api/admin/users` e `PUT /api/admin/users/{id}/permissions`. Criado por um `ApplicationRunner` idempotente quando `ADMIN_PASSWORD` está definido (Flyway SQL não consegue gerar BCrypt nem ler variáveis de ambiente). |
| D11 | CI no GitHub Actions como primeira tarefa: `mvn -B verify`, JaCoCo com gate de **80% de linhas** (baseline medido: 93,3% linhas / 69,1% branches), Checkstyle (Google) em modo aviso. |
| D12 | Três PRs: PR 1 (spec + plano + CI), PR 2 (núcleo auth/autorização), PR 3 (CORS, cripto, Postman, docs). Branch protection em `main` exigindo o check do CI após o PR 1. |

---

## 2. Arquitetura

```
                 ┌───────────── SecurityFilterChain @Order(1)  securityMatcher("/oauth/**") ──────────────┐
 POST /oauth/token  ──►  cors · csrf off · stateless · permitAll  ──►  TokenController
 DELETE /oauth/token/revoke                                             │  ClientAuthenticator (Basic, registro de clients — nunca o user store)
                                                                        │  PasswordGrant → UserRepository + PasswordEncoder
                                                                        │  RefreshGrant  → RefreshTokenService (rotação atômica)
                                                                        └► AccessTokenService (NimbusJwtEncoder HS256)
                 ┌───────────── SecurityFilterChain @Order(2)  (profile != basic-security) ───────────────┐
 /api/**  ──►  cors · csrf off · stateless · oauth2ResourceServer(jwt)  ──►  @PreAuthorize (authority + scope)
                                                                         ──►  Service → HouseAccessValidator (casa)
                 ┌───────────── SecurityFilterChain (profile basic-security) ─────────────────────────────┐
 /api/**  ──►  cors · csrf off · stateless · httpBasic  ──►  DatabaseUserDetailsService (username = user.id)
```

### 2.1. Pacote `com.nossoteto.security` (estado final)

| Classe | Responsabilidade |
|---|---|
| `SecurityProperties` | `@ConfigurationProperties("app.security")`: clients, TTLs, cookie. |
| `OAuthSecurityConfig` | `@Profile("!basic-security")`: chain `/oauth/**` (Order 1), chain da API (Order 2), `JwtDecoder`, `JwtEncoder`, `JwtAuthenticationConverter`. |
| `BasicSecurityConfig` | `@Profile("basic-security")`: chain HTTP Basic. |
| `CommonSecurityConfig` | Sem profile: `PasswordEncoder`, `@EnableMethodSecurity`, handlers JSON 401/403, `CorsConfigurationSource`. |
| `JsonAuthHandlers` | `AuthenticationEntryPoint` (`{"error":"Authentication required"}`) e `AccessDeniedHandler` (`{"error":"Insufficient permission"}`). |
| `DatabaseUserDetailsService` | Profile basic: carrega por e-mail, `username` = `user.id`, authorities = permissões + `SCOPE_read` + `SCOPE_write`. |
| `ClientAuthenticator` | Valida `Authorization: Basic` contra `app.security.clients` em tempo constante. |
| `AccessTokenService` | Emite o JWT (claims `sub`, `scope`, `authorities`, `client_id`, `iat`, `exp`). |
| `RefreshTokenService` | Emite, rotaciona, detecta reuso e revoga refresh tokens. |
| `RefreshTokenCookieFactory` | Cria/apaga o cookie `refresh_token`. |
| `TokenController` | `POST /oauth/token`, `DELETE /oauth/token/revoke`. |
| `OAuthException` | Erros OAuth (`invalid_client` 401, `invalid_grant`/`invalid_request`/`invalid_scope`/`unsupported_grant_type` 400). |
| `AdminBootstrap` | `ApplicationRunner` que cria/atualiza o admin. |

Removidos: `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig`, `JwtTokenProviderTest`, dependências `jjwt-*`, `AuthResponse`, `LoginRequest`, `AuthService.loginUser`.

---

## 3. Banco de Dados

### 3.1. `V3__Security_permissions_and_refresh_tokens.sql`
- `permissions (id INT PK, name VARCHAR(60) UNIQUE NOT NULL, description VARCHAR(255))`.
- `user_permissions (user_id VARCHAR(36), permission_id INT, PK(user_id, permission_id))`, FKs `fk_user_permissions_user` (CASCADE) e `fk_user_permissions_permission`.
- `refresh_tokens (id VARCHAR(36) PK, token_hash VARCHAR(64) UNIQUE NOT NULL, family_id VARCHAR(36) NOT NULL, user_id VARCHAR(36) NOT NULL, client_id VARCHAR(100) NOT NULL, scope VARCHAR(100) NOT NULL, expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP NULL, revoked_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL)`, FK `fk_refresh_tokens_user` (CASCADE), índice `idx_refresh_tokens_family`.
- Seed do catálogo de 9 permissões (seção 4).
- **Backfill:** todo usuário existente recebe todas as permissões exceto `ROLE_ADMIN_USERS`.

### 3.2. `V4__Widen_encrypted_profile_columns.sql`
- `allergies` passa de `VARCHAR(255)` para `TEXT` (o texto cifrado `v1:` + base64(IV ‖ ciphertext ‖ tag) ultrapassa 255 caracteres).

---

## 4. Permissões e Matriz de Acesso

Catálogo (`id`, `name`): 1 `ROLE_MANAGE_PROFILE` · 2 `ROLE_SEARCH_HOUSES` · 3 `ROLE_MANAGE_HOUSE` · 4 `ROLE_MATCH` · 5 `ROLE_MANAGE_EXPENSES` · 6 `ROLE_MANAGE_CHORES` · 7 `ROLE_MANAGE_GROCERIES` · 8 `ROLE_VIEW_DASHBOARD` · 9 `ROLE_ADMIN_USERS`.

Novo usuário (registro): 1–8. Admin: 1–9.

| Endpoint | Permissão | Scope |
|---|---|---|
| `POST /api/auth/register` | público | — |
| `GET /api/users/me` | `ROLE_MANAGE_PROFILE` | `read` |
| `PUT /api/users/me/profile` | `ROLE_MANAGE_PROFILE` | `write` |
| `GET /api/houses`, `GET /api/houses/{id}` | `ROLE_SEARCH_HOUSES` | `read` |
| `POST /api/houses`, `PUT /api/houses/{id}`, `DELETE /api/houses/{id}/members/{memberId}` | `ROLE_MANAGE_HOUSE` | `write` |
| `POST /api/houses/{id}/leave` | (apenas autenticado) | `write` |
| `POST /api/matches/requests`, `POST /api/matches/requests/{id}/vote`, `POST /api/matches/requests/cancel-others` | `ROLE_MATCH` | `write` |
| `GET /api/houses/{id}/matches` | `ROLE_MATCH` | `read` |
| `GET /api/houses/{h}/expenses`, `GET …/{e}/attachment` | `ROLE_MANAGE_EXPENSES` | `read` |
| `POST /api/houses/{h}/expenses`, `POST …/propose-split`, `POST …/vote-split`, `PUT …/pay` | `ROLE_MANAGE_EXPENSES` | `write` |
| `GET /api/houses/{h}/chores` | `ROLE_MANAGE_CHORES` | `read` |
| `POST /api/houses/{h}/chores`, `PUT …/complete`, `POST …/rotate` | `ROLE_MANAGE_CHORES` | `write` |
| `GET /api/houses/{h}/groceries` | `ROLE_MANAGE_GROCERIES` | `read` |
| `POST /api/houses/{h}/groceries`, `PUT …/assign`, `PUT …/purchase` | `ROLE_MANAGE_GROCERIES` | `write` |
| `GET /api/houses/{h}/dashboard` | `ROLE_VIEW_DASHBOARD` | `read` |
| `GET /api/admin/users` | `ROLE_ADMIN_USERS` | `read` |
| `PUT /api/admin/users/{id}/permissions` | `ROLE_ADMIN_USERS` | `write` |

Expressão padrão: `@PreAuthorize("hasAuthority('<PERM>') and hasAuthority('SCOPE_<scope>')")`. Checagens de casa (membro/admin) permanecem nos services.

---

## 5. Contrato OAuth

### 5.1. `POST /oauth/token` (`application/x-www-form-urlencoded`, `Authorization: Basic base64(client_id:client_secret)`)

| `grant_type` | Parâmetros | Sucesso |
|---|---|---|
| `password` | `username` (e-mail), `password`, `scope` opcional (subconjunto dos scopes do client; padrão = todos) | `200` + corpo + `Set-Cookie: refresh_token` |
| `refresh_token` | refresh lido **do cookie** `refresh_token` | `200` + novo access token + novo cookie (rotação) |

Corpo de sucesso: `{"access_token":"…","token_type":"Bearer","expires_in":900,"scope":"read write","user_id":"…"}`. O refresh token **nunca** aparece no corpo.

Erros: `{"error":"<code>","error_description":"…"}` — `invalid_client` (401, com `WWW-Authenticate: Basic realm="nosso-teto"`), `invalid_grant` (400: credenciais erradas, refresh ausente/expirado/revogado/reusado/de outro client), `invalid_scope` (400), `unsupported_grant_type` (400), `invalid_request` (400).

### 5.2. `DELETE /oauth/token/revoke` (client Basic)
Revoga a família do refresh do cookie (idempotente: sem cookie ou token desconhecido também retorna `204`) e apaga o cookie (`Max-Age=0`).

### 5.3. Cookie
`refresh_token=<valor>; HttpOnly; SameSite=Strict; Path=/oauth/token; Max-Age=<refresh TTL>; Secure` — `Secure` controlado por `app.security.cookie.secure` (`COOKIE_SECURE`, padrão `false` para Postman em http local; `true` em produção).

### 5.4. JWT de acesso
HS256 com `JWT_SECRET` (≥ 32 bytes, validado no startup). Claims: `sub` = user id, `scope` = `"read write"`, `authorities` = lista de permissões **lidas do banco na emissão** (inclusive no refresh), `client_id`, `iat`, `exp`. Conversão: `scope` → `SCOPE_*`; `authorities` → como estão.

---

## 6. Invariantes de Segurança (checklist do Crítico)

1. **INV-01** Nenhum endpoint de `/api/**` exceto `POST /api/auth/register` e `/error` responde sem autenticação (401 JSON `Authentication required`).
2. **INV-02** Falta de permissão ou de scope retorna 403 JSON `Insufficient permission`; `AccessDeniedException` nunca vira 500 (proibido handler catch-all de `Exception`).
3. **INV-03** Client credentials são validadas apenas contra o registro de clients (`app.security.clients`), em tempo constante (`MessageDigest.isEqual`); nunca contra a tabela `users`.
4. **INV-04** Scope solicitado ⊆ scopes do client; o refresh mantém o scope e o client da emissão original; refresh apresentado por outro client → `invalid_grant`.
5. **INV-05** Permissões do JWT são relidas do banco a cada emissão (password e refresh): revogar uma permissão vale no próximo refresh.
6. **INV-06** Rotação atômica: `UPDATE refresh_tokens SET used_at=:now WHERE id=:id AND used_at IS NULL` deve afetar exatamente 1 linha; caso contrário é reuso.
7. **INV-07** Reuso, token revogado, expirado ou desconhecido → `invalid_grant` 400; em caso de reuso **toda a família** é revogada e essa revogação é **persistida** mesmo com a exceção (`noRollbackFor`).
8. **INV-08** Só o hash SHA-256 do refresh é persistido; o valor bruto (32 bytes de `SecureRandom`, base64url) só existe no cookie.
9. **INV-09** Cookie sempre `HttpOnly`, `SameSite=Strict`, `Path=/oauth/token`; o refresh nunca aparece em corpo, log ou URL.
10. **INV-10** Logout revoga a família no banco e apaga o cookie; um refresh posterior com o cookie antigo → `invalid_grant`.
11. **INV-11** Principal em todos os controllers obtido via `authentication.getName()` (= user id) nos dois profiles; nenhum cast de `getPrincipal()`.
12. **INV-12** Profile `basic-security`: `username` do `UserDetails` = id do usuário; authorities = permissões + `SCOPE_read` + `SCOPE_write`; contexto sobe e rota protegida responde 200 com Basic válido.
13. **INV-13** `@EnableMethodSecurity` fica em configuração de segurança, não na classe da aplicação.
14. **INV-14** `JWT_SECRET` e `APP_CRYPTO_KEY` não têm valor padrão em `src/main/resources/application.properties`; ausência/tamanho inválido falha no startup.
15. **INV-15** AES-256-GCM com IV de 12 bytes aleatório por cifragem e tag de 128 bits; formato `v1:` + base64(IV ‖ ciphertext‖tag); valor sem prefixo é lido como texto legado e recifrado no próximo save; tag inválida → exceção (nunca retorna lixo).
16. **INV-16** CORS aplicado em **todas** as chains; `allowCredentials=true` com lista explícita de origens (nunca `*`); preflight de origem não permitida → 403.
17. **INV-17** Endpoints de admin: um admin não pode remover `ROLE_ADMIN_USERS` de si mesmo (400); nomes de permissão desconhecidos → 400.
18. **INV-18** Respostas nunca expõem `passwordHash`, hash de refresh, secrets ou texto cifrado.

---

## 7. CI (GitHub Actions)

`.github/workflows/ci.yml`: `push` em `main` e `pull_request`; job `build` em `ubuntu-latest`; Temurin 17 com cache Maven; `mvn -B verify`; upload de `target/site/jacoco` e `target/checkstyle-result.xml` como artifacts (`if: always()`).
`pom.xml`: `jacoco-maven-plugin` 0.8.12 (`prepare-agent`, `report` e `check` em `verify`, regra `BUNDLE`/`LINE`/`COVEREDRATIO` ≥ `0.80`); `maven-checkstyle-plugin` 3.3.1 com `google_checks.xml`, `failOnViolation=false`. O `argLine` do Surefire não é sobrescrito no pom (o JaCoCo injeta o próprio).

---

## 8. Estratégia de Testes

- Unitários: `ClientAuthenticator`, `AccessTokenService`, `RefreshTokenService`, `AesGcmFieldCipher`, `AdminService`, `DatabaseUserDetailsService`.
- Integração (`@SpringBootTest` + H2): fluxo OAuth completo (password → refresh → rotação → reuso → logout), matriz de autorização com `SecurityMockMvcRequestPostProcessors.jwt()`, CORS preflight, contexto `basic-security`, cifragem em repouso (leitura via JDBC bruto).
- Golden path existente (`NossoTetoIntegrationTest`, `ApiErrorHandlingTest`) migrado para `/oauth/token`.
- `MySqlSchemaCompatibilityTest` inclui as novas entidades.
