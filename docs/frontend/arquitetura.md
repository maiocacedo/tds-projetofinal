# Arquitetura do frontend

Decidida em [Qual é a stack do front? (#17)](https://github.com/maiocacedo/tds-projetofinal/issues/17) e [#10](https://github.com/maiocacedo/tds-projetofinal/issues/10). Nada disso está implementado ainda; este documento é a referência para a spec e o plano.

## Stack

| Camada | Escolha | Por quê |
|---|---|---|
| Base | Vite + React + TypeScript, pasta `web/` neste repo | RNF01 exige React; um zip e um CI só |
| Estilo | Tailwind v4 com os tokens OKLCH no `@theme` | Protótipos já usam tokens; passa direto para classes |
| Primitivos acessíveis | Radix (dialog, menu, tabs…) | Foco, teclado e ARIA sem impor visual |
| Rotas | React Router 7 (modo biblioteca) | Conhecido, suficiente para ~27 rotas |
| Dados | TanStack Query sobre um wrapper de `fetch` | Cache, invalidação, estados de carregando/erro |
| Tabelas | TanStack Table (headless) | Só onde houver tabela de verdade |
| Formulários | React Hook Form + Zod | Mesmos schemas validam formulário e resposta |
| Testes | Vitest + Testing Library | Prioridade: wrapper de auth; E2E decidido depois |
| Ferramentas | npm + Node 22 LTS (`.nvmrc`, `engines`) | Nada a instalar além do Node |

## Estrutura de pastas (proposta)

```
web/
├── src/
│   ├── api/            # client fetch + schemas Zod por recurso (houses, matches, expenses…)
│   ├── auth/           # sessão: token em memória, refresh single-flight, login/logout
│   ├── routes/         # páginas por rota (publico/, candidato/, morador/, admin/)
│   ├── components/     # componentes do design system (Botao, Segmentado, Alerta, Checklist…)
│   ├── motion/         # geleia, risco desenhado, splash, entradas no scroll
│   └── styles/         # tokens Tailwind (@theme) e CSS base
├── index.html
└── vite.config.ts      # proxy /api e /oauth → http://localhost:8080
```

## Sessão e OAuth

```
Abrir o app ──► splash ──► POST /oauth/token (grant_type=refresh_token, cookie HttpOnly)
                               ├─ 200: access token em memória ─► rota do estado (Início ou Explorar)
                               └─ 400: sem sessão ─────────────► landing
Login ──► POST /oauth/token (password, client_id=nosso-teto-web no corpo, sem secret)
Requisição ──► Authorization: Bearer <token em memória>
   └─ 401 ──► um único refresh em andamento por vez (single-flight) ──► repete a requisição
Sair ──► DELETE /oauth/token/revoke ──► limpa o token em memória
```

- O access token **nunca** vai para `localStorage`/`sessionStorage`; o refresh fica no cookie HttpOnly (`Path=/oauth/token`).
- O proxy do Vite deixa API e front na mesma origem em dev e na demo, sem CORS e sem problema de cookie.
- `sessionStorage` guarda apenas a flag "splash já exibida nesta sessão".

## Rotas por estado

O app tem dois estados logados, porque um usuário mora em no máximo uma casa:

| Estado | Abas | Rotas principais |
|---|---|---|
| Público | — | `/`, `/sobre`, `/entrar`, `/cadastro` |
| Primeiro acesso | — | `/onboarding` (ficha; bloqueia o Explorar até as 5 respostas do match) |
| Candidato | Explorar, Solicitações, Perfil | `/explorar`, `/casas/:id`, `/solicitacoes`, `/casas/nova`, `/bem-vindo` |
| Morador | Início, Contas, Tarefas, Mercado, Casa | `/inicio`, `/contas`, `/contas/:id`, `/tarefas`, `/mercado`, `/casa`, `/casa/candidatos`, `/casa/configurar`, `/mudanca/:requestId` |
| Qualquer logado | avatar | `/perfil`, sair |
| Admin do sistema | avatar | `/admin/usuarios` (só com `ROLE_ADMIN_USERS`) |

Um guard decide o destino a partir de `GET /api/users/me` (tem casa? ficha completa? permissões).

## Dados

- Um schema Zod por recurso em `web/src/api/`, seguindo [`docs/api-routes.md`](../api-routes.md); tipos via `z.infer`.
- Mutations invalidam as queries afetadas (ex.: "Paguei" invalida dashboard e contas).
- Erros da API (`{ "error": "..." }` e erros OAuth) são traduzidos para mensagens em pt-BR.

## Testes e CI

- Vitest + Testing Library: componentes do design system e, principalmente, o wrapper de auth (401 → refresh único → retry; refresh falho → logout).
- Job novo no GitHub Actions para `web/`: lint, typecheck, testes e build.
- E2E (Cypress ou Playwright) e golden paths: decididos quando as telas existirem.

## Demo

`mvn spring-boot:run` e `npm run dev` (em `web/`) lado a lado; o README terá os dois comandos.

## Dependências da API

O front depende de lacunas registradas no [#19](https://github.com/maiocacedo/tds-projetofinal/issues/19), entre elas: client público no OAuth, pontuação e histórico de moradia, listar as próprias solicitações, mudança de casa com confirmação, reabrir tarefa, score vazio para perfil incompleto, validação de cadastro e recuperação de senha simulada.
