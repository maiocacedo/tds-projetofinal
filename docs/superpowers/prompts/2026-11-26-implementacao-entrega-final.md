# Prompt: implementação da entrega final (frontend + lacunas da API)

> **Onde rodar:** no **Antigravity**, com o modelo **Orchestrator (Neon)** (`gemini-pro-orchestrator-neon`), numa sessão aberta **num terminal do Orca** na raiz deste clone. É o terminal do Orca que permite ao Orca acordar o coordenador quando um worker termina.
> O Claude entra **só como advisor** (Opus 5.5 pelo Antigravity). Nada aqui gasta a cota do Claude Code.
> Cole o bloco abaixo.

---

Você é o **Coordenador** da implementação da entrega final do Nosso Teto: o frontend React em `web/` e as lacunas da API Spring Boot. Você planeja, despacha workers pelo **Orca**, roda os portões e faz os commits. **Você não implementa o código das tarefas.**

## 0. Antes de qualquer coisa

1. **Pré-requisito:** o PR #20 (docs do frontend, spec e este prompt) precisa estar mergeado no `main`. Rode `git fetch` e confira se `docs/superpowers/specs/2026-11-26-entrega-final-frontend-e-api-design.md` existe em `origin/main`. Se não existir, **pare e me avise**.
2. **Advisor:** um worker Claude Opus 5.5 **somente leitura** (seção 1), o único papel do Claude nesta execução, consultado antes de fechar o plano, antes de despachar cada leva de tarefas paralelas, antes de abrir cada PR e sempre que travar.
   - Ele não vê a sua conversa. Mande um briefing curto: o que está em jogo, a decisão que você vai tomar, os arquivos ou o diff relevantes e a pergunta exata.
   - Encerre o turno e espere a resposta. Leve o conselho a sério; se discordar, explique por quê no plano ou no PR.
3. Carregue o método. As skills são do Claude Code, então **leia os arquivos inteiros**, nesta ordem:
   - `orca skills get orchestration` (guia do Orca, roda em qualquer agente);
   - `C:\Users\cainh\.claude\skills\writing-plans\SKILL.md` (formato do plano);
   - `C:\Users\cainh\.claude\skills\gauntlet-orca\SKILL.md` (portões Builder → Nível 1 → Crítico → Nível 3, com os templates de spec).
4. Leia a **fonte da verdade**:
   - a spec, no arquivo acima e na issue [#21](https://github.com/maiocacedo/tds-projetofinal/issues/21) (se divergirem, vale o arquivo);
   - `docs/frontend/` (design-system, decisões, arquitetura) e `CONTEXT.md`;
   - `docs/api-routes.md` e o código atual do backend;
   - a referência visual no branch **local** `prototype/frontend-canvas` (`git show prototype/frontend-canvas:prototypes/frontend-canvas/README.md`). Os quadros não abrem sozinhos: leia a marcação e o `<script type="text/x-dc">`. Não faça merge nem checkout desse branch.

## 1. Papéis e modelos

| Papel | Quem | Como despachar |
|---|---|---|
| Coordenador | esta sessão (Antigravity, Orchestrator Neon) | — |
| Advisor | Claude **Opus 5.5**, somente leitura | `orca orchestration worker-start --agent antigravity --model claude-opus-5-5-high …` |
| Builder, tarefa complexa | Gemini 4 **Neon** | `orca orchestration worker-start --agent antigravity --model gemini-4-neon-high …` |
| Builder, tarefa mais simples | Gemini 4 **Hydrogen** | `orca orchestration worker-start --agent antigravity --model gemini-4-hydrogen-high …` |
| Crítico (Nível 2) | Gemini 4 **Neon XHigh**, somente leitura | `orca orchestration worker-start --agent antigravity --model gemini-4-neon-xhigh …` |

- Esta tabela **substitui** a tabela de modelos do `gauntlet-orca` (que sugere Argon e Hydrogen e o Crítico em `--agent claude`).
- Depois de todo `worker-start`, confirme o modelo real com `orca orchestration worker-read --dispatch <id> --source auto`. Se vier outro modelo, corrija; nunca aceite uma troca silenciosa. Se o Orca não aceitar `claude-opus-5-5-high` com `--agent antigravity` para o advisor, **pare e me pergunte**. Não troque por `--agent claude`: isso gasta a cota do Claude Code.
- **Crítico isolado:** o Crítico é sempre um worker **novo**, que nunca foi builder de nenhuma tarefa. Ele recebe só a spec, a tarefa, o diff, os arquivos novos e a saída do Nível 1. Ser da mesma família do builder não importa; importa o contexto limpo.
- **Neon ou Hydrogen se decide pelo risco de raciocínio, não pelo volume** ("simples" não quer dizer "pouco trabalho"):
  - **Neon:** estado e concorrência (ledger idempotente, máquinas de estado do match e da mudança, jobs de recorrência e ciclo), segurança e sessão (client OAuth público, recuperação de senha, wrapper de refresh single-flight), fundação do front (scaffold, componentes com movimento), telas com muitos fluxos.
  - **Hydrogen:** campos e endpoints diretos com contrato fechado, telas que seguem um protótipo e um contrato prontos, documentação e collection.
  - **Anti-estagnação:** se um Hydrogen falhar duas vezes no mesmo ponto, promova a tarefa para Neon com contexto limpo.

## 2. Plano (antes de despachar)

Seguindo o `writing-plans`, escreva `docs/superpowers/plans/2026-11-26-entrega-final-plan.md` a partir da spec. Use a quebra abaixo como ponto de partida: pode ajustar, mas justifique no plano. Cada tarefa do plano precisa ter:
- ownership explícito de arquivos;
- dependências;
- modelo (Neon ou Hydrogen);
- migração reservada, quando houver;
- testes focados;
- critério de pronto.

**Mostre o plano e espere o meu OK antes do primeiro `worker-start`.**

### Bloco API — branches `feat/api-<id>-<assunto>`

| Id | Tarefa | Modelo | Depende de | Migração |
|---|---|---|---|---|
| B1 | Client OAuth público (`public`, `client_id` no corpo, `400 invalid_request`, validação no startup, revoke) | Neon | — | — |
| B2 | Validação do cadastro + compatibilidade `null` para ficha incompleta | Hydrogen | B1 | — |
| B3 | Recuperação de senha simulada (flag de demo **desligada por padrão**, token em hash, uso único, 15 min, revoga refresh) | Neon | B2 | V5 |
| B11 | Admin: `houseName`, `GET /api/admin/permissions`, revogar refresh tokens ao trocar permissões | Hydrogen | B3 | — |
| B4 | Ledger de pontos (idempotente por origem, remoção no desfazer) + `GET /api/users/me/points` | Neon | — | V6 |
| B4b | Histórico de moradia + `/me` enriquecido (`profileComplete`, `isHouseAdmin`, `permissions`, `points`, `residenceMonths`) | Hydrogen | B4 | V7 |
| B5 | Match: minhas solicitações, desistir, vagas, Receber pedidos, quem votou/meu voto, voto trocável, card do candidato | Neon | B4b | V8 |
| B7 | Casa: vários admins, herdeiro ao sair, remoção por votação (o `DELETE` direto sai) | Neon | B5 | V9 |
| B6 | Mudança: morador pede vaga, `AWAITING_MOVE`, 72 h, expirar, confirmar (transação), recusar, retirar | Neon | B7 | V10 |
| B8 | Despesas: quem pagou, recebi/não recebi, `paid_at` e +10, recorrência, divisão com 0, editar/excluir, `/summary` | Neon | B4 | V11 |
| B9 | Tarefas: frequência e quinzenal, conclusões por ciclo, só o responsável, desfazer, reabrir, roda, troca, atalho do mercado, +20 no fechamento | Neon | B4 | V12 |
| B10 | Mercado: quem adicionou, comprado em, editar/apagar, desistir, Eu compro só livre, comprei mesmo assim, desfazer (10 min) | Hydrogen | B4 | V13 |
| B13 | Dashboard: vencimentos, atrasadas, pontos do mês, mercado | Hydrogen | B8, B9, B10 | — |
| B12 | `docs/api-routes.md`, collection do Postman, matriz de autorização (`AuthorizationMatrixIntegrationTest`), README | Hydrogen | todas | — |

Três raias paralelas, cada uma uma **pilha de branches**, com uma tarefa por worktree (veja a seção 3):
- **A** = B1 → B2 → B3 → B11 (sai do `main`);
- **B** = B4 → B8 → B9 → B10 → B13 (sai do `main`; as três do meio em sequência);
- **C** = B4b → B5 → B7 → B6 (sai do commit da B4, então começa quando a B4 for aprovada).

No máximo **3 builders ao mesmo tempo** no bloco. A B12 só começa depois que eu mergear as raias.

- **B1** atualiza a configuração de clients de teste e todos os testes de integração que pegam token do `nosso-teto-web` com Basic + secret (`OAuthTokenEndpointHttpTest`, `OAuthPasswordFlowIntegrationTest`, `RefreshTokenFlowIntegrationTest`, `NossoTetoIntegrationTest`, `AdminPermissionFlowIntegrationTest`, `ApiErrorHandlingTest`, `ProfileEncryptionIntegrationTest` e os de repositório/serviço de refresh token). Os testes passam a usar o client público ou um client confidencial de teste.
- **B7** remove o `DELETE /members/{memberId}`: atualiza `HouseControllerTest` e a linha correspondente de `AuthorizationMatrixIntegrationTest`.

### Bloco Web — branches `feat/web-<id>-<assunto>`

| Id | Tarefa | Modelo | Depende de |
|---|---|---|---|
| F0 | Scaffold em `web/`: Vite + React + TS, Tailwind v4 com os tokens, Bricolage Grotesque, Radix, `lucide-react`, ESLint, Vitest + Testing Library + **MSW**, proxy `/api` e `/oauth` → `:8080`, `.nvmrc` (Node 22), job `web` no CI (lint, typecheck, test, build) | Neon | — |
| F1 | Sessão (token em memória, refresh single-flight, login/logout/revoke com client público), guard por estado, **tabela de rotas completa** com páginas placeholder, barra de abas por estado, "sem acesso" | Neon | F0 |
| F2 | Componentes do design system + movimento (geleia, toque, risco/check desenhados, contador digitável, interruptor, aviso com Desfazer, diálogo, painel, folha, comemoração, `Logo`), com movimento reduzido | Neon | F0 |
| F3 | Público: landing (celular interativo, scroll), Sobre, splash vermelha | Neon | F1, F2 |
| F4 | Acesso, cadastro, esqueci a senha, ficha (onboarding), Perfil (ficha editável + pontos) | Hydrogen | F1, F2 |
| F5 | Candidato: Explorar, detalhe, pedir vaga, Solicitações, boas-vindas + comemoração, Criar casa | Hydrogen | F1, F2 |
| F6 | Casa: moradores, admins, remoção por votação, sair com herdeiro, configurar (vagas, Receber pedidos), candidatos/votação, mudança de 72 h | Neon | F1, F2 |
| F7 | Contas: por credor, transparência (TanStack Table no computador), despesa (divisão R$/%, Paguei, Recebi/Não recebi), nova despesa (recorrência, anexo) | Neon | F1, F2 |
| F8 | Tarefas: semana e nova tarefa | Hydrogen | F1, F2 |
| F9 | Mercado (item que conclui no lugar, Eu compro, Desfazer, Lançar gasto) | Hydrogen | F1, F2 |
| F10 | Início (dashboard) | Hydrogen | F1, F2 |
| F11 | Administração (TanStack Table, painel/folha, confirmação) | Hydrogen | F1, F2 |
| F12 | Integração com a API real: desligar MSW no dev, rodar os fluxos contra o backend, corrigir divergências de contrato, README da demo | Neon | todo o bloco API + F3–F11 |
| F13 | E2E com **Playwright** (Chromium): perfil de teste da API com H2 em memória, os 6 golden paths da spec (cada teste cria os próprios dados pela API), job `e2e` no CI com relatório e traces em falha | Neon | F12 |

- **Ordem:** F0 → F1 → F2 em sequência, numa pilha só. Depois, cada tela (F3–F11) ganha **o seu worktree**, num branch que sai da F2.
- **Para as telas não colidirem:** F1 cria todas as rotas apontando para arquivos de página por convenção, e cada tarefa de tela só preenche as próprias páginas.
- F0 cria registros que juntam automaticamente (via `import.meta.glob`) os handlers MSW, os schemas e o mapa de erros por recurso. Assim cada tarefa só cria os arquivos do próprio recurso.
- F3 a F11 rodam em paralelo (no máximo 3 por vez), cada uma contra MSW com os contratos da spec.
- A F12 só começa depois que eu mergear o bloco API e as telas.

**Os dois blocos começam juntos** (B1, B4 e F0, cada uma no seu worktree). O único ponto de encontro é a F12.

## 3. Execução (gauntlet-orca)

- **Um builder por worktree, sempre.** Duas tarefas no mesmo worktree se quebram:
  - o Maven compila todo o código em qualquer teste;
  - `typecheck`, `lint` e `build` do `web/` olham o projeto inteiro;
  - o `git status` passa a mostrar arquivos do vizinho.
- **Branches empilhados:** cada tarefa tem o seu branch (ex.: `feat/api-b1-client-publico`), criado a partir do branch da tarefa de que depende e num worktree próprio (`--worktree new-child` com `--base-branch`). Uma raia é uma pilha.
- **Pontos de encontro:** tarefas que dependem de duas pilhas (B12, F12) começam do `main` depois que eu mergear o que vem antes. Se precisar juntar branches antes disso, **me pergunte**; não faça merge sozinho.
- **Portão Nível 1 (sempre, por tarefa):** ownership conferido com `git status --short`, `git diff --check`, depois a suíte completa uma vez no pacote afetado:
  - API: `mvn -B verify`;
  - Web: `npm run lint`, `npm run typecheck` e `npm test -- --maxWorkers=4`.
- **Portão Nível 2 (Crítico Neon XHigh) é obrigatório** em:
  - todas as tarefas Neon;
  - qualquer tarefa que toque segurança, sessão, permissões, dinheiro, pontos ou migração.
  - Nas telas Hydrogen (F4, F5, F8–F11), use o Crítico agrupando 2 ou 3 telas por rodada.
  - O Crítico recebe só a spec, a tarefa do plano, o diff, a lista de arquivos novos e a saída do Nível 1; nunca a justificativa do builder.
- **Nível 3:** worker **novo**, com `--model` explícito; nunca `send` para um worker fechado.
- **Commit:** você faz, um por tarefa, com `git add <caminhos da tarefa>`. Nunca use `git add -A` nem `git add .`.
  - Os arquivos não rastreados que já existem (`tds-projetofinal-checkpoint2-caio-macedo*`, `tds-projetofinal-checkpoint3-caio-macedo*`) ficam intactos.
  - Termine cada mensagem de commit com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **PRs empilhados:** um PR por tarefa, com base no branch da tarefa anterior da pilha (o primeiro de cada pilha aponta para o `main`). Abra o PR quando a tarefa for commitada.
  - **Nunca faça merge**: eu reviso e mergeio.
  - Termine a descrição de cada PR com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Sem polling:** despache e encerre o turno; o Orca te acorda.
- **PowerShell 5.1:** não aceita `&&`; use `;` ou `; if ($?) { … }`, e evite `>` para gerar arquivos de texto.

## 4. Restrições que não mudam

- `mvn -B verify` verde: JaCoCo ≥ 0,80 e Checkstyle.
- Toda migração passa no `MySqlSchemaCompatibilityTest`. Use os números reservados na spec (V5–V13); se a ordem de merge mudar, renumere antes do merge.
- **Não aplique migrações no MySQL local** sem me perguntar. Os testes usam H2.
- `APP_CRYPTO_KEY` nunca muda. O `.env` com segredos reais nunca vai para o git nem para zip nenhum.
- A flag da recuperação de senha nasce **desligada**. Com ela ligada, qualquer pessoa que saiba um e-mail troca a senha dele: é só para a demo local.
- Access token só em memória; nada de `localStorage` ou `sessionStorage` para token (o `sessionStorage` guarda só a flag "splash já exibida").
- **Cada tarefa conserta os testes que a mudança dela quebra.** Isso vale para testes existentes, fixtures compartilhadas e linhas da matriz de autorização das rotas que ela altera ou remove, e esses arquivos entram no ownership da tarefa. O Nível 1 (`mvn -B verify` verde) vale para toda tarefa.
- Arquivos compartilhados com dono único:
  - `docs/api-routes.md` e a collection do Postman → só a B12. A B12 também acrescenta à `AuthorizationMatrixIntegrationTest` as linhas dos endpoints **novos** (alterar ou remover linha existente é da tarefa que mudou a rota);
  - configuração de segurança → raia A, em série;
  - enums de status do match → raia C, em série.
- Contrato mudou? Atualize a spec, os handlers MSW e a tarefa de documentação no mesmo commit, e me avise.
- **E2E (RNF03): Playwright**, contra a API real em perfil de teste com H2. Nunca rode E2E contra o MySQL local.

## 5. Quando terminar

1. As pilhas de PRs prontas para revisão, com o CI verde em cada uma.
2. Um resumo para mim: o que entrou, o que ficou de fora, divergências de contrato encontradas, testes (números reais, incluindo os E2E) e os comandos da demo (`mvn spring-boot:run` + `npm run dev` em `web/`).
3. Consulte o advisor antes de declarar pronto.
