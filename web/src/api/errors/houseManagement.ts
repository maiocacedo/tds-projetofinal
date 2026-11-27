// Erros da gestão da casa (F6): vagas, admins, herdeiro e remoção por votação.
export const messages: Record<string, string> = {
  'house has no available vacancies':
    'A casa não tem vagas livres agora. Abra uma vaga em Configurar antes de aceitar.',
  'house must keep at least one admin':
    'A casa precisa de pelo menos um admin. Promova outra pessoa antes de sair do cargo.',
  'new admin required':
    'Você é o único admin: escolha quem assume a administração da casa antes de sair.',
  'removal already open for this member':
    'Já existe uma proposta de saída em votação para essa pessoa.',
  'target member cannot vote on own removal':
    'Quem está em votação não vota na própria saída.',
};
