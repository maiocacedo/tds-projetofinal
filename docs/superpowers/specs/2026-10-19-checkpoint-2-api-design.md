# Especificação de Design — Checkpoint 2: Funcionalidades Completas da API (Nosso Teto)

**Data:** 2026-10-19  
**Escopo:** Implementação completa dos módulos de negócio da API REST do sistema **Nosso Teto** (Usuários e Perfis, Casas e Compatibilidade, Match e Votação, Despesas e Divisões, Tarefas Domésticas Semanais, Lista de Mercado e Dashboard da Casa).

---

## 1. Arquitetura e Componentes Transversais

### 1.1. Padrão MVC e Convenções de Idioma
- **Código e Banco de Dados:** Entidades, repositórios, serviços, controllers e colunas do banco de dados mantidos em **inglês** (seguindo o padrão iniciado no Checkpoint 1 com `User`, `House`, `AuthService`).
- **Compatibilidade de Payloads (PT/EN):** Todos os DTOs de entrada/saída suportarão os nomes de campos documentados em português no `docs/api-routes.md` e seus equivalentes em inglês via `@JsonAlias` / `@JsonProperty`, garantindo compatibilidade total com os contratos da documentação e com os testes existentes do Checkpoint 1.

### 1.2. Autorização Baseada em Recursos (`HouseAccessValidator` + `GlobalExceptionHandler`)
- **`HouseAccessValidator`:** Componente Spring injetado nos serviços de gestão da casa:
  - `User requireAuthenticatedUser(String userId)`: Busca o usuário autenticado ou lança `ResourceNotFoundException` (`404 Not Found`).
  - `User requireHouseMember(String userId, String houseId)`: Valida se `user.getHouseId()` é igual a `houseId`. Caso contrário, lança `ForbiddenOperationException` (`403 Forbidden`).
  - `User requireHouseAdmin(String userId, String houseId)`: Valida se o usuário pertence à casa e possui `Boolean.TRUE.equals(user.getIsHouseAdmin())`. Caso contrário, lança `ForbiddenOperationException` (`403 Forbidden`).
- **`GlobalExceptionHandler` (`@RestControllerAdvice`):**
  - `ForbiddenOperationException` $\rightarrow$ `403 Forbidden` (`{ "error": "..." }`)
  - `ResourceNotFoundException` $\rightarrow$ `404 Not Found` (`{ "error": "..." }`)
  - `BusinessRuleException` / `IllegalArgumentException` $\rightarrow$ `400 Bad Request` (`{ "error": "..." }`)

### 1.3. Motor de Votação Reutilizável (`VotingEvaluator`)
- A entidade `House` possui o atributo `votingRule` (`VotingRule.UNANIMITY` por padrão, ou `VotingRule.MAJORITY`).
- Dado o total de moradores atuais da casa (`totalMembers`), a quantidade de votos favoráveis (`approveCount`) e contrários (`rejectCount`):
  - **Modo `UNANIMITY`:**
    - Se `rejectCount >= 1` $\rightarrow$ `VotingOutcome.REJECTED`
    - Se `approveCount >= totalMembers` (com `totalMembers > 0`) $\rightarrow$ `VotingOutcome.APPROVED`
    - Caso contrário $\rightarrow$ `VotingOutcome.PENDING`
  - **Modo `MAJORITY` (Maioria Simples `> 50%`):**
    - Se `approveCount > totalMembers / 2` $\rightarrow$ `VotingOutcome.APPROVED`
    - Se `rejectCount >= (totalMembers + 1) / 2` (impossível atingir maioria de aprovação) $\rightarrow$ `VotingOutcome.REJECTED`
    - Caso contrário $\rightarrow$ `VotingOutcome.PENDING`
- Este motor é utilizado tanto na votação de **Solicitações de Match** quanto na votação de **Divisão Customizada de Despesas**.

---

## 2. Evolução do Banco de Dados (`V2__Checkpoint2_full_schema.sql`)

A migration `V2` expande o schema criado na `V1` com as seguintes alterações e novas tabelas:

1. **Alterações em `users`:**
   - Adição de `name VARCHAR(255) NULL`.
2. **Alterações em `houses`:**
   - Adição de `rent_amount DECIMAL(10,2) NULL`
   - Adição de `allows_pets BOOLEAN DEFAULT TRUE`
   - Adição de `allows_smokers BOOLEAN DEFAULT FALSE`
   - Adição de `expected_organization_level INT DEFAULT 3` (escala 1 a 5)
   - Adição de `visitor_tolerance INT DEFAULT 3` (escala 1 a 5)
   - Adição de `has_furniture BOOLEAN DEFAULT FALSE`
   - Adição de `basic_rules TEXT NULL`
   - Adição de `voting_rule VARCHAR(20) NOT NULL DEFAULT 'UNANIMITY'`
3. **Tabela `user_profiles`:**
   - `user_id VARCHAR(36) PRIMARY KEY` (FK para `users.id` com `ON DELETE CASCADE`)
   - `budget DECIMAL(10,2)`
   - `has_pets BOOLEAN DEFAULT FALSE`
   - `is_smoker BOOLEAN DEFAULT FALSE`
   - `profession VARCHAR(255)`
   - `bio TEXT`
   - `allergies VARCHAR(255)`
   - `organization_level INT` (1 a 5)
   - `visitor_tolerance INT` (1 a 5)
   - `has_furniture BOOLEAN DEFAULT FALSE`
4. **Tabela `match_requests`:**
   - `id VARCHAR(36) PRIMARY KEY`
   - `user_id VARCHAR(36) NOT NULL` (FK para `users.id`)
   - `house_id VARCHAR(36) NOT NULL` (FK para `houses.id`)
   - `status VARCHAR(20) NOT NULL` (`PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`)
   - `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
5. **Tabela `match_votes`:**
   - `id VARCHAR(36) PRIMARY KEY`
   - `request_id VARCHAR(36) NOT NULL` (FK para `match_requests.id`)
   - `voter_id VARCHAR(36) NOT NULL` (FK para `users.id`)
   - `vote VARCHAR(20) NOT NULL` (`APPROVE`, `REJECT`)
   - Constraint única `(request_id, voter_id)`
6. **Tabela `expenses`:**
   - `id VARCHAR(36) PRIMARY KEY`
   - `house_id VARCHAR(36) NOT NULL` (FK para `houses.id`)
   - `title VARCHAR(255) NOT NULL`
   - `total_amount DECIMAL(10,2) NOT NULL`
   - `due_date DATE NOT NULL`
   - `attachment_url VARCHAR(500) NULL`
   - `split_type VARCHAR(30) NOT NULL DEFAULT 'EQUAL'` (`EQUAL`, `CUSTOM_PENDING`, `CUSTOM_APPROVED`)
7. **Tabela `expense_splits`:**
   - `id VARCHAR(36) PRIMARY KEY`
   - `expense_id VARCHAR(36) NOT NULL` (FK para `expenses.id` com `ON DELETE CASCADE`)
   - `user_id VARCHAR(36) NOT NULL` (FK para `users.id`)
   - `amount_due DECIMAL(10,2) NOT NULL`
   - `proposed_amount_due DECIMAL(10,2) NULL`
   - `is_paid BOOLEAN NOT NULL DEFAULT FALSE`
8. **Tabela `expense_split_votes`:**
   - `id VARCHAR(36) PRIMARY KEY`
   - `expense_id VARCHAR(36) NOT NULL` (FK para `expenses.id` com `ON DELETE CASCADE`)
   - `voter_id VARCHAR(36) NOT NULL` (FK para `users.id`)
   - `vote VARCHAR(20) NOT NULL` (`APPROVE`, `REJECT`)
   - Constraint única `(expense_id, voter_id)`
9. **Tabela `chores`:**
   - `id VARCHAR(36) PRIMARY KEY`
   - `house_id VARCHAR(36) NOT NULL` (FK para `houses.id`)
   - `description VARCHAR(255) NOT NULL`
   - `chore_type VARCHAR(20) NOT NULL` (`ROTATIONAL`, `FIXED`)
   - `current_assignee_id VARCHAR(36) NOT NULL` (FK para `users.id`)
   - `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'` (`PENDING`, `COMPLETED`, `DELAYED`)
   - `last_reset_date DATE NOT NULL`
10. **Tabela `groceries`:**
    - `id VARCHAR(36) PRIMARY KEY`
    - `house_id VARCHAR(36) NOT NULL` (FK para `houses.id`)
    - `item_name VARCHAR(255) NOT NULL`
    - `quantity INT NOT NULL DEFAULT 1`
    - `assignee_id VARCHAR(36) NULL` (FK para `users.id`)
    - `is_purchased BOOLEAN NOT NULL DEFAULT FALSE`

---

## 3. Especificação Funcional por Módulo e Rotas

### 3.1. Autenticação (`AuthController` — ajuste incremental)
- **`POST /api/auth/register`:** Passa a aceitar opcionalmente `name` (ou `nome` via `@JsonAlias`) além de `email` e `password` (ou `senha`), mantendo retrocompatibilidade com os testes do Checkpoint 1.

### 3.2. Usuários e Perfis (`UserController`, `UserService`)
- **`GET /api/users/me`:** Retorna dados do usuário logado (`id`, `name`, `email`, `houseId`, `isHouseAdmin`), detalhes resumidos da casa atual (se houver) e seu `UserProfile` (se preenchido).
- **`PUT /api/users/me/profile`:** Cria ou atualiza o `UserProfile` do usuário autenticado. Aceita campos em PT/EN:
  - `orcamentoMensal` / `budget` (`BigDecimal`)
  - `possuiPets` / `hasPets` (`Boolean`)
  - `fumante` / `isSmoker` (`Boolean`)
  - `profissao` / `profession` (`String`)
  - `biografia` / `bio` (`String`)
  - `alergias` / `allergies` (`String`)
  - `nivelOrganizacao` / `organizationLevel` (`Integer`, 1..5)
  - `toleranciaVisitas` / `visitorTolerance` (`Integer`, 1..5)
  - `mobiliaPropria` / `hasFurniture` (`Boolean`)

### 3.3. Casas e Cálculo de Compatibilidade (`HouseController`, `HouseService`, `CompatibilityCalculator`)
- **`POST /api/houses`:**
  - Cria uma nova `House` (nome, endereço, descrição, regras básicas, valor de aluguel, aceita pets, aceita fumantes, nível de organização esperado, tolerância a visitas, mobília existente e `votingRule`: `UNANIMITY` ou `MAJORITY`).
  - Vincula automaticamente o usuário criador (`user.houseId = house.id`, `user.isHouseAdmin = true`).
- **`GET /api/houses`:**
  - Lista casas com `isAcceptingMatches == true`.
  - Filtros opcionais via query params: `maxRent` (ou `orcamentoMaximo`), `allowsPets` (ou `aceitaPets`), `allowsSmokers` (ou `aceitaFumantes`).
  - Se o usuário logado possuir `UserProfile`, calcula o `compatibilityScore` (`0` a `100`) para cada casa com base em 5 critérios (20 pontos cada):
    1. **Orçamento:** `user.budget >= house.rentAmount` (20 pts; se `rentAmount` for nulo, 20 pts; se menor, proporcional ou 0 pts).
    2. **Pets:** Se `user.hasPets == true` e `house.allowsPets == false` $\rightarrow$ 0 pts; caso contrário $\rightarrow$ 20 pts.
    3. **Fumante:** Se `user.isSmoker == true` e `house.allowsSmokers == false` $\rightarrow$ 0 pts; caso contrário $\rightarrow$ 20 pts.
    4. **Nível de Organização:** `20 - (abs(user.organizationLevel - house.expectedOrganizationLevel) * 5)` (mínimo 0).
    5. **Tolerância a Visitas:** `20 - (abs(user.visitorTolerance - house.visitorTolerance) * 5)` (mínimo 0).
- **`GET /api/houses/{id}`:**
  - Retorna os detalhes da casa, lista resumida de moradores atuais e o `compatibilityScore` para o usuário logado.

### 3.4. Match e Votação (`MatchController`, `MatchService`)
- **`POST /api/matches/requests`:**
  - Payload: `{ "houseId": "..." }`.
  - Valida se a casa existe e está aceitando matches (`isAcceptingMatches == true`), e se o usuário ainda não tem uma solicitação `PENDING` para a mesma casa. Cria `MatchRequest` com status `PENDING`.
- **`GET /api/houses/{id}/matches`:**
  - Restrito a moradores da casa (`requireHouseMember`).
  - Lista solicitações `PENDING` daquela casa com perfil do candidato e `compatibilityScore`.
- **`POST /api/matches/requests/{requestId}/vote`:**
  - Restrito a moradores atuais da casa alvo da solicitação (`requireHouseMember`).
  - Payload: `{ "voto": "APROVAR" | "REJEITAR" }` (ou `APPROVE` / `REJECT`).
  - Registra/atualiza o voto em `match_votes` e avalia o resultado via `VotingEvaluator` conforme o `votingRule` da casa (`UNANIMITY` ou `MAJORITY`).
  - Se `APPROVED`:
    - Atualiza `matchRequest.status = ACCEPTED`.
    - Vincula o candidato à casa (`candidate.houseId = houseId`, `candidate.isHouseAdmin = false`).
    - Verifica se o candidato possui outras solicitações `PENDING` em outras casas e retorna na resposta `hasOtherPendingRequests = true/false` e `otherPendingRequestIds` (atendendo ao **RF06**).
  - Se `REJECTED`:
    - Atualiza `matchRequest.status = REJECTED`.
- **`POST /api/matches/requests/cancel-others`:**
  - Cancela (`status = CANCELLED`) todas as demais solicitações `PENDING` do usuário logado após ele ingressar em uma casa (**RF06**).

### 3.5. Financeiro / Despesas (`ExpenseController`, `ExpenseService`)
- **`POST /api/houses/{houseId}/expenses`:**
  - Restrito a moradores da casa (`requireHouseMember`).
  - Payload: `{ "titulo", "valorTotal", "vencimento", "anexoBase64" }`.
  - Se `anexoBase64` estiver presente, decodifica (ou armazena os bytes) no diretório `./uploads/expenses/{expenseId}` e salva o caminho em `attachmentUrl`.
  - Cria `Expense` com `splitType = EQUAL` e gera os registros em `expense_splits` dividindo `valorTotal` igualmente entre os moradores atuais da casa (`RoundingMode.HALF_UP` com ajuste de centavos no primeiro morador para que a soma bata exatamente com `valorTotal`).
- **`GET /api/houses/{houseId}/expenses`:**
  - Restrito a moradores da casa. Retorna a lista de despesas com seus respectivos `splits`.
- **`GET /api/houses/{houseId}/expenses/{expenseId}/attachment`:**
  - Restrito a moradores da casa (`requireHouseMember` $\rightarrow$ `403 Forbidden` se for de outra casa).
  - Retorna os bytes/conteúdo do comprovante armazenado em `./uploads/expenses/{expenseId}`.
- **`POST /api/houses/{houseId}/expenses/{expenseId}/propose-split`:**
  - Restrito a moradores da casa.
  - Payload: `{ "divisoes": [ { "userId": "...", "valorOuPorcentagem": 150.00 } ], "tipoDivisao": "VALOR" | "PORCENTAGEM" }` (se omitido `tipoDivisao`, infere ou assume valor exato; se a soma das porcentagens for 100 e diferente do `totalAmount`, converte porcentagem para valor).
  - Valida se a soma dos valores propostos equivale ao `totalAmount` da despesa.
  - Preenche `proposedAmountDue` em cada `ExpenseSplit`, altera `expense.splitType = CUSTOM_PENDING` e limpa votos anteriores daquela despesa.
- **`POST /api/houses/{houseId}/expenses/{expenseId}/vote-split`:**
  - Restrito a moradores da casa.
  - Payload: `{ "voto": "APROVAR" | "REJEITAR" }`.
  - Avalia os votos via `VotingEvaluator` usando a `votingRule` da casa:
    - Se `APPROVED`: copia `proposedAmountDue` para `amountDue` em todos os splits, limpa `proposedAmountDue` e define `expense.splitType = CUSTOM_APPROVED`.
    - Se `REJECTED`: limpa `proposedAmountDue` e retorna `expense.splitType = EQUAL`.
- **`PUT /api/houses/{houseId}/expenses/{expenseId}/pay`:**
  - Restrito a moradores da casa. Marca o `ExpenseSplit` do usuário logado naquela despesa como `isPaid = true`.

### 3.6. Tarefas Domésticas (`ChoreController`, `ChoreService`)
- **`POST /api/houses/{houseId}/chores`:**
  - Restrito a moradores da casa.
  - Payload: `{ "descricao", "tipo": "ROTATIVA" | "FIXA", "responsavelId" }`.
  - Cria a tarefa com status `PENDING` e `lastResetDate = LocalDate.now()`.
- **`GET /api/houses/{houseId}/chores`:**
  - Restrito a moradores da casa. Lista as tarefas da casa.
- **`PUT /api/houses/{houseId}/chores/{choreId}/complete`:**
  - Restrito a moradores da casa. Marca a tarefa como `COMPLETED`.
- **Ciclo Semanal e Rotação (`processWeeklyCycle` + `@Scheduled(cron = "0 0 0 * * SUN")` + `POST /api/houses/{houseId}/chores/rotate`):**
  - Para cada tarefa da casa:
    - Se o status atual for `PENDING` (não concluída no ciclo que se encerra), seu status é marcado como `DELAYED` (**RF14**); se já estava `COMPLETED`, inicia o novo ciclo como `PENDING`.
    - Se `choreType == ROTATIONAL`, avança `currentAssigneeId` para o próximo morador da casa (ordenados de forma determinística por `id`).
    - Atualiza `lastResetDate = LocalDate.now()`.

### 3.7. Lista de Mercado (`GroceryController`, `GroceryService`)
- **`POST /api/houses/{houseId}/groceries`:**
  - Restrito a moradores da casa. Cria item com `nomeItem` (`itemName`), `quantidade` (`quantity`) e `isPurchased = false`.
- **`GET /api/houses/{houseId}/groceries`:**
  - Restrito a moradores da casa. Lista todos os itens da casa.
- **`PUT /api/houses/{houseId}/groceries/{itemId}/assign`:**
  - Restrito a moradores da casa. Atribui `assigneeId = loggedUserId`.
- **`PUT /api/houses/{houseId}/groceries/{itemId}/purchase`:**
  - Restrito a moradores da casa. Marca `isPurchased = true` (e define `assigneeId = loggedUserId` caso ainda estivesse nulo).

### 3.8. Dashboard / Mural da Casa (`DashboardController`, `DashboardService`)
- **`GET /api/houses/{houseId}/dashboard`:**
  - Restrito a moradores da casa.
  - Retorna em uma única chamada:
    - `pendingSplitsForUser`: parcelas de despesas ainda não pagas pelo morador logado e soma total devida.
    - `houseChores`: tarefas da semana da casa (destacando a tarefa atual do morador e eventuais tarefas `DELAYED`).
    - `pendingGroceries`: itens de mercado ainda não comprados (`isPurchased == false`).
    - `pendingMatchRequestsCount`: quantidade de solicitações de match aguardando voto na casa.

---

## 4. Estratégia de Testes (TDD e Integração)
- Para evitar dependência de instância MySQL externa durante a execução automatizada de testes, adicionaremos o banco em memória **H2** no escopo `test` (`src/test/resources/application.properties`), permitindo tanto testes unitários com JUnit 5 + Mockito quanto testes de integração `@SpringBootTest` / `@AutoConfigureMockMvc` cobrindo os *Golden Paths* (Match com votação unânime/maioria, Despesas com divisão igual/customizada + comprovante com bloqueio `403`, Rotação de Tarefas, Mercado e Dashboard).
