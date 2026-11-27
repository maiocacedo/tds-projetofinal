// Mensagens de erro das tarefas (B9). As chaves de casa/admin ficam em outros
// módulos: aqui só as exclusivas de `/api/houses/:h/chores*`.
export const messages: Record<string, string> = {
  'only the assignee can complete': 'Só quem está responsável pela tarefa pode marcar como feita.',
  'chore already completed for this cycle': 'Essa tarefa já foi concluída nesta semana.',
  'chore has no completions to undo': 'Não há nenhuma conclusão para desfazer nesta tarefa.',
  'assignee cannot reopen own chore': 'Você não pode reabrir a sua própria tarefa.',
  'chore is not completed': 'Só dá para reabrir uma tarefa que está marcada como feita.',
  'rotation order must match current house members':
    'A roda precisa ter exatamente os moradores atuais da casa. Recarregue e tente de novo.',
  'invalid chore swap': 'Essa troca não é possível. Escolha a tarefa de outro morador.',
  'only the target resident can answer the swap': 'Só quem recebeu o pedido de troca pode responder.',
  'invalid chore frequency': 'Escolha uma frequência válida para a tarefa.',
};
