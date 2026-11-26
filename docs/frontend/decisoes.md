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

## Gestão da casa — [#13](https://github.com/maiocacedo/tds-projetofinal/issues/13)
- **Vários admins** por casa; um admin promove ou rebaixa outro morador, sempre sobrando pelo menos um.
- **Remoção por votação:** um admin propõe (motivo opcional), a casa vota pela mesma regra da casa, sem o voto da pessoa; quem votou aparece, a direção não. Aprovada, a saída usa o mesmo repasse do "sair" e as dívidas continuam.
- **Sair:** se for o último admin, escolhe quem assume. Pode sair devendo, com aviso.
- **Vagas + Receber pedidos:** contador de vagas (cada aceite desconta uma; em zero a casa sai da busca) e um interruptor que pausa pedidos sem zerar as vagas.
- **Criar casa:** uma tela só com prévia ao vivo de como os candidatos veem a casa (no celular, prévia compacta fixa no topo).

## Despesas — [#14](https://github.com/maiocacedo/tds-projetofinal/issues/14)
- Despesa registra **quem pagou** (padrão: quem lançou). As parcelas viram dívida com essa pessoa; Contas mostra "você deve X a fulano" por credor.
- O credor confirma **Recebi** ou marca **Não recebi** (reabre a parcela, com registro).
- **Repetir todo mês** em qualquer dia de 1 a 31 (29–31 caem no último dia nos meses curtos).
- **Divisão customizada** em R$ ou %, com atalho para tirar alguém da conta (valor 0); a soma precisa fechar.
- **Editar/excluir** só enquanto ninguém pagou. Transparência por morador (contas pagas x de y, valor em aberto) no topo de Contas; no computador, tabela.

## Tarefas — [#15](https://github.com/maiocacedo/tds-projetofinal/issues/15)
- **Frequência por tarefa:** 1×, 2×, 3× na semana, todo dia ou a cada 15 dias. Os +20 só vêm cumprindo todas as vezes do ciclo.
- **Ordem da roda** definida pelo admin (padrão: ordem de entrada); "próximo da roda" visível.
- **Troca na semana:** propor e aceitar/recusar; vale só para o ciclo atual.
- **Fazer o mercado** vira tarefa (rotativa, quinzenal) com atalho para a lista.
- Só o responsável conclui; outro morador **reabre**; o admin pode **fechar o ciclo** antes do domingo.

## Mercado e administração do sistema — [#16](https://github.com/maiocacedo/tds-projetofinal/issues/16)
- **Mercado:** três grupos (Falta comprar, Alguém vai comprar, Comprado nos últimos 7 dias, recolhido) e seletor **Tudo / Eu compro** (modo para usar no mercado).
- Quantidade inteira com contador digitável; a unidade vai no nome ("Arroz 5 kg").
- **Eu compro** só em item livre; **Desistir** para quem assumiu; item de outra pessoa → **Comprei mesmo assim** (item e +5 passam para quem comprou).
- Marcar: o item **fica no lugar** (caixa pula, risco desenhado, "+5" sobe) e só depois vai para Comprado. Aviso com **Desfazer** (~5 s) estorna os pontos.
- Editar/apagar item não comprado (qualquer morador). Atalho **Lançar gasto do mercado** abre a Nova despesa já preenchida.
- **Administração** (`/admin/usuarios`): tabela com busca, filtro e ordenação; painel com atalhos (Morador padrão, Admin do sistema) e as 9 permissões como chaves; a própria chave de admin fica travada.
- Salvar mostra a confirmação ("Tira / Dá") e **revoga os refresh tokens** da pessoa: ela sai em até 15 min.
- **Sem permissão:** a aba ou ação some; quem entra pela URL vê "Você não tem acesso — fale com o administrador". "Administração" no avatar só aparece para quem tem `ROLE_ADMIN_USERS`.

## Regras gerais de interface
- **Contadores aceitam digitação** (campo numérico + −/+), em todo lugar.
- **Ícones:** um conjunto só, **Lucide** (`lucide-react`), e um componente `Logo` com a casinha da splash. Nada de emoji ou caractere como ícone.

## Fora do escopo do mapa
Swagger/OpenAPI, plano B de disponibilidade e o código de implementação (vem depois do mapa: spec → plano → execução).
