# Frontend do Nosso Teto

Planejamento do frontend da entrega final. As telas são desenhadas e aprovadas **antes** de qualquer código, seguindo o mapa [Frontend do Nosso Teto (#7)](https://github.com/maiocacedo/tds-projetofinal/issues/7).

| Documento | Conteúdo |
|---|---|
| [design-system.md](design-system.md) | Cores, tipografia, componentes, movimento (efeito geleia, risco desenhado, splash) |
| [decisoes.md](decisoes.md) | Registro das decisões já tomadas, com o ticket de origem |
| [arquitetura.md](arquitetura.md) | Stack, estrutura de pastas, sessão OAuth, rotas por estado, dados e testes |
| [../../CONTEXT.md](../../CONTEXT.md) | Glossário do domínio (candidato, morador, pontos, ciclo…) |
| [Spec da implementação](../superpowers/specs/2026-11-26-entrega-final-frontend-e-api-design.md) | Histórias, contratos da API, módulos e testes da entrega final |

## Onde estão os protótipos

Os protótipos clicáveis ficam num canvas de design no claude.ai (privado; o dono compartilha pelo menu Share). Páginas do canvas:

- **Direções visuais:** as três direções iniciais e a **A revisada**, escolhida.
- **Navegação e telas:** inventário de cerca de 27 telas com rotas e a navegação **abas por estado**.
- **Entrada:** landing final (desktop e celular), Sobre com o design system, acesso, ficha de onboarding e splash.
- **UC01 · Match:** Explorar (lista escolhida), fluxo do candidato com comemoração, votação dos moradores e confirmação de mudança.
- **Gestão da casa:** casa (morador e admin), configurar (vagas, Receber pedidos) e criar casa (desktop e celular).
- **Despesas:** contas (celular e desktop), detalhe com divisão customizada e nova despesa com recorrência.
- **Tarefas:** semana (marcar, 3× na semana, trocar, reabrir, fechar ciclo) e nova tarefa.
- **Mercado e admin:** lista do mercado e administração de usuários (desktop e celular).

Uma cópia dos arquivos do canvas (HTML dos quadros e geradores) fica no branch local `prototype/frontend-canvas`, fora do main, para servir de referência visual na implementação.

## Situação do mapa

| Ticket | Assunto | Situação |
|---|---|---|
| #8 | Direção visual | decidido |
| #9 | Navegação candidato × morador | decidido |
| #10 | `client_secret` no SPA | decidido |
| #17 | Stack | decidido |
| #18 | Pontuação | decidido |
| #11 | Entrada (landing, sobre, acesso, onboarding, splash) | decidido |
| #12 | Busca, compatibilidade, solicitações e votação | decidido |
| #13 | Gestão da casa | decidido |
| #14 | Despesas | decidido |
| #15 | Tarefas | decidido |
| #16 | Mercado e administração do sistema | decidido |

As lacunas de API que os protótipos revelam ficam no [Backlog: lacunas da API para o frontend (#19)](https://github.com/maiocacedo/tds-projetofinal/issues/19) e estão especificadas, com contrato, na spec da implementação.
