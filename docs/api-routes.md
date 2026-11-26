# Mapeamento da API REST - Nosso Teto

Este documento define o contrato da API REST implementada no backend (Spring Boot) para suportar os Checkpoints 1, 2 e 3.
Todas as rotas de `/api/**` (exceto `POST /api/auth/register` e `/error`) exigem autenticação via JWT OAuth 2 no header `Authorization: Bearer <access_token>` (ou HTTP Basic quando o profile `basic-security` estiver ativo).

**Códigos de erro da API (`/api/**`, corpo `{ "error": "..." }`):**
- `400 Bad Request` — regra de negócio violada, JSON malformado ou valor inválido (ex.: `"regraVotacao": "FOO"`, `?maxRent=abc`, permissão desconhecida ou tentativa do admin de remover a própria `ROLE_ADMIN_USERS`).
- `401 Unauthorized` — sem token ou token inválido/expirado (`{"error":"Authentication required"}`, com `WWW-Authenticate: Bearer` / `Bearer error="invalid_token"` no modo OAuth, ou `WWW-Authenticate: Basic realm="nosso-teto"` no profile `basic-security`).
- `403 Forbidden` — usuário autenticado sem a permissão/scope necessária (`{"error":"Insufficient permission"}`) ou sem acesso ao recurso da casa (não mora na casa ou não é admin da casa).
- `404 Not Found` — recurso inexistente (ou de outra casa).

**Códigos de erro OAuth 2 (`/oauth/token` e `/oauth/token/revoke`, corpo `{ "error": "<code>", "error_description": "..." }`):**
- `401 Unauthorized` (`invalid_client`, com header `WWW-Authenticate: Basic realm="nosso-teto"`) — header `Authorization: Basic` ausente, malformado ou credenciais de client inválidas.
- `400 Bad Request`:
  - `invalid_request` — parâmetros enviados via query string, parâmetros repetidos no formulário ou campos obrigatórios ausentes.
  - `invalid_grant` — credenciais do usuário inválidas ou refresh token ausente, expirado, revogado, reusado (revoga toda a família) ou emitido para outro client.
  - `invalid_scope` — `scope` solicitado fora dos scopes permitidos para o client.
  - `unsupported_grant_type` — `grant_type` diferente de `password` ou `refresh_token`.

**Variáveis de ambiente principais:** `JWT_SECRET` (mínimo 32 bytes, obrigatório), `APP_CRYPTO_KEY` (32 bytes em Base64, obrigatório), `JWT_ACCESS_TTL` (padrão `PT15M`), `JWT_REFRESH_TTL` (padrão `P7D`), `COOKIE_SECURE` (padrão `false`), `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:5173`), `ADMIN_EMAIL` / `ADMIN_PASSWORD` e `SPRING_PROFILES_ACTIVE=basic-security` (opcional, para HTTP Basic). Veja a tabela completa em [docs/security-ci-strategy.md](security-ci-strategy.md).

## 1. Autenticação (OAuth 2)
- `POST /api/auth/register` (público)
  - **Payload:** `{ "nome", "email", "senha" }`
  - **Retorno:** `201 Created` com `{ "id", "name", "email", "createdAt" }` (o hash da senha nunca é retornado). Atribui automaticamente as 8 permissões padrão de usuário (todas exceto `ROLE_ADMIN_USERS`).
- `POST /oauth/token`
  - **Autenticação do client:** `Authorization: Basic base64(client_id:client_secret)` (clients padrão: `nosso-teto-web` com scopes `read write`, e `nosso-teto-mobile` com scope `read`).
  - **Content-Type:** `application/x-www-form-urlencoded` com parâmetros **exclusivamente no corpo** da requisição (parâmetros na query string ou repetidos retornam `400 invalid_request`).
  - **Grant `password`:** `grant_type=password`, `username` (e-mail do usuário), `password`, `scope` (opcional — subconjunto dos scopes do client; padrão = todos os scopes do client).
  - **Grant `refresh_token`:** `grant_type=refresh_token` no corpo; o refresh token é lido do **cookie** `refresh_token` (nunca enviado no corpo ou na URL). Executa rotação atômica e revoga a família inteira em caso de reuso (`400 invalid_grant`).
  - **Retorno `200 OK`** (`Cache-Control: no-store`):
    - **Corpo:** `{ "access_token": "...", "token_type": "Bearer", "expires_in": 900, "scope": "read write", "user_id": "..." }`. O refresh token **nunca** aparece no corpo.
    - **Cookie:** `Set-Cookie: refresh_token=<valor>; HttpOnly; SameSite=Strict; Path=/oauth/token; Max-Age=<refresh TTL>` (adiciona `Secure` quando `COOKIE_SECURE=true`).
  - **Claims do JWT (`HS256`):** `sub` (`user_id`), `scope`, `authorities` (permissões relidas do banco a cada emissão), `client_id`, `iat`, `exp`.
- `DELETE /oauth/token/revoke` (Logout)
  - **Autenticação do client:** `Authorization: Basic base64(client_id:client_secret)` (sem query parameters).
  - **Ação:** Revoga a família do refresh token presente no cookie `refresh_token` (se emitido para aquele client) e apaga o cookie (`Max-Age=0`, `Path=/oauth/token`). Operação idempotente: mesmo sem cookie ou com token desconhecido retorna `204 No Content`.
- **Profile alternativo `basic-security`:** ao iniciar com `SPRING_PROFILES_ACTIVE=basic-security`, os endpoints `/oauth/**` são desativados e as rotas de `/api/**` passam a aceitar `Authorization: Basic base64(email:senha)` contra o banco de dados (retornando `401` com `WWW-Authenticate: Basic realm="nosso-teto"` em falhas).

## 2. Usuários e Perfis
- `GET /api/users/me`
  - **Retorno:** Retorna os dados do usuário logado, seu perfil de hábitos e sua casa atual (se houver).
- `PUT /api/users/me/profile`
  - **Payload:** `{ "orcamentoMensal", "possuiPets", "fumante", "profissao", "biografia", "alergias", "nivelOrganizacao", "toleranciaVisitas", "mobiliaPropria" }`
  - **Retorno:** `200 OK`

## 3. Casas / Repúblicas
- `POST /api/houses`
  - **Payload:** `{ "nome", "endereco", "descricao", "regrasBasicas", "valorAluguel", "aceitaPets", "aceitaFumantes", "nivelOrganizacaoEsperado", "toleranciaVisitas", "mobiliaExistente", "regraVotacao": "UNANIMITY" | "MAJORITY" }`
  - **Ação:** Cria a casa e vincula o usuário criador como Administrador (`201 Created`). Retorna `400` se o usuário já mora em uma casa (é preciso sair dela antes). `nivelOrganizacaoEsperado` e `toleranciaVisitas` devem estar entre 1 e 5.
- `GET /api/houses`
  - **Query Params (Opcionais):** `maxRent`/`orcamentoMaximo`, `allowsPets`/`aceitaPets`, `allowsSmokers`/`aceitaFumantes`
  - **Ação:** Retorna a lista de casas com vagas abertas, aplicando filtros e calculando o `compatibilityScore` (`0` a `100`) com o usuário logado.
- `GET /api/houses/{id}`
  - **Retorno:** Detalhes de uma casa específica, lista de moradores e `compatibilityScore`.
  - **Privacidade:** o `email` dos moradores só aparece para quem mora na casa (também vale para `GET /api/houses`).
- `PUT /api/houses/{id}`
  - **Payload:** os mesmos campos do `POST`, todos opcionais, mais `aceitandoMatches` (`isAcceptingMatches`) para abrir/fechar vagas.
  - **Ação:** Atualização parcial — só os campos enviados são alterados. Restrito ao administrador da casa (`403` para os demais).
- `POST /api/houses/{id}/leave`
  - **Ação:** O usuário logado sai da casa (`204 No Content`). Se era o único admin, o próximo morador (ordem por `id`) vira admin; tarefas atribuídas a ele passam para esse morador e itens de mercado que ele assumiu voltam a ficar sem responsável. Se a casa ficar vazia, ela para de aceitar matches e as solicitações pendentes são canceladas.
- `DELETE /api/houses/{id}/members/{memberId}`
  - **Ação:** O administrador remove um morador (`204 No Content`), com as mesmas regras de repasse do `leave`. O admin não pode remover a si mesmo (use `leave`).

## 4. Match e Votação
- `POST /api/matches/requests`
  - **Payload:** `{ "houseId" }`
  - **Ação:** Candidato demonstra interesse em uma casa (`201 Created`). Quem já mora em uma casa recebe `400` (um usuário pertence a no máximo uma casa).
- `GET /api/houses/{id}/matches`
  - **Ação:** Lista todos os candidatos aguardando aprovação (`PENDING`) para aquela casa com seus perfis e `compatibilityScore` (restrito a moradores da casa).
- `POST /api/matches/requests/{requestId}/vote`
  - **Payload:** `{ "voto": "APROVAR" | "REJEITAR" }`
  - **Ação:** Um morador registra seu voto. Se atingir a regra da casa (`MAJORITY` ou `UNANIMITY`), o candidato é aceito e o retorno informa `hasOtherPendingRequests` (RF06). Só contam votos de quem ainda mora na casa. Se o candidato manteve a solicitação aberta mas já entrou em outra casa, ela é encerrada com `status: CANCELLED` em vez de transferi-lo.
- `POST /api/matches/requests/cancel-others`
  - **Ação:** Cancela todas as outras solicitações de match pendentes do usuário após ingressar em uma casa (RF06).

## 5. Financeiro (Despesas)
- `POST /api/houses/{houseId}/expenses`
  - **Payload:** `{ "titulo", "valorTotal", "vencimento", "anexoBase64" }`
  - **Ação:** Cria a conta e divide igualmente entre os moradores atuais (`201 Created`).
- `GET /api/houses/{houseId}/expenses`
  - **Ação:** Lista as despesas e divisões da casa (restrito a moradores).
- `GET /api/houses/{houseId}/expenses/{expenseId}/attachment`
  - **Ação:** Baixa o comprovante/boleto da despesa (restrito a moradores da casa; retorna `403 Forbidden` para não-moradores).
- `POST /api/houses/{houseId}/expenses/{expenseId}/propose-split`
  - **Payload:** `{ "tipoDivisao": "VALOR" | "PORCENTAGEM", "divisoes": [ {"userId", "valorOuPorcentagem"} ] }`
  - **Ação:** Inicia votação para divisão customizada (`CUSTOM_PENDING`). Cada `userId` pode aparecer uma única vez (`400` se repetido).
- `POST /api/houses/{houseId}/expenses/{expenseId}/vote-split`
  - **Payload:** `{ "voto": "APROVAR" | "REJEITAR" }`
  - **Ação:** Morador vota na divisão customizada; quando aprovada conforme regra da casa, aplica os novos valores (`CUSTOM_APPROVED`).
- `PUT /api/houses/{houseId}/expenses/{expenseId}/pay`
  - **Ação:** O usuário logado marca sua parcela daquela conta como paga.

## 6. Tarefas Domésticas
- `POST /api/houses/{houseId}/chores`
  - **Payload:** `{ "descricao", "tipo": "ROTATIVA" | "FIXA", "responsavelId" }`
  - **Ação:** Cria tarefa doméstica (`201 Created`).
- `GET /api/houses/{houseId}/chores`
  - **Ação:** Lista as tarefas domésticas da casa.
- `PUT /api/houses/{houseId}/chores/{choreId}/complete`
  - **Ação:** Marca como feita (`COMPLETED`) na semana atual.
- `POST /api/houses/{houseId}/chores/rotate`
  - **Ação:** Força o fechamento do ciclo semanal (o mesmo processamento roda automaticamente todo domingo à meia-noite). Restrito ao administrador da casa.
  - **Regras do ciclo:** tarefas não concluídas (`PENDING` ou já `DELAYED`) ficam `DELAYED` **com o mesmo responsável** até serem concluídas (RF14), para que o atraso não passe para outra pessoa. Tarefas `COMPLETED` voltam a `PENDING`; se forem `ROTATIONAL`, avançam para o próximo morador (ordem por `id`).

## 7. Mercado
- `POST /api/houses/{houseId}/groceries`
  - **Payload:** `{ "nomeItem", "quantidade" }`
  - **Ação:** Adiciona item à lista de compras da casa (`201 Created`).
- `GET /api/houses/{houseId}/groceries`
  - **Ação:** Lista todos os itens de mercado da casa.
- `PUT /api/houses/{houseId}/groceries/{itemId}/assign`
  - **Ação:** O usuário logado assume a responsabilidade de comprar o item.
- `PUT /api/houses/{houseId}/groceries/{itemId}/purchase`
  - **Ação:** Marca o item como comprado (`isPurchased = true`).

## 8. Dashboard da Casa
- `GET /api/houses/{houseId}/dashboard`
  - **Ação:** Retorna o resumo consolidado da casa para o morador logado (`totalPendingAmountForUser`, `pendingSplitsForUser`, `houseChores`, `pendingGroceries` e `pendingMatchRequestsCount`).

## 9. Administração
- `GET /api/admin/users`
  - **Autorização:** `ROLE_ADMIN_USERS` + scope `read`.
  - **Retorno:** `200 OK` com a lista de usuários cadastrados: `[{ "id", "name", "email", "houseId", "permissions": [...] }]`.
- `PUT /api/admin/users/{id}/permissions`
  - **Autorização:** `ROLE_ADMIN_USERS` + scope `write`.
  - **Payload:** `{ "permissions": ["ROLE_MANAGE_PROFILE", ...] }` (também aceita o alias `"permissoes"`).
  - **Ação:** Substitui integralmente o conjunto de permissões do usuário `{id}` e retorna `{ "id", "name", "email", "houseId", "permissions": [...] }`. Um administrador não pode remover a própria `ROLE_ADMIN_USERS` (`400`), nomes desconhecidos/nulos/em branco retornam `400` e usuário inexistente retorna `404`.
- **Bootstrap (`AdminBootstrap`):** cria a conta de administrador na inicialização somente quando `ADMIN_PASSWORD` (com `ADMIN_EMAIL`, padrão `admin@nossoteto.local`) está definido; **nunca** promove uma conta pré-existente que não possua `ROLE_ADMIN_USERS`.

## 10. Matriz de Permissões e Scopes

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

> **Criptografia em repouso:** os campos `alergias` (`allergies`) e `biografia` (`bio`) de `user_profiles` são armazenados cifrados com AES-256-GCM (`APP_CRYPTO_KEY`) e retornados em texto claro pela API.
> **CORS:** configurado para aceitar credenciais a partir das origens definidas em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:5173`).
