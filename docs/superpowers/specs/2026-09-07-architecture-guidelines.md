# Diretrizes de Arquitetura e Engenharia - Nosso Teto

Este documento define as regras e padrões de desenvolvimento que devem ser seguidos rigorosamente por todos os agentes e desenvolvedores no projeto.

## 1. Padrão Arquitetural
- **Backend:** Padrão MVC (Model-View-Controller) rigoroso.
  - `Model`: Entidades JPA e regras de negócio de persistência.
  - `View`: (No caso de API REST, são os DTOs - Data Transfer Objects).
  - `Controller`: Endpoints REST, tratamento de requisições HTTP e delegação para services.
  - `Service`: Regras de negócio da aplicação isoladas.

## 2. Controle de Versão
- Uso obrigatório de **Conventional Commits** para padronizar o histórico do Git:
  - `feat:` (nova funcionalidade)
  - `fix:` (correção de bug)
  - `docs:` (documentação)
  - `style:` (formatação, ponto e vírgula, etc)
  - `refactor:` (refatoração de código)
  - `test:` (adição ou correção de testes)
  - `chore:` (atualização de build, dependências, etc)

## 3. Metodologia de Execução (SDD)
- Utilizaremos **Subagent-Driven Development (SDD)**.
- O agente principal delegará cada subtarefa atômica para um subagente especializado (implementador).
- Nenhuma tarefa será executada sem um escopo claro e isolado.

## 4. Qualidade e Revisão de Código
- **Code Review Obrigatório:** Após a conclusão de QUALQUER tarefa, um subagente revisor de código entrará em ação.
- O revisor analisará: aderência ao padrão MVC, clean code, tratamento de erros e cobertura de testes.
- O código só é mergeado/considerado pronto após a aprovação do revisor.

## 5. Testes e CI
- **TDD / Testes Unitários:** Todo código deve ser testado.
- **Testes E2E (End-to-End):** O fluxo principal da aplicação (cadastro, login, match, gestão) será testado de ponta a ponta.
- **Continuous Integration (CI):** Haverá um pipeline de CI (ex: GitHub Actions) para rodar testes e lint automaticamente em cada push.
