# Nosso Teto — glossário do domínio

**Casa (república):** grupo de pessoas que dividem um imóvel. Tem regras (aluguel por vaga, aceita pets, aceita fumantes, organização esperada, tolerância a visitas, mobília) e uma regra de votação (maioria ou unanimidade).

**Morador:** usuário que mora numa casa. Um usuário mora em **no máximo uma** casa.

**Admin da casa:** morador com poderes de administração daquela casa (configurar, fechar ciclo, propor remoção, ordenar a roda). Uma casa pode ter **vários** admins e sempre tem pelo menos um. Não confundir com o administrador do sistema.

**Vagas:** quantas pessoas a casa ainda aceita. Cada aceite desconta uma; em zero, a casa sai da busca.

**Receber pedidos:** interruptor da casa que pausa novas solicitações sem mexer nas vagas.

**Remoção por votação:** um admin propõe tirar um morador; a casa vota pela regra dela, sem o voto da própria pessoa.

**Administrador do sistema:** usuário com a permissão `ROLE_ADMIN_USERS`, que gerencia usuários e permissões. Não tem relação com casas.

**Candidato:** usuário sem casa, procurando uma. Também chamamos de candidato quem pede vaga, inclusive um morador que quer se mudar.

**Ficha (perfil de hábitos):** orçamento, pets, fumante, organização (1–5), visitas (1–5), mobília, profissão, bio e alergias. As cinco primeiras pesam no match.

**Compatibilidade (match):** nota de 0 a 100 entre uma ficha e uma casa, cinco critérios de até 20 pontos cada. Sempre múltiplo de 5. Ficha incompleta não tem compatibilidade.

**Solicitação (de vaga):** pedido de um candidato para entrar numa casa. Os moradores votam; a regra da casa decide.

**Mudança:** quando um morador é aceito em outra casa, a vaga fica **reservada por 72 h** e ele **confirma a mudança** manualmente: sai da atual e entra na nova de uma vez. Sem confirmação, a reserva expira; a casa nova pode retirá-la antes.

**Despesa:** conta da casa (título, valor, vencimento, comprovante). É dividida em **parcelas**, uma por morador; a divisão pode ser igual ou customizada (proposta e votada).

**Quem pagou (credor):** o morador que pagou a conta para fora da casa. As parcelas dos outros são dívida com ele.

**Parcela:** a parte de uma despesa que cabe a um morador. Ele marca como paga ("Paguei"); o credor confirma ("Recebi") ou reabre ("Não recebi").

**Despesa recorrente:** despesa que se repete todo mês num dia fixo (1 a 31; nos meses curtos, o último dia).

**Tarefa:** afazer da casa, **fixa** (sempre da mesma pessoa) ou **rotativa** (gira entre os moradores).

**Frequência:** quantas vezes a tarefa precisa ser feita no ciclo (1×, 2×, 3×, todo dia) ou se ela é **quinzenal** (ciclo de duas semanas).

**Ciclo:** a semana das tarefas (duas semanas nas quinzenais). Fecha todo domingo: o que não foi feito fica **atrasado** com o mesmo responsável; o resto gira. O admin pode fechar antes.

**Roda:** a ordem em que uma tarefa rotativa passa pelos moradores. O admin define; o padrão é a ordem de entrada.

**Troca:** um morador propõe trocar a sua tarefa pela de outro só no ciclo atual; vale quando o outro aceita.

**Reabrir:** outro morador marca que uma tarefa dada como feita não foi feita; ela volta atrasada, com o nome de quem reabriu.

**Item do mercado:** algo que falta na casa, com quantidade e quem adicionou. Alguém **assume** ("Eu compro", só se ninguém assumiu) e depois marca como **comprado**; quem compra um item assumido por outra pessoa fica com ele ("Comprei mesmo assim").

**Pontos:** reconhecimento que **só soma**: tarefa no prazo, parcela paga até o vencimento, item comprado que outra pessoa anotou. Não há ranking.

**Tempo de moradia:** quanto tempo a pessoa já morou em casas pelo Nosso Teto, somando todas. Aparece junto dos pontos no card do candidato.
