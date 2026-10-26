# Mapeamento da API REST - Nosso Teto

Este documento define o contrato da API REST implementada no backend (Spring Boot) para suportar os Checkpoints 1 e 2.
Todas as rotas (exceto as de Auth público) exigem um token JWT válido no header `Authorization: Bearer <token>`.

**Códigos de erro (corpo sempre `{ "error": "..." }`):**
- `400 Bad Request` — regra de negócio violada, JSON malformado ou valor inválido (ex.: `"regraVotacao": "FOO"`, `?maxRent=abc`).
- `401 Unauthorized` — sem token, token inválido/expirado ou login com credenciais erradas.
- `403 Forbidden` — usuário autenticado sem permissão sobre o recurso (não mora na casa ou não é admin).
- `404 Not Found` — recurso inexistente (ou de outra casa).

**Configuração do JWT (variáveis de ambiente):** `JWT_SECRET` (mínimo 32 caracteres) e `JWT_EXPIRATION_MS` (padrão `7200000` = 2h).

## 1. Autenticação (Auth)
- `POST /api/auth/register`
  - **Payload:** `{ "nome", "email", "senha" }`
  - **Retorno:** `201 Created` com `{ "id", "name", "email", "createdAt" }` (o hash da senha nunca é retornado).
- `POST /api/auth/login`
  - **Payload:** `{ "email", "senha" }`
  - **Retorno:** `{ "token": "jwt-token-string", "userId", "expiresIn": 7200 }` — `expiresIn` (segundos) reflete a validade real do token. Credenciais erradas retornam `401`.

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
