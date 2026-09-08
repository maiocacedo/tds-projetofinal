# Plano de Documentação Pré-Implementação

Antes de escrevermos qualquer linha de código ou o plano de implementação técnico, executaremos as tarefas de documentação abaixo. Cada etapa deve ser validada pelo usuário.

- [ ] **Fase 0: Levantamento de Requisitos**
  - Documento alvo: `docs/requirements.md`
  - Conteúdo: Entrevista com o usuário para definição de Requisitos Funcionais (RF), Requisitos Não Funcionais (RNF) e Casos de Uso (UC).

- [ ] **Fase 1: Mapeamento da API REST**
  - Documento alvo: `docs/api-routes.md`
  - Conteúdo: Rotas completas para Autenticação, Usuários, Casas, Matches, Despesas, Tarefas e Mercado. Verbos HTTP, payloads e respostas esperadas.

- [ ] **Fase 2: Modelagem de Banco de Dados**
  - Documento alvo: `docs/database-schema.md`
  - Conteúdo: Diagrama Entidade-Relacionamento (conceitual/texto), tipagem de colunas, chaves primárias, estrangeiras e constraints.

- [ ] **Fase 3: Mapeamento de Telas (UI)**
  - Documento alvo: `docs/ui-screens.md`
  - Conteúdo: Estrutura de navegação do React, páginas necessárias, componentes reutilizáveis principais e fluxos de estado.

- [ ] **Fase 4: Estratégia de Segurança, CI e E2E**
  - Documento alvo: `docs/security-ci-strategy.md`
  - Conteúdo: Detalhamento de como o JWT será validado, proteção de endpoints, configuração do pipeline de CI e ferramentas de E2E (ex: Cypress ou Playwright).

**Critério de Pronto:** 
Após a conclusão destas 4 fases, teremos uma base sólida e inquestionável. Só então invocaremos a skill `writing-plans` para gerar as tarefas granulares de código, que serão executadas via Subagentes (SDD).
