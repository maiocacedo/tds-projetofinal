# Estratégia de Segurança, CI e Testes E2E - Nosso Teto

Este documento define os pilares técnicos para garantir a qualidade, segurança e entrega contínua do projeto, alinhado aos Requisitos Não Funcionais (RNFs) levantados.

## 1. Segurança e Autorização (Spring Security + JWT)

### 1.1. Autenticação (Stateless)
- Utilizaremos **JSON Web Tokens (JWT)**.
- O token será assinado com HMAC (HS256) usando uma secret key armazenada em variáveis de ambiente.
- Tempo de expiração (TTL) curto (ex: 2 horas), e endpoints sensíveis exigirão o token no header `Authorization: Bearer <token>`.
- Senhas salvas no banco de dados serão irreversivelmente criptografadas usando o algoritmo **Bcrypt**.

### 1.2. Autorização Baseada em Recursos (Multi-Tenancy lógico)
- Não basta o usuário estar autenticado, ele precisa ter **permissão sobre o recurso**.
- Para qualquer requisição em `/api/houses/{houseId}/*` (seja contas, tarefas ou mercado), o backend interceptará a requisição e validará se o `user_id` atrelado ao JWT possui `house_id == {houseId}` no banco de dados.
- Caso o usuário tente acessar dados de outra casa, a API retornará `403 Forbidden`.

### 1.3. Segurança de Anexos (Comprovantes Financeiros)
- Comprovantes de despesas não podem ser arquivos públicos.
- Os arquivos físicos serão armazenados (localmente na pasta `/uploads` durante o desenvolvimento, ou num bucket S3 em produção).
- A API fornecerá um endpoint `/api/houses/{houseId}/expenses/{expenseId}/attachment` que só faz o stream do arquivo se o usuário passar pela validação de morador citada acima.

## 2. Continuous Integration (CI)

Utilizaremos o **GitHub Actions** para garantir que nenhum código quebrado seja mesclado ao projeto.

### 2.1. Pipeline de Backend (Spring Boot)
Disparado no `push` e `pull_request` para a branch `master`:
1. Checkout do código.
2. Setup Java JDK 17 (ou 21).
3. Cache das dependências do Maven/Gradle.
4. Execução do Linter/Checkstyle (Garantindo padronização).
5. Compilação do projeto.
6. Execução dos **Testes Unitários e de Integração**.
7. Falha do pipeline caso a cobertura de testes (Jacoco) seja inferior a 80%.

## 3. Estratégia de Testes E2E (End-to-End)

Embora o Checkpoint 1 seja focado na API, a plataforma utilizará **Cypress** ou **Playwright** (quando o frontend iniciar) para simular o comportamento de um usuário real no navegador.

### 3.1. Cenários Críticos (Golden Paths)
Os testes E2E focarão nos fluxos que não podem falhar:
- **Cenário 1 (Match):** Cadastrar Usuário A -> Cadastrar Usuário B -> Usuário A cria Casa -> Usuário B pede Match -> Usuário A aprova -> Usuário B entra na casa.
- **Cenário 2 (Gestão):** Usuário logado -> Cadastra Despesa -> Confere divisão matemática (50/50) -> Marca como Paga -> Sistema atualiza o saldo.
- **Cenário 3 (Segurança):** Tentar acessar URL direta da dashboard de uma casa sem estar logado -> Ser redirecionado para o Login.
