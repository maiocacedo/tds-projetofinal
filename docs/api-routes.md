# Mapeamento da API REST - Nosso Teto

Este documento define o contrato da API REST implementada no backend (Spring Boot) para suportar os Checkpoints 1 e 2.
Todas as rotas (exceto as de Auth público) exigem um token JWT válido no header `Authorization: Bearer <token>`.

## 1. Autenticação (Auth)
- `POST /api/auth/register`
  - **Payload:** `{ "nome", "email", "senha" }`
  - **Retorno:** `201 Created`
- `POST /api/auth/login`
  - **Payload:** `{ "email", "senha" }`
  - **Retorno:** `{ "token": "jwt-token-string", "expiresIn": 3600 }`

## 2. Usuários e Perfis
- `GET /api/users/me`
  - **Retorno:** Retorna os dados do usuário logado, seu perfil de hábitos e sua casa atual (se houver).
- `PUT /api/users/me/profile`
  - **Payload:** `{ "orcamentoMensal", "possuiPets", "fumante", "profissao", "biografia", "alergias", "nivelOrganizacao", "toleranciaVisitas", "mobiliaPropria" }`
  - **Retorno:** `200 OK`

## 3. Casas / Repúblicas
- `POST /api/houses`
  - **Payload:** `{ "nome", "endereco", "descricao", "regrasBasicas", "valorAluguel", "aceitaPets", "aceitaFumantes", "nivelOrganizacaoEsperado", "toleranciaVisitas", "mobiliaExistente", "regraVotacao": "UNANIMITY" | "MAJORITY" }`
  - **Ação:** Cria a casa e vincula o usuário criador como Administrador (`201 Created`).
- `GET /api/houses`
  - **Query Params (Opcionais):** `maxRent`/`orcamentoMaximo`, `allowsPets`/`aceitaPets`, `allowsSmokers`/`aceitaFumantes`
  - **Ação:** Retorna a lista de casas com vagas abertas, aplicando filtros e calculando o `compatibilityScore` (`0` a `100`) com o usuário logado.
- `GET /api/houses/{id}`
  - **Retorno:** Detalhes de uma casa específica, lista de moradores e `compatibilityScore`.

## 4. Match e Votação
- `POST /api/matches/requests`
  - **Payload:** `{ "houseId" }`
  - **Ação:** Candidato demonstra interesse em uma casa (`201 Created`).
- `GET /api/houses/{id}/matches`
  - **Ação:** Lista todos os candidatos aguardando aprovação (`PENDING`) para aquela casa com seus perfis e `compatibilityScore` (restrito a moradores da casa).
- `POST /api/matches/requests/{requestId}/vote`
  - **Payload:** `{ "voto": "APROVAR" | "REJEITAR" }`
  - **Ação:** Um morador registra seu voto. Se atingir a regra da casa (`MAJORITY` ou `UNANIMITY`), o candidato é aceito e o retorno informa `hasOtherPendingRequests` (RF06).
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
  - **Ação:** Inicia votação para divisão customizada (`CUSTOM_PENDING`).
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
  - **Ação:** Processa o ciclo semanal de rotação da casa (tarefas `PENDING` não concluídas viram `DELAYED` e tarefas `ROTATIONAL` avançam para o próximo morador).

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
