# Decisões do frontend

Uma entrada por decisão, com o ticket onde ela foi tomada. A discussão completa está nos comentários de cada issue.

## Visual e navegação

### Direção visual: A · Quadro da geladeira, revisada — [#8](https://github.com/maiocacedo/tds-projetofinal/issues/8)
Branco e tomate, Bricolage Grotesque. O Início tem alertas no topo (tarefa atrasada, conta vencendo), "Você deve à casa", "Pra você esta semana", pontos (sem ranking) e "Faltando no mercado" no formato de lista. A tabela de transparência de contas da direção B fica para a tela de Contas. A direção C foi descartada.

### Navegação: abas por estado — [#9](https://github.com/maiocacedo/tds-projetofinal/issues/9)
- **Candidato** (sem casa): Explorar, Solicitações, Perfil.
- **Morador**: Início, Contas, Tarefas, Mercado, Casa.
- Perfil, administração do sistema e sair ficam no avatar.
- Ao ser aceito, o candidato passa pela tela de boas-vindas (RF06) e vira morador.
- O morador pode **pedir vaga em outra casa**; se for aceito, **confirma a mudança manualmente**. Pode **sair devendo**, com aviso do que muda.

## Segurança

### SPA como client OAuth público — [#10](https://github.com/maiocacedo/tds-projetofinal/issues/10)
`nosso-teto-web` vira **client público**: manda `client_id` no corpo, sem secret. O `nosso-teto-mobile` continua confidencial com Basic. Basic + `client_id` no corpo ao mesmo tempo → `400 invalid_request`. O startup valida a configuração (público sem secret, confidencial com secret). A demo final roda local, sem deploy. BFF e secret no bundle foram rejeitados.

## Stack — [#17](https://github.com/maiocacedo/tds-projetofinal/issues/17)
Pasta `web/` neste repositório, Vite + React + TypeScript, Tailwind v4 + Radix, React Router 7, TanStack Query, TanStack Table onde houver tabela, React Hook Form + Zod (schemas escritos à mão), access token em memória + proxy do Vite, Vitest + Testing Library + job no CI, npm + Node 22. Detalhes em [arquitetura.md](arquitetura.md).

## Domínio

### Pontuação — [#18](https://github.com/maiocacedo/tds-projetofinal/issues/18)
- Pontos **só somam**, são da pessoa e vão junto na mudança de casa.
- Tarefa no prazo **+20** (creditada no fechamento de domingo, se fechou feita e não ficou atrasada na semana); parcela paga até o vencimento **+10**; item do mercado comprado **+5**, só se outra pessoa adicionou. Atraso vale 0. Votos não pontuam.
- Só o responsável conclui a tarefa. Outro morador pode **reabrir** na mesma semana: ela volta atrasada na hora, com o nome de quem reabriu.
- Aparece no perfil (total + histórico), no Início ("este mês" e total da casa, sem nomes) e no card do candidato, junto do **tempo de moradia** ("340 pts · 8 meses no Nosso Teto"; sem histórico: "Novo no Nosso Teto").

## Entrada — [#11](https://github.com/maiocacedo/tds-projetofinal/issues/11)
- **Landing:** estrutura da proposta 1 com um celular que troca de aba sozinho e vira interativo ao toque; a semana da república; os 4 passos em linha do tempo; "E se…" como conversa com altura fixa; bloco final tomate com o teste de compatibilidade ao vivo e o CTA. CTA para `/cadastro` no topo e no fim. Seções montam conforme o scroll.
- **Sobre:** página própria (`/sobre`) com o projeto, a conta da compatibilidade, privacidade e o design system.
- **Onboarding:** ficha numa tela só. **Obrigatório antes de buscar casas:** as 5 respostas que pesam no match (orçamento, pet, cigarro, organização, visitas); a API passa a devolver score vazio para perfil incompleto.
- **Cadastro:** nome obrigatório, e-mail válido, senha com 8+ caracteres; o front traduz os erros.
- **Esqueci minha senha:** entra, simulado (o botão "Verificar e-mail" faz o papel do link); só com flag de demo.
- **Splash vermelha:** tela tomate, casa branca grande desenhada (sem quadrado), geleia ao fechar o traço, porta abrindo com luz e mergulho pela porta até o site. Primeira abertura de cada sessão, cobrindo o refresh silencioso.
- **Interação:** seleção e toque em geleia, risco desenhado nas checklists (ver [design-system.md](design-system.md)).

## UC01 · Match — [#12](https://github.com/maiocacedo/tds-projetofinal/issues/12)
- **Explorar:** lista com filtros (aluguel em segmentado, aceita pets, aceita fumantes) e cards com anel de compatibilidade; "Por que N%?" abre os 5 critérios explicados.
- **Aprovação de quem já mora em outra casa:** vaga reservada por **72 h**; o admin da casa pode retirar antes; sem resposta, expira.
- **Desistir** de um pedido específico; casa com **número de vagas** (em zero, sai da busca).
- **Aviso de aceite:** ao abrir o app, o aceito vai às boas-vindas (RF06) e depois a uma tela de comemoração; sem e-mail/push.
- **Votação:** mostra quem já votou, sem o voto, e o meu voto, que dá para trocar enquanto pendente.

## Fora do escopo do mapa
Swagger/OpenAPI, plano B de disponibilidade e o código de implementação (vem depois do mapa: spec → plano → execução).
