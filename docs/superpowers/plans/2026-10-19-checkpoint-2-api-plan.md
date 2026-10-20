# Checkpoint 2 (Funcionalidades Completas da API) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar todas as funcionalidades de negócio da API REST do sistema Nosso Teto (Perfis de Usuário, Casas e Compatibilidade, Match e Votação Configurável, Despesas e Divisões com Anexo Seguro, Tarefas Domésticas Rotativas, Lista de Mercado e Dashboard da Casa) seguindo TDD e o padrão MVC.

**Architecture:** API REST em Spring Boot 3.2 (Java 17+) estruturada em camadas MVC (`model`, `repository`, `service`, `controller`, `dto`, `exception`, `security`). Validação transversal de multi-tenancy da casa via `HouseAccessValidator` e tratamento padronizado de erros HTTP (`400`, `403`, `404`) via `GlobalExceptionHandler`. Persistência versionada com Flyway (`V2`) e testada com JUnit 5, Mockito e H2 em modo MySQL.

**Tech Stack:** Java 17+, Spring Boot 3.2 (Web, Security, Data JPA, Validation), MySQL / H2 (Test), Flyway, JWT (`jjwt`), JUnit 5, Mockito, MockMvc.

---

### Task 1: Infraestrutura de Testes (H2), Migration V2, Enums, Evolução de `User`/`House` e Componentes Transversais (`HouseAccessValidator`, `VotingEvaluator`, `GlobalExceptionHandler`)

**Files:**
- Modify: `pom.xml`
- Create: `src/test/resources/application.properties`
- Create: `src/main/resources/db/migration/V2__Checkpoint2_full_schema.sql`
- Create: `src/main/java/com/nossoteto/model/enums/VotingRule.java`
- Create: `src/main/java/com/nossoteto/model/enums/VotingOutcome.java`
- Create: `src/main/java/com/nossoteto/model/enums/MatchStatus.java`
- Create: `src/main/java/com/nossoteto/model/enums/VoteType.java`
- Create: `src/main/java/com/nossoteto/model/enums/SplitType.java`
- Create: `src/main/java/com/nossoteto/model/enums/ChoreType.java`
- Create: `src/main/java/com/nossoteto/model/enums/ChoreStatus.java`
- Modify: `src/main/java/com/nossoteto/model/User.java`
- Modify: `src/main/java/com/nossoteto/repository/UserRepository.java`
- Modify: `src/main/java/com/nossoteto/model/House.java`
- Modify: `src/main/java/com/nossoteto/dto/RegisterRequest.java`
- Modify: `src/main/java/com/nossoteto/dto/LoginRequest.java`
- Modify: `src/main/java/com/nossoteto/service/AuthService.java`
- Modify: `src/main/java/com/nossoteto/controller/AuthController.java`
- Create: `src/main/java/com/nossoteto/exception/ResourceNotFoundException.java`
- Create: `src/main/java/com/nossoteto/exception/ForbiddenOperationException.java`
- Create: `src/main/java/com/nossoteto/exception/BusinessRuleException.java`
- Create: `src/main/java/com/nossoteto/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/nossoteto/service/HouseAccessValidator.java`
- Create: `src/main/java/com/nossoteto/service/VotingEvaluator.java`
- Test: `src/test/java/com/nossoteto/service/VotingEvaluatorTest.java`
- Test: `src/test/java/com/nossoteto/service/HouseAccessValidatorTest.java`

- [x] **Step 1: Adicionar H2 no `pom.xml` e configurar `src/test/resources/application.properties`**

Adicionar dependência H2 com `<scope>test</scope>` em `pom.xml` e criar `src/test/resources/application.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:nossoteto_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
app.upload.dir=target/test-uploads
```

- [x] **Step 2: Criar Migration `src/main/resources/db/migration/V2__Checkpoint2_full_schema.sql`**

Criar todas as alterações em `users` e `houses` e as novas tabelas (`user_profiles`, `match_requests`, `match_votes`, `expenses`, `expense_splits`, `expense_split_votes`, `chores`, `groceries`) conforme especificado na seção 2 da Spec.

- [x] **Step 3: Escrever os testes unitários `VotingEvaluatorTest` e `HouseAccessValidatorTest` (TDD)**

Testar:
- `VotingRule.UNANIMITY`: aprovação com 100% dos votos, rejeição imediata com 1 voto contrário, pendente enquanto faltam votos.
- `VotingRule.MAJORITY`: aprovação com `> 50%` (ex: 2 de 3, 3 de 4), rejeição quando votos contrários atingem `(total + 1) / 2` (ex: 2 de 3, 2 de 4), pendente nos demais casos.
- `HouseAccessValidator`: sucesso quando `user.houseId == houseId`, `ForbiddenOperationException` quando pertence a outra casa ou não tem casa, e validação de `requireHouseAdmin`.

- [x] **Step 4: Implementar Enums, Exceções, `GlobalExceptionHandler`, `VotingEvaluator`, `HouseAccessValidator` e atualizar `User`, `House`, `RegisterRequest`, `LoginRequest`, `AuthService`**

Suportar `@JsonAlias("nome")` em `RegisterRequest.name` e `@JsonAlias("senha")` em `RegisterRequest.password` / `LoginRequest.password`.

- [x] **Step 5: Rodar `mvn test` e fazer commit**

Run: `mvn test`  
Commit: `feat: add V2 flyway migration, domain enums, voting evaluator and house access validator`

---

### Task 2: Módulo de Usuários e Perfis (`UserProfile`, `UserService`, `UserController`)

**Files:**
- Create: `src/main/java/com/nossoteto/model/UserProfile.java`
- Create: `src/main/java/com/nossoteto/repository/UserProfileRepository.java`
- Create: `src/main/java/com/nossoteto/dto/UserProfileDto.java`
- Create: `src/main/java/com/nossoteto/dto/UserMeResponse.java`
- Create: `src/main/java/com/nossoteto/service/UserService.java`
- Create: `src/main/java/com/nossoteto/controller/UserController.java`
- Test: `src/test/java/com/nossoteto/service/UserServiceTest.java`
- Test: `src/test/java/com/nossoteto/controller/UserControllerTest.java`

- [x] **Step 1: Escrever os testes `UserServiceTest` e `UserControllerTest` (TDD)**

Testar `GET /api/users/me` (retornando dados do usuário logado, casa e perfil) e `PUT /api/users/me/profile` (criando/atualizando `UserProfile` tanto com campos em português do `api-routes.md` quanto em inglês).

- [x] **Step 2: Implementar `UserProfile`, `UserProfileRepository`, DTOs, `UserService` e `UserController`**

Mapear `user_profiles` (`userId` como PK) e suportar `@JsonAlias` para `orcamentoMensal`/`budget`, `possuiPets`/`hasPets`, `fumante`/`isSmoker`, `profissao`/`profession`, `biografia`/`bio`, `alergias`/`allergies`, `nivelOrganizacao`/`organizationLevel`, `toleranciaVisitas`/`visitorTolerance`, `mobiliaPropria`/`hasFurniture`.

- [x] **Step 3: Rodar `mvn test` e fazer commit**

Run: `mvn test`  
Commit: `feat: implement user profile management and /api/users/me endpoints`

---

### Task 3: Módulo de Casas e Cálculo de Compatibilidade (`CompatibilityCalculator`, `HouseService`, `HouseController`)

**Files:**
- Create: `src/main/java/com/nossoteto/service/CompatibilityCalculator.java`
- Create: `src/main/java/com/nossoteto/dto/CreateHouseRequest.java`
- Create: `src/main/java/com/nossoteto/dto/HouseResponse.java`
- Create: `src/main/java/com/nossoteto/service/HouseService.java`
- Create: `src/main/java/com/nossoteto/controller/HouseController.java`
- Modify: `src/main/java/com/nossoteto/repository/HouseRepository.java`
- Test: `src/test/java/com/nossoteto/service/CompatibilityCalculatorTest.java`
- Test: `src/test/java/com/nossoteto/service/HouseServiceTest.java`
- Test: `src/test/java/com/nossoteto/controller/HouseControllerTest.java`

- [x] **Step 1: Escrever testes `CompatibilityCalculatorTest`, `HouseServiceTest` e `HouseControllerTest` (TDD)**

Testar:
- Cálculo de score de compatibilidade (`0` a `100`) cobrindo os 5 critérios (orçamento, pets, fumante, organização, visitas).
- `POST /api/houses` criando a casa e atribuindo o criador como morador administrador (`isHouseAdmin = true`).
- `GET /api/houses` listando casas com vagas abertas, aplicando filtros (`maxRent`/`orcamentoMaximo`, `allowsPets`/`aceitaPets`, `allowsSmokers`/`aceitaFumantes`) e calculando `compatibilityScore`.
- `GET /api/houses/{id}` retornando detalhes da casa, moradores e `compatibilityScore`.

- [x] **Step 2: Implementar `CompatibilityCalculator`, DTOs, `HouseService` e `HouseController`**

- [x] **Step 3: Rodar `mvn test` e fazer commit**

Run: `mvn test`  
Commit: `feat: implement houses endpoints, filters and compatibility calculator`

---

### Task 4: Módulo de Match e Votação (`MatchRequest`, `MatchVote`, `MatchService`, `MatchController`)

**Files:**
- Create: `src/main/java/com/nossoteto/model/MatchRequest.java`
- Create: `src/main/java/com/nossoteto/model/MatchVote.java`
- Create: `src/main/java/com/nossoteto/repository/MatchRequestRepository.java`
- Create: `src/main/java/com/nossoteto/repository/MatchVoteRepository.java`
- Create: `src/main/java/com/nossoteto/dto/CreateMatchRequestDto.java`
- Create: `src/main/java/com/nossoteto/dto/VoteRequestDto.java`
- Create: `src/main/java/com/nossoteto/dto/MatchRequestResponse.java`
- Create: `src/main/java/com/nossoteto/dto/VoteMatchResponse.java`
- Create: `src/main/java/com/nossoteto/service/MatchService.java`
- Create: `src/main/java/com/nossoteto/controller/MatchController.java`
- Test: `src/test/java/com/nossoteto/service/MatchServiceTest.java`
- Test: `src/test/java/com/nossoteto/controller/MatchControllerTest.java`

- [ ] **Step 1: Escrever testes `MatchServiceTest` e `MatchControllerTest` (TDD)**

Testar:
- `POST /api/matches/requests`: criação de solicitação `PENDING` e bloqueio de duplicidade.
- `GET /api/houses/{id}/matches`: restrito a moradores da casa (`403` para não-moradores), retornando candidatos com `compatibilityScore`.
- `POST /api/matches/requests/{requestId}/vote`: votação com `UNANIMITY` e `MAJORITY`, entrada automática do candidato na casa quando aprovado, retorno de `hasOtherPendingRequests` (RF06), e rejeição.
- `POST /api/matches/requests/cancel-others`: cancelamento das demais solicitações `PENDING` do usuário (RF06).

- [ ] **Step 2: Implementar entidades, repositórios, DTOs, `MatchService` e `MatchController`**

Suportar votos `"APROVAR"`/`"APPROVE"` e `"REJEITAR"`/`"REJECT"`.

- [ ] **Step 3: Rodar `mvn test` e fazer commit**

Run: `mvn test`  
Commit: `feat: implement match requests and configurable house voting`

---

### Task 5: Módulo Financeiro / Despesas (`Expense`, `ExpenseSplit`, `ExpenseSplitVote`, `ExpenseService`, `ExpenseController`)

**Files:**
- Create: `src/main/java/com/nossoteto/model/Expense.java`
- Create: `src/main/java/com/nossoteto/model/ExpenseSplit.java`
- Create: `src/main/java/com/nossoteto/model/ExpenseSplitVote.java`
- Create: `src/main/java/com/nossoteto/repository/ExpenseRepository.java`
- Create: `src/main/java/com/nossoteto/repository/ExpenseSplitRepository.java`
- Create: `src/main/java/com/nossoteto/repository/ExpenseSplitVoteRepository.java`
- Create: `src/main/java/com/nossoteto/dto/CreateExpenseRequest.java`
- Create: `src/main/java/com/nossoteto/dto/ProposeSplitRequest.java`
- Create: `src/main/java/com/nossoteto/dto/ExpenseSplitDto.java`
- Create: `src/main/java/com/nossoteto/dto/ExpenseResponse.java`
- Create: `src/main/java/com/nossoteto/service/ExpenseService.java`
- Create: `src/main/java/com/nossoteto/controller/ExpenseController.java`
- Test: `src/test/java/com/nossoteto/service/ExpenseServiceTest.java`
- Test: `src/test/java/com/nossoteto/controller/ExpenseControllerTest.java`

- [ ] **Step 1: Escrever testes `ExpenseServiceTest` e `ExpenseControllerTest` (TDD)**

Testar:
- `POST /api/houses/{houseId}/expenses`: divisão igualitária automática entre todos os moradores da casa (com ajuste de centavos) e salvamento de `anexoBase64` em disco.
- `GET /api/houses/{houseId}/expenses` e `GET /api/houses/{houseId}/expenses/{expenseId}/attachment`: acesso permitido a moradores e bloqueado (`403 Forbidden`) para não-moradores.
- `POST /api/houses/{houseId}/expenses/{expenseId}/propose-split` (por valor ou porcentagem) e `POST .../vote-split` aplicando a nova divisão após aprovação conforme `votingRule` da casa.
- `PUT /api/houses/{houseId}/expenses/{expenseId}/pay` marcando a parcela individual do morador logado como paga.

- [ ] **Step 2: Implementar entidades, repositórios, DTOs, `ExpenseService` e `ExpenseController`**

- [ ] **Step 3: Rodar `mvn test` e fazer commit**

Run: `mvn test`  
Commit: `feat: implement shared expenses, custom split voting and secure attachments`

---

### Task 6: Tarefas Domésticas, Mercado, Dashboard, Atualização de Docs e Teste de Integração End-to-End

**Files:**
- Modify: `src/main/java/com/nossoteto/NossoTetoApplication.java`
- Create: `src/main/java/com/nossoteto/model/Chore.java`
- Create: `src/main/java/com/nossoteto/repository/ChoreRepository.java`
- Create: `src/main/java/com/nossoteto/dto/CreateChoreRequest.java`
- Create: `src/main/java/com/nossoteto/dto/ChoreResponse.java`
- Create: `src/main/java/com/nossoteto/service/ChoreService.java`
- Create: `src/main/java/com/nossoteto/controller/ChoreController.java`
- Create: `src/main/java/com/nossoteto/model/Grocery.java`
- Create: `src/main/java/com/nossoteto/repository/GroceryRepository.java`
- Create: `src/main/java/com/nossoteto/dto/CreateGroceryRequest.java`
- Create: `src/main/java/com/nossoteto/dto/GroceryResponse.java`
- Create: `src/main/java/com/nossoteto/service/GroceryService.java`
- Create: `src/main/java/com/nossoteto/controller/GroceryController.java`
- Create: `src/main/java/com/nossoteto/dto/DashboardResponse.java`
- Create: `src/main/java/com/nossoteto/service/DashboardService.java`
- Create: `src/main/java/com/nossoteto/controller/DashboardController.java`
- Modify: `docs/api-routes.md`
- Modify: `docs/database-schema.md`
- Test: `src/test/java/com/nossoteto/service/ChoreServiceTest.java`
- Test: `src/test/java/com/nossoteto/service/GroceryServiceTest.java`
- Test: `src/test/java/com/nossoteto/integration/NossoTetoIntegrationTest.java`

- [ ] **Step 1: Escrever testes `ChoreServiceTest`, `GroceryServiceTest` e `NossoTetoIntegrationTest` (TDD)**

Testar:
- Criação e conclusão de tarefas (`ROTATIONAL` e `FIXED`), e ciclo semanal (`processWeeklyCycle`) marcando pendentes como `DELAYED` e rotacionando o responsável das tarefas `ROTATIONAL`.
- Criação, atribuição (`assign`) e compra (`purchase`) de itens de mercado.
- `GET /api/houses/{houseId}/dashboard` retornando resumo consolidado da casa.
- Teste de integração completo (`@SpringBootTest` + `@AutoConfigureMockMvc`) executando os Golden Paths ponta a ponta (Cadastro -> Perfil -> Criação de Casa -> Solicitação e Votação de Match -> Despesa + Split + Pagamento + Anexo `403` para intruso -> Tarefas + Rotação -> Mercado -> Dashboard).

- [ ] **Step 2: Implementar módulos de Tarefas, Mercado e Dashboard, e atualizar `docs/api-routes.md` e `docs/database-schema.md`**

- [ ] **Step 3: Rodar `mvn test` completo e fazer commit**

Run: `mvn test`  
Commit: `feat: implement chores, groceries, house dashboard and full integration tests`
