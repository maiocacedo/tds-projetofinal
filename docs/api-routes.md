# Mapeamento da API REST - Nosso Teto

Este documento define o contrato da API REST que será implementada no backend (Spring Boot) para suportar o Checkpoint 1.
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
  - **Retorno:** Retorna os dados do usuário logado e sua casa atual (se houver).
- `PUT /api/users/me/profile`
  - **Payload:** `{ "orcamentoMensal", "possuiPets", "fumante", "profissao", "biografia", "alergias", "nivelOrganizacao", "toleranciaVisitas", "mobiliaPropria" }`
  - **Retorno:** `200 OK`

## 3. Casas / Repúblicas
- `POST /api/houses`
  - **Payload:** `{ "nome", "endereco", "descricao", "regrasBasicas" }`
  - **Ação:** Cria a casa e vincula o usuário criador como Administrador.
- `GET /api/houses`
  - **Ação:** Retorna a lista de casas com vagas abertas (com filtros por orçamento, pets, etc) para usuários buscando match.
- `GET /api/houses/{id}`
  - **Retorno:** Detalhes de uma casa específica.

## 4. Match e Votação
- `POST /api/matches/requests`
  - **Payload:** `{ "houseId" }`
  - **Ação:** Candidato demonstra interesse em uma casa.
- `GET /api/houses/{id}/matches`
  - **Ação:** Lista todos os candidatos aguardando aprovação para aquela casa.
- `POST /api/matches/requests/{requestId}/vote`
  - **Payload:** `{ "voto": "APROVAR" | "REJEITAR" }`
  - **Ação:** Um morador registra seu voto. Se atingir maioria/unanimidade, o candidato é aceito.

## 5. Financeiro (Despesas)
- `POST /api/houses/{houseId}/expenses`
  - **Payload:** `{ "titulo", "valorTotal", "vencimento", "anexoBase64" }`
  - **Ação:** Cria a conta. Por padrão, divide 50/50 entre os moradores atuais.
- `POST /api/houses/{houseId}/expenses/{expenseId}/propose-split`
  - **Payload:** `{ "divisoes": [ {"userId", "valorOuPorcentagem"} ] }`
  - **Ação:** Inicia votação para divisão customizada.
- `PUT /api/houses/{houseId}/expenses/{expenseId}/pay`
  - **Ação:** O usuário logado marca sua parcela daquela conta como paga.

## 6. Tarefas Domésticas
- `POST /api/houses/{houseId}/chores`
  - **Payload:** `{ "descricao", "tipo": "ROTATIVA" | "FIXA", "responsavelId" }`
- `PUT /api/houses/{houseId}/chores/{choreId}/complete`
  - **Ação:** Marca como feita na semana atual.

## 7. Mercado
- `POST /api/houses/{houseId}/groceries`
  - **Payload:** `{ "nomeItem", "quantidade" }`
- `PUT /api/houses/{houseId}/groceries/{itemId}/assign`
  - **Ação:** O usuário logado assume a responsabilidade de comprar o item.
