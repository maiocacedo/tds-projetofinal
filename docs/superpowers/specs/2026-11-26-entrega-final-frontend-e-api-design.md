# Entrega final: frontend React e lacunas da API

Spec da implementação que fecha o [mapa do frontend (#7)](https://github.com/maiocacedo/tds-projetofinal/issues/7). Fontes: decisões dos tickets #8–#18 ([`docs/frontend/decisoes.md`](../../frontend/decisoes.md)), design system ([`docs/frontend/design-system.md`](../../frontend/design-system.md)), arquitetura ([`docs/frontend/arquitetura.md`](../../frontend/arquitetura.md)), glossário ([`CONTEXT.md`](../../../CONTEXT.md)) e o backlog de lacunas ([#19](https://github.com/maiocacedo/tds-projetofinal/issues/19)). Referência visual: protótipos do canvas, copiados no branch local `prototype/frontend-canvas`.

## Problem Statement

O Nosso Teto tem uma API completa para o Checkpoint 3 (OAuth, casas, match, despesas, tarefas, mercado, admin), mas **ninguém consegue usar o app**: não há interface. Para a entrega final, quem procura uma república precisa se cadastrar, preencher a ficha, achar casas compatíveis e pedir vaga; quem já mora precisa ver o que deve, o que tem de fazer na semana e o que falta no mercado, sem planilha nem grupo de mensagens. Os protótipos aprovados também revelaram regras que a API ainda não cumpre: quem pagou cada conta, pontos, tempo de moradia, mudança de casa, vários admins, frequência das tarefas, "comprei mesmo assim" e revogação de sessão, entre outras. Sem elas, as telas aprovadas não funcionam.

## Solution

Duas frentes que andam em paralelo, ligadas por **contratos fixados nesta spec**:

1. **API:** fechar as lacunas do #19 no backend Spring Boot existente, com migrações Flyway novas, testes e a documentação da API atualizada.
2. **Frontend:** um SPA em `web/` (Vite + React + TypeScript) que implementa as telas aprovadas no canvas (landing, Sobre, splash, acesso, ficha, Explorar, solicitações, comemoração, Início, Contas, Tarefas, Mercado, Casa, Perfil e Administração), responsivo (celular e computador), com o movimento do design system (geleia, risco desenhado, comemoração) e alternativa para `prefers-reduced-motion`.

O frontend é desenvolvido contra **handlers MSW que seguem os contratos abaixo**, então não espera o backend; no fim, troca para a API real pelo proxy do Vite e os dois são verificados juntos. A demo roda local: `mvn spring-boot:run` + `npm run dev`.

## User Stories

### Visitante e entrada
1. Como visitante, quero uma landing que mostre o app funcionando (um celular que troca de aba sozinho e responde ao toque), para entender o produto sem criar conta.
2. Como visitante, quero ver a semana de uma república, os 4 passos em linha do tempo e as perguntas "E se…" em formato de conversa, para tirar dúvidas antes de me cadastrar.
3. Como visitante, quero testar a compatibilidade ao vivo no bloco final, para sentir como o match funciona.
4. Como visitante, quero um botão de cadastro no topo e no fim da landing, para começar quando eu decidir.
5. Como visitante, quero uma página Sobre com o projeto, a conta da compatibilidade, privacidade e o design system, para confiar no app.
6. Como usuário, quero uma splash vermelha (casa desenhada, porta abrindo) na primeira abertura da sessão, com botão Pular, enquanto o app recupera minha sessão.
7. Como usuário com movimento reduzido no sistema, quero que todas as animações virem troca direta ou fade curto.
8. Como visitante, quero me cadastrar com nome, e-mail e senha de 8+ caracteres e ver os erros em português, para corrigir sem adivinhar.
9. Como usuário, quero entrar com e-mail e senha, com uma mensagem clara quando não conferem.
10. Como usuário que esqueceu a senha, quero pedir a recuperação e, na demo, clicar em "Verificar e-mail" para definir uma senha nova, sem o app revelar se o e-mail existe.
11. Como usuário novo, quero preencher a ficha numa tela só, sabendo que as 5 respostas do match são obrigatórias antes de buscar casas.
12. Como usuário, quero continuar logado ao recarregar a página (refresh silencioso), sem guardar token no navegador.
13. Como usuário, quero sair da conta e ter a sessão encerrada no servidor.

### Candidato (sem casa)
14. Como candidato, quero ver as casas em lista com o anel de compatibilidade e filtros (aluguel, aceita pets, aceita fumantes), para achar as que combinam comigo.
15. Como candidato, quero abrir "Por que N%?" e ver os 5 critérios explicados.
16. Como candidato, quero ver o detalhe de uma casa (regras, vagas, moradores) e pedir vaga.
17. Como candidato, quero não ver casas sem vaga ou que pausaram os pedidos.
18. Como candidato, quero ver minhas solicitações com o progresso da votação e o status de cada uma.
19. Como candidato, quero desistir de uma solicitação específica.
20. Como candidato aceito, quero ser levado às boas-vindas (RF06) e a uma comemoração ao abrir o app, e então virar morador.
21. Como candidato, quero criar minha própria casa numa tela só, com prévia ao vivo de como os candidatos vão vê-la.
22. Como candidato, quero ver meus pontos e meu tempo de moradia no meu perfil.

### Morador: Início e perfil
23. Como morador, quero ver no topo do Início os alertas (tarefa atrasada, conta vencendo) com ação rápida.
24. Como morador, quero ver quanto devo à casa, o que é meu esta semana, meus pontos do mês e o total da casa no mês (sem ranking) e o que falta no mercado.
25. Como morador, quero ver no perfil meu total de pontos e o histórico (motivo, valor, data).
26. Como morador, quero editar minha ficha a qualquer momento.

### Morador: Casa
27. Como morador, quero ver os moradores, quem é admin e as regras da casa.
28. Como admin da casa, quero promover ou rebaixar outro morador a admin, sem nunca deixar a casa sem admin.
29. Como admin da casa, quero propor a remoção de um morador com motivo opcional, e a casa vota pela regra dela, sem o voto da pessoa.
30. Como morador, quero votar numa remoção vendo quem já votou, sem ver a direção dos votos.
31. Como morador, quero sair da casa; se eu for o último admin, escolho quem assume; se eu estiver devendo, sou avisado e posso sair mesmo assim.
32. Como admin da casa, quero ajustar as vagas com um contador digitável e pausar os pedidos sem zerar as vagas.
33. Como morador, quero ver os candidatos com compatibilidade, pontos e tempo de moradia ("Novo no Nosso Teto" para quem nunca morou) e votar; posso trocar meu voto enquanto a solicitação está aberta.
34. Como morador, quero pedir vaga em outra casa; se for aceito, a vaga fica reservada por 72 h e eu confirmo ou recuso a mudança.
35. Como admin da casa que aprovou alguém que já mora em outra casa, quero poder retirar a reserva antes de ele confirmar.

### Morador: Contas
36. Como morador, quero ver quanto devo a cada pessoa (por credor) e quanto me devem.
37. Como morador, quero ver a transparência da casa (contas pagas x de y e valor em aberto por morador); no computador, numa tabela.
38. Como morador, quero lançar uma despesa dizendo quem pagou, o vencimento, um comprovante e, se quiser, que ela se repete todo mês num dia de 1 a 31.
39. Como morador, quero propor uma divisão customizada em R$ ou %, tirando alguém da conta, com a soma validada.
40. Como devedor, quero marcar "Paguei" e ganhar +10 pontos se for até o vencimento.
41. Como credor, quero confirmar "Recebi" ou marcar "Não recebi", reabrindo a parcela.
42. Como quem lançou, quero editar ou excluir a despesa enquanto ninguém pagou.

### Morador: Tarefas
43. Como morador, quero ver minhas tarefas e as da casa na semana, com atrasadas destacadas.
44. Como responsável, quero marcar a tarefa feita com o risco desenhado; nas de várias vezes por semana, marco cada vez ("2 de 3").
45. Como morador, quero reabrir uma tarefa que outro marcou como feita e não fez; ela volta atrasada com meu nome.
46. Como morador, quero propor trocar minha tarefa da semana com a de outra pessoa, e ela aceita ou recusa.
47. Como admin da casa, quero criar tarefas fixas ou rotativas com frequência (1×, 2×, 3×, todo dia, a cada 15 dias), definir a ordem da roda e ligar o atalho do mercado.
48. Como admin da casa, quero fechar o ciclo antes do domingo.
49. Como responsável, quero ganhar +20 pontos quando a tarefa fecha o ciclo feita e sem atraso.

### Morador: Mercado
50. Como morador, quero adicionar um item com quantidade (contador digitável).
51. Como morador, quero ver a lista em três grupos: falta comprar, alguém vai comprar (com o nome) e comprado nos últimos 7 dias.
52. Como morador, quero assumir um item livre ("Eu compro") e desistir dele.
53. Como morador no mercado, quero o modo "Eu compro" só com a minha parte, com caixas grandes.
54. Como morador, quero marcar um item comprado e ver o risco e o "+5" no lugar antes de ele ir para Comprado, e desfazer em ~5 s.
55. Como morador, quero comprar um item que outra pessoa assumiu ("Comprei mesmo assim") e ficar com o item e os pontos.
56. Como morador, quero ganhar +5 só quando compro um item que outra pessoa adicionou.
57. Como morador, quero editar ou apagar um item ainda não comprado.
58. Como morador, quero lançar o gasto do mercado como despesa já preenchida.

### Administrador do sistema
59. Como administrador do sistema, quero listar os usuários com busca, filtro e ordenação, vendo a casa e quantas permissões cada um tem.
60. Como administrador do sistema, quero editar as 9 permissões de alguém com atalhos (Morador padrão, Admin do sistema) e confirmar um resumo antes de salvar.
61. Como administrador do sistema, quero que a pessoa editada perca a sessão (sai em até 15 min).
62. Como administrador do sistema, não quero conseguir tirar meu próprio acesso de administração.
63. Como usuário sem uma permissão, quero que a aba ou ação correspondente nem apareça e que a URL direta mostre "sem acesso".

### Qualidade
64. Como usuário de celular, quero alvos de toque de 44 px ou mais e texto com contraste ≥ 4,5:1.
65. Como usuário de teclado ou leitor de tela, quero foco visível, rótulos e papéis ARIA corretos em segmentados, chaves, diálogos e listas.
66. Como avaliador, quero rodar a demo com dois comandos e ver o CI verde nas duas frentes.

## Implementation Decisions

### Organização do trabalho
- **Dois blocos independentes:** API (backend existente) e Web (pasta `web/` nova). Cada bloco tem seu worktree e seu branch; as tarefas de um bloco com arquivos disjuntos podem rodar em paralelo.
- **Contratos primeiro:** as seções abaixo são a fonte da verdade. O front implementa handlers MSW com exatamente esses formatos; o backend implementa e testa os mesmos. Mudança de contrato durante a implementação precisa atualizar esta spec, os handlers e a documentação da API no mesmo commit.
- **Formato de erro** continua `{ "error": "<mensagem estável em inglês>" }`; o front traduz por um mapa de mensagens. As mensagens novas estão listadas em cada contrato.
- **Campos** em camelCase inglês, como os DTOs atuais (aliases em português continuam aceitos onde já existem).

### Migrações (Flyway) — números reservados por tarefa
Para tarefas paralelas não colidirem: V5 recuperação de senha · V6 ledger de pontos · V7 histórico de moradia · V8 match (vagas, novos status, votos) · V9 gestão da casa (remoção por votação) · V10 mudança de casa (reserva de 72 h) · V11 despesas · V12 tarefas · V13 mercado. Toda migração passa no `MySqlSchemaCompatibilityTest`. Se a ordem de merge mudar, renumerar antes do merge.

### Contratos: autenticação ([#10](https://github.com/maiocacedo/tds-projetofinal/issues/10), [#11](https://github.com/maiocacedo/tds-projetofinal/issues/11))
- **Client público:** a configuração de clients ganha a flag `public`. `nosso-teto-web` é público e sem secret; `nosso-teto-mobile` continua confidencial. O startup falha se um público tiver secret ou um confidencial não tiver.
- `POST /oauth/token` e `DELETE /oauth/token/revoke`: client público manda `client_id=nosso-teto-web` no corpo, sem `Authorization`; confidencial continua com Basic. Basic **e** `client_id` no corpo → `400 {"error":"invalid_request"}`. Refresh do web: `grant_type=refresh_token&client_id=nosso-teto-web` + cookie HttpOnly.
- `POST /api/auth/register` valida: nome obrigatório (`"name is required"`), e-mail válido (`"invalid email"`), senha ≥ 8 (`"password must have at least 8 characters"`). E-mail já usado mantém o comportamento atual.
- **Recuperação simulada:** `POST /api/auth/password-reset {email}` → sempre `202 {}`. Com a flag de demo ligada (**desligada por padrão**), a resposta é `202 {"demoToken":"..."}`. `POST /api/auth/password-reset/confirm {token, newPassword}` → `204`. Erros: `"invalid or expired token"`, regra da senha. Token guardado como hash, uso único, 15 min; ao trocar a senha, revoga todos os refresh tokens do usuário. Os dois endpoints são públicos.

### Contratos: usuário, ficha e pontos ([#18](https://github.com/maiocacedo/tds-projetofinal/issues/18))
- `GET /api/users/me` passa a trazer também: `profileComplete` (as 5 respostas do match preenchidas), `isHouseAdmin`, `permissions` (lista de nomes), `points: { total, thisMonth }` e `residenceMonths` (inteiro, `null` se nunca morou).
- `GET /api/users/me/points` → `{ total, thisMonth, history: [{ id, reason, amount, createdAt, description }] }`, com `reason` ∈ `CHORE_ON_TIME | EXPENSE_ON_TIME | GROCERY_BOUGHT`.
- **Ledger de pontos:** pessoa, motivo, valor, data e origem (tipo + id + ciclo). Restrição de unicidade por origem: no máximo um crédito por tarefa por ciclo, por parcela e por item. Pontos só somam; desfazer uma compra ou reabrir uma parcela **remove** o crédito daquela origem (ele não aconteceu), nunca grava valor negativo.
- **Histórico de moradia:** entrada e saída de cada casa, gravado em todo ingresso (aceite, criar casa, confirmar mudança) e saída (sair, remoção, mudança). `residenceMonths` soma todas as casas.
- **Compatibilidade:** ficha incompleta → `compatibility: null` em `GET /api/houses` e no detalhe (hoje dá 100%). O front bloqueia o Explorar até `profileComplete`.

### Contratos: match e mudança ([#12](https://github.com/maiocacedo/tds-projetofinal/issues/12))
- **Casa:** novo `vacancies` (inteiro ≥ 0). `isAcceptingMatches` passa a ser o interruptor **Receber pedidos**. A busca lista só casas com `isAcceptingMatches && vacancies > 0`. `PUT /api/houses/{id}` aceita os dois. Pedir vaga em casa pausada ou sem vaga → `400 "house is not accepting requests"`.
- **Status da solicitação:** `PENDING | ACCEPTED | REJECTED | CANCELLED | WITHDRAWN | AWAITING_MOVE | EXPIRED`. Aceitar desconta uma vaga; `EXPIRED`, retirada e recusa da mudança devolvem a vaga.
- `GET /api/matches/requests/mine` → `[{ id, houseId, houseName, status, createdAt, votesCast, votersTotal, moveDeadline }]`.
- `DELETE /api/matches/requests/{id}` → `204`; só o dono e só `PENDING` (`"request is not pending"`); status vira `WITHDRAWN`.
- `GET /api/houses/{id}/matches` traz em cada solicitação `candidate: { id, name, compatibility, points, residenceMonths }`, `voters: [{ id, name }]` (sem a direção) e `myVote: "APPROVE" | "REJECT" | null`. Votar de novo enquanto `PENDING` troca o voto.
- **Morador pode pedir vaga** em outra casa (hoje `400`). Se aceito, a solicitação vai para `AWAITING_MOVE` com `moveDeadline = aceite + 72 h`.
- `POST /api/matches/requests/{id}/confirm-move {newAdminId?}` → `200` com o usuário já na casa nova: sai da atual (mesmo repasse de tarefas, mercado e admin do "sair", incluindo a escolha do herdeiro) e entra na nova **numa transação só**.
- `POST /api/matches/requests/{id}/decline-move` → `204`, vaga devolvida.
- `POST /api/matches/requests/{id}/retract` → `204`; só admin da casa de destino, só em `AWAITING_MOVE`.
- Expiração: reservas vencidas viram `EXPIRED` (job periódico ou verificação na leitura; as duas precisam dar o mesmo resultado).
- Parcelas em aberto de quem sai continuam devidas na casa antiga e aparecem para quem fica, com o nome da pessoa.

### Contratos: gestão da casa ([#13](https://github.com/maiocacedo/tds-projetofinal/issues/13))
- `PUT /api/houses/{id}/members/{memberId}/admin {isAdmin}` → `200`; só admin; `400 "house must keep at least one admin"`.
- `POST /api/houses/{id}/leave {newAdminId?}`: último admin com outros moradores e sem `newAdminId` → `400 "new admin required"`.
- **Remoção por votação** (substitui o `DELETE /members/{memberId}` direto, que sai da API):
  - `POST /api/houses/{id}/removals {memberId, reason?}` → `201`; só admin; um processo aberto por pessoa.
  - `GET /api/houses/{id}/removals` → abertos, com `voters` (sem direção) e `myVote`.
  - `POST /api/houses/{id}/removals/{removalId}/vote {vote}` → `200`; a pessoa alvo não vota.
  - `DELETE /api/houses/{id}/removals/{removalId}` → `204`; quem propôs cancela.
  - Ao atingir a regra da casa, executa sozinha (mesmo repasse do "sair"; as dívidas continuam). Status: `OPEN | APPROVED | REJECTED | CANCELLED`.

### Contratos: despesas ([#14](https://github.com/maiocacedo/tds-projetofinal/issues/14))
- Despesa ganha `paidById` (padrão: quem lançou), `createdById`, `recurringDay` (1–31 ou `null`) e `recurrenceActive`. A parcela de quem pagou nasce paga.
- Parcela ganha `paidAt`, `receivedConfirmedAt`, `reopenedById` e `reopenedAt`.
- `GET /api/houses/{h}/expenses` traz `paidBy {id,name}` e, por parcela, `userName`, `isPaid`, `paidAt`, `receivedConfirmedAt`.
- `PUT …/expenses/{e}/splits/{splitId}/confirm-received` e `PUT …/splits/{splitId}/not-received` → `200`; só o credor. "Não recebi" reabre a parcela e remove o crédito de pontos dela.
- `PUT /api/houses/{h}/expenses/{e}` (título, valor, vencimento, quem pagou, recorrência) e `DELETE …/expenses/{e}` → só quem lançou e só enquanto nenhuma parcela de outra pessoa foi paga (`409 "expense already has payments"`).
- `propose-split` aceita valor **0** (pessoa fora da conta). A soma precisa fechar o total (`"split must add up to the total"`). O front converte % em R$ antes de enviar; a diferença de centavos fica com quem pagou.
- `GET /api/houses/{h}/expenses/summary` → `{ myDebts: [{ creditorId, creditorName, amount }], owedToMe: [{ debtorId, debtorName, amount }], members: [{ userId, name, paidCount, totalCount, openAmount }] }`.
- **Recorrência:** job diário cria a despesa do mês no dia configurado (29–31 caem no último dia em meses curtos), dividida igualmente entre os moradores atuais. Idempotente por mês. Quem criou ou um admin pausa ou edita.
- **Pontos:** "Paguei" até o fim do dia do vencimento → +10 para o devedor.

### Contratos: tarefas ([#15](https://github.com/maiocacedo/tds-projetofinal/issues/15))
Rotas relativas a `/api/houses/{h}`.
- Tarefa ganha `frequency` ∈ `ONCE | TWICE | THRICE | DAILY | BIWEEKLY` (requer 1, 2, 3, 7 ou 1 conclusão no ciclo; `BIWEEKLY` tem ciclo de 2 semanas), `completionsThisCycle`, `requiredCompletions`, `delayedThisCycle`, `linkedToGroceries`, `rotationOrder` (ids) e `nextAssigneeId`.
- `POST /chores` aceita `frequency`, `rotationOrder` (padrão: ordem de entrada) e `linkedToGroceries`.
- `PUT /chores/{c}/complete` registra **uma** conclusão; só o responsável (`403 "only the assignee can complete"`). `COMPLETED` quando atinge `requiredCompletions`.
- `PUT /chores/{c}/uncomplete` → o responsável desfaz a última conclusão do ciclo.
- `PUT /chores/{c}/reopen` → outro morador, só no ciclo atual e só em `COMPLETED`; vai para `DELAYED` na hora e grava quem reabriu.
- `PUT /chores/{c}/rotation {order}` → só admin; a lista tem de ser exatamente os moradores atuais.
- **Troca:** `POST /chores/{c}/swaps {targetChoreId}` → `201`; `PUT /chores/swaps/{s}/accept` e `/reject` (responsável pela tarefa alvo). O responsável temporário vale só no ciclo atual. `GET /chores` traz `pendingSwap` quando houver.
- **Fechar ciclo** (`POST /chores/rotate`, admin, e o job de domingo): credita +20 a quem fechou `COMPLETED` sem ter ficado `DELAYED` no ciclo; o resto segue a regra atual (atrasada fica com o responsável). Quinzenais só giram a cada duas semanas.

### Contratos: mercado ([#16](https://github.com/maiocacedo/tds-projetofinal/issues/16))
Rotas relativas a `/api/houses/{h}`.
- Item ganha `createdById`, `purchasedById` e `purchasedAt`. `GET /groceries` lista os não comprados e os comprados nos últimos 7 dias, com `createdBy`, `assignee` e `purchasedBy` como `{id, name}`.
- `PUT /groceries/{i} {itemName, quantity}` e `DELETE /groceries/{i}` → qualquer morador, só item não comprado (`409 "item already purchased"`).
- `PUT /groceries/{i}/assign` → só item livre (`409 "item already assigned"`); `DELETE /groceries/{i}/assign` → só quem assumiu.
- `PUT /groceries/{i}/purchase` → quem marca vira `assignee` e `purchasedBy` (é o "Comprei mesmo assim"); +5 se `createdById` ≠ quem comprou.
- `PUT /groceries/{i}/unpurchase` → só quem comprou, até 10 min depois; volta ao estado anterior e remove o crédito.

### Contratos: Início e administração
- `GET /api/houses/{h}/dashboard` acrescenta `dueDate` em cada parcela pendente do usuário, `delayedChores: [{ id, description, assigneeName }]`, `points: { mineThisMonth, houseThisMonth }` e os itens do mercado que faltam.
- `GET /api/admin/users` acrescenta `houseName`.
- `GET /api/admin/permissions` → `[{ name, description }]`, com a descrição em português.
- `PUT /api/admin/users/{id}/permissions` revoga **todos os refresh tokens** da pessoa (a resposta continua a mesma).
- **Permissões dos endpoints novos:** seguem o recurso (match → `ROLE_MATCH`, casa → `ROLE_MANAGE_HOUSE`, despesas → `ROLE_MANAGE_EXPENSES`, tarefas → `ROLE_MANAGE_CHORES`, mercado → `ROLE_MANAGE_GROCERIES`, pontos → `ROLE_MANAGE_PROFILE`, admin → `ROLE_ADMIN_USERS`), com scope `read` ou `write`. Sair, confirmar ou recusar mudança e votar remoção exigem só estar autenticado e ser morador, como o `leave`.

### Frontend: módulos
- **Shell e estilo:** tokens OKLCH do design system no tema do Tailwind v4, Bricolage Grotesque, `lucide-react` e o componente `Logo`.
- **Movimento:** utilitários de geleia (indicador deslizante e toque), risco e check desenhados, entradas no scroll, item que conclui no lugar, comemoração e splash, todos com alternativa para movimento reduzido.
- **Sessão (módulo profundo):** access token só em memória; um wrapper de `fetch` com refresh **single-flight** (vários 401 simultâneos geram um só refresh; se ele falhar, logout); login, logout e revoke com o client público. Interface pequena: `login`, `logout`, `request`, `useSession`.
- **API:** um schema Zod por recurso, com tipos derivados, hooks do TanStack Query e invalidação após mutations; um mapa de erros da API para mensagens em pt-BR.
- **Rotas e guard:** React Router 7 com as rotas de [`arquitetura.md`](../../frontend/arquitetura.md). O guard decide o destino a partir de `/me` (sem sessão → landing; sem ficha completa → onboarding; sem casa → candidato; com casa → morador; aceito pendente → boas-vindas). Aba ou ação sem permissão some; a rota mostra "sem acesso".
- **Componentes do design system:** Botão (toque em geleia), Segmentado (indicador geleia), Interruptor, Contador digitável, Item de checklist (risco desenhado), Chip de estado, Alerta, Aviso com Desfazer, Diálogo, Painel lateral, Folha, Barra de abas, Anel de compatibilidade, Sem acesso e Estado vazio.
- **Telas** (quadro de referência do canvas entre parênteses): Landing (E-landing-final + celular) · Sobre (E-sobre) · Splash (E-splash-vermelha + celular) · Acesso (E-acesso) · Ficha (E-onb-ficha) · Explorar (M-explorar-lista) · Detalhe, pedido, solicitações e comemoração (M-candidato) · Votação (M-votacao) · Mudança (M-mudanca) · Início (A2-celular / A2-desktop) · Casa (G-casa) · Configurar (G-configurar) · Criar casa (G-criar + celular) · Contas (D-contas + D-contas-desktop) · Despesa (D-despesa) · Nova despesa (D-nova) · Tarefas (T-semana) · Nova tarefa (T-nova) · Mercado (K-mercado) · Administração (A-usuarios + celular) · Perfil (ficha editável + pontos e histórico).
- **Tabelas:** TanStack Table só na transparência de Contas (computador) e na Administração.
- **Formulários:** React Hook Form + Zod; contadores digitáveis em vagas, quantidade, dia do mês e valores da divisão.

## Testing Decisions

- **Um bom teste** verifica comportamento visível de fora: resposta HTTP, estado gravado e efeitos (pontos, vagas, status) no backend; o que o usuário vê e consegue fazer no frontend. Nada de testar detalhes internos (métodos privados, estado de componente, classes CSS).
- **Pontos de teste (seams) propostos**, sempre o mais alto possível:
  - **Backend, regra de negócio:** testes de serviço no estilo dos atuais (`*ServiceTest`), um arquivo por tarefa. Ledger (idempotência por origem, remoção no desfazer e no "não recebi"), máquina de estados do match e da mudança (72 h, expirar, retirar, confirmar em transação), remoção por votação, recorrência (dias 29–31), frequência e créditos de tarefa e regras do mercado.
  - **Backend, HTTP e segurança:** testes de integração com `@SpringBootTest` + H2 no estilo de `AuthorizationMatrixIntegrationTest`, `OAuthTokenEndpointHttpTest` e `RefreshTokenFlowIntegrationTest`: client público, recuperação de senha, revogação ao trocar permissões e a matriz de permissões dos endpoints novos. Cada tarefa cria a sua classe de teste; a matriz geral é atualizada uma vez, na tarefa de documentação.
  - **Banco:** toda migração passa no `MySqlSchemaCompatibilityTest`.
  - **Frontend, sessão:** testes do wrapper com `fetch` simulado (401 → um refresh só → repete; refresh falho → logout; vários 401 juntos → um refresh).
  - **Frontend, telas:** Vitest + Testing Library contra handlers **MSW** que seguem os contratos desta spec: o usuário vê, clica e o resultado aparece. Um teste por fluxo principal de cada tela (ex.: marcar item como comprado mostra o risco e depois vai para Comprado; desfazer devolve).
  - **Frontend, componentes:** acessibilidade dos componentes interativos (papéis, `aria-checked`/`aria-pressed`, teclado) e a alternativa de movimento reduzido.
- **Gates:** `mvn -B verify` continua verde (JaCoCo ≥ 0,80 e Checkstyle). O CI ganha um job para `web/` (lint, typecheck, testes, build).
- **E2E (RNF03):** ferramenta e golden paths **ainda em aberto** (#7, "Not yet specified"); decidir quando as telas existirem.

## Out of Scope

- Escolha da ferramenta de E2E (decisão pendente).
- Swagger/OpenAPI (RNF04), deploy e plano B de disponibilidade.
- E-mail e push reais (a recuperação de senha é simulada; o aviso de aceite aparece ao abrir o app).
- Ranking de pontos, pontos negativos e votos que pontuam.
- Unidades de medida no mercado (vão no nome do item) e categorias de item.
- Forçar logout imediato do access token (a revogação vale em até 15 min).
- Internacionalização além do pt-BR.

## Further Notes

- **Prazo:** entrega final em 07/12/2026, estendida em cerca de uma semana (data exata a confirmar).
- **Segurança da demo:** a flag da recuperação de senha deixa qualquer pessoa que saiba um e-mail trocar a senha dele. Só na demo local, desligada por padrão.
- **Restrições do repositório:** o `.env` com segredos reais nunca vai para o git; `APP_CRYPTO_KEY` nunca muda; não aplicar migrações no MySQL local sem pedir; PRs empilhados que o Caio revisa e mergeia.
- **Arquivos compartilhados** que só uma tarefa por vez pode tocar: `docs/api-routes.md`, a collection do Postman, a configuração de segurança e os enums de status. A documentação da API e o Postman ficam numa tarefa final única.
- **Referência visual:** os HTML do canvas usam um runtime do canvas e não abrem sozinhos; servem como referência de marcação, estados e lógica de interação (o JS de cada quadro descreve o comportamento esperado).
