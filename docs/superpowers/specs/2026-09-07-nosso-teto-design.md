# Nosso Teto - Gestão e Match de Moradias Compartilhadas

## Visão Geral
O **Nosso Teto** visa resolver duas dores centrais da vida em república ou apartamentos divididos: a dificuldade de encontrar pessoas compatíveis para morar junto e os atritos gerados pela desorganização nas finanças e nas tarefas domésticas diárias. O sistema atua como uma plataforma completa (duas jornadas): conecta pessoas compatíveis e fornece ferramentas integradas para gestão da casa.

**Público-alvo:** Estudantes universitários e jovens adultos que dividem aluguel.

## Escopo e Funcionalidades

1. **Sistema de Perfis e Match**
   - Cadastro detalhado de usuários e casas (hábitos, orçamento, pets).
   - Mecanismo de busca de vagas com filtros.
   - Solicitação de "interesse" (match) e aceite/recusa pela casa.
2. **Gestão Financeira Compartilhada**
   - Lançamento de despesas (aluguel, água, internet).
   - Divisão automática de valores entre moradores.
   - Controle de quem pagou sua parte.
3. **Quadro de Tarefas Rotativas**
   - Criação de tarefas domésticas.
   - Atribuição automática e cíclica entre moradores.
4. **Lista de Mercado Colaborativa**
   - Lista sincronizada de itens faltantes.
   - Marcação de itens já comprados.
5. **Dashboard da Casa (Mural)**
   - Visão geral das pendências e alertas da moradia.

## Arquitetura e Tecnologias

- **Backend / API:** Java com Spring Boot (API REST). Testes e documentação com Postman.
- **Frontend:** Single Page Application (SPA) com React.
- **Banco de Dados:** MySQL. Mapeamento relacional com Hibernate/JPA. Versionamento com Flyway.
- **Autenticação / Autorização:** Spring Security com JWT (garantindo o acesso restrito aos dados de cada casa).

## Entidades Principais
- **Usuario:** Credenciais, perfil, hábitos, casa vinculada.
- **Casa:** Detalhes do imóvel, regras, perfil desejado.
- **SolicitacaoMatch:** Registro de interesses em vagas.
- **Despesa:** Contas a pagar e divisão por morador.
- **Tarefa:** Afazeres domésticos e responsável atual.
- **ItemMercado:** Produtos para compra.
