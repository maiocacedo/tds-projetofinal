# Especificação de Requisitos e Casos de Uso - Nosso Teto

Este documento consolida os resultados da entrevista de levantamento de requisitos, definindo o escopo funcional e não funcional do sistema "Nosso Teto".

## 1. Atores do Sistema
- **Usuário Buscando Vaga:** Indivíduo com perfil cadastrado que procura uma casa/república.
- **Morador:** Usuário que já pertence a uma casa.
- **Administrador da Casa:** Morador com permissões para editar regras da casa, vagas e gerenciar moradores.

## 2. Requisitos Funcionais (RF)

### 2.1. Perfis e Sistema de Match
- **RF01:** O sistema deve permitir o cadastro de usuários com atributos detalhados: orçamento mensal, pets, fumante, profissão, biografia, alergias, nível de organização (habilidades complementares), tolerância a visitas e mobília própria.
- **RF02:** O sistema deve permitir o cadastro de "Casas" com atributos compatíveis com os perfis de usuários (regras de visitas, mobília existente, etc).
- **RF03:** O sistema deve calcular e exibir o grau de compatibilidade entre um candidato e uma casa.
- **RF04:** O sistema deve permitir que um usuário envie uma "Solicitação de Interesse" para uma vaga.
- **RF05:** O sistema deve permitir que os moradores de uma casa votem para aprovar ou rejeitar um candidato. A aceitação requer aprovação por votação.
- **RF06:** Ao ser aceito em uma casa, o sistema deve questionar o candidato se ele deseja cancelar ou manter suas outras solicitações ativas.

### 2.2. Gestão Financeira
- **RF07:** O sistema deve permitir o registro de despesas da casa (título, valor, vencimento e anexos de comprovantes).
- **RF08:** O sistema deve, por padrão, dividir o valor da despesa igualmente (50/50) entre todos os moradores da casa.
- **RF09:** O sistema deve permitir a proposição de divisões customizadas (porcentagem ou valor fixo por pessoa) para uma despesa.
- **RF10:** Uma divisão de despesa customizada só será aplicada se aprovada por votação entre os moradores.
- **RF11:** O sistema deve permitir que os moradores marquem individualmente que já pagaram a sua parte da despesa (funcionando como um "caderninho" de controle, sem carteira digital interna).

### 2.3. Gestão de Tarefas e Mercado
- **RF12:** O sistema deve permitir a criação de tarefas domésticas, definindo se são de atribuição fixa a um morador ou rotativas.
- **RF13:** O ciclo de tarefas deve ser semanal (resetando aos domingos).
- **RF14:** O sistema deve registrar atrasos automaticamente para tarefas não marcadas como "concluídas" até o fim do ciclo semanal.
- **RF15:** O sistema deve manter uma lista de mercado colaborativa, onde itens podem ser adicionados por qualquer morador.
- **RF16:** O sistema deve permitir que um morador atribua a si mesmo a responsabilidade de comprar um item específico da lista de mercado.

## 3. Requisitos Não Funcionais (RNF)

### 3.1. Arquitetura e Engenharia
- **RNF01 (Stack):** Backend em Java/Spring Boot (MVC) e Frontend em React. Banco de dados MySQL.
- **RNF02 (Integração):** O projeto deve possuir um pipeline de Continuous Integration (CI) completo.
- **RNF03 (Testes):** O sistema deve possuir testes unitários/integração (TDD) e testes End-to-End (E2E) para os fluxos principais.
- **RNF04 (Documentação):** A API REST deve ser documentada via Swagger/OpenAPI. O processo de desenvolvimento via agentes deve gerar relatórios de hand-off a cada etapa concluída.

### 3.2. Usabilidade e Desempenho
- **RNF05 (Responsividade):** A interface deve ser plenamente responsiva, adaptando-se a celulares e desktops de forma fluida.
- **RNF06 (Performance):** O sistema deve garantir tempos de resposta rápidos nas listagens de matches e painéis da casa.

### 3.3. Segurança
- **RNF07 (Autenticação):** A API deve ser protegida por tokens JWT.
- **RNF08 (Autorização):** Moradores só podem acessar dados financeiros e de tarefas da casa à qual pertencem.
- **RNF09 (Privacidade e Criptografia):** O sistema deve aplicar segurança robusta a dados sensíveis, garantindo que anexos de comprovantes financeiros sejam acessíveis apenas por moradores da casa. Senhas e dados pessoais críticos devem ser encriptados no banco de dados.

## 4. Casos de Uso Principais (Resumo)
- **UC01 - Encontrar Moradia:** Usuário preenche perfil -> Busca vagas com filtros -> Avalia compatibilidade -> Envia solicitação -> Casa vota -> Usuário é aceito.
- **UC02 - Registrar Conta:** Morador adiciona despesa -> Sistema divide igualitariamente -> Moradores marcam como pago.
- **UC03 - Mudar Regra de Divisão:** Morador propõe divisão customizada -> Moradores votam -> Nova regra é aplicada.
- **UC04 - Ciclo de Limpeza:** Sistema atribui tarefas semanais -> Morador marca como concluída -> (Se falhar) Sistema gera alerta de atraso no domingo.
