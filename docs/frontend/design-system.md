# Design system do Nosso Teto

Direção **A · Quadro da geladeira, revisada** ([#8](https://github.com/maiocacedo/tds-projetofinal/issues/8)). Registro de produto: o design serve ao uso do dia a dia; a landing e o Sobre são as únicas superfícies de marca e podem ousar mais.

## Princípios

1. **Cada cor tem uma função.** Nenhuma cor aparece só para enfeitar.
2. **O mais importante primeiro.** Alertas (atrasado, vencendo) vêm no topo; o resto vem depois.
3. **Ninguém perde.** Pontos só somam, nunca aparecem em vermelho e não existe ranking.
4. **Movimento com propósito.** O app se mexe bastante (geleia, risco desenhado, entradas no scroll), mas tudo respeita `prefers-reduced-motion`.
5. **Contraste que se lê no sol.** Texto ≥ 4,5:1; alvos de toque ≥ 44 px.

## Cores

Tokens em OKLCH. O fundo é **branco puro**: nada de bege.

| Token | Valor | Uso |
|---|---|---|
| `--tomate` | `#c9402a` | Ação principal, o que é seu, o que pede atenção agora. Texto branco sobre ele: ≈ 4,9:1 |
| `--tomate-escuro` | `#a8331f` | Texto tomate sobre branco, hover |
| `--tinta` | `oklch(0.22 0.03 35)` | Texto, botões secundários, indicador de seleção |
| `--texto-suave` | `oklch(0.40 0.03 35)` | Parágrafos de apoio |
| `--legenda` | `oklch(0.45 0.03 35)` | Legendas e metadados (≥ 4,5:1 sobre branco) |
| `--superficie` | `oklch(0.965 0.015 33)` | Blocos, listas, trilhos de segmentado |
| `--linha` | `oklch(0.91 0.02 33)` | Divisórias e bordas de 1 px |
| `--fundo` | `oklch(1 0 0)` | Fundo de tudo |

### Cores de estado

Sempre em par fundo + texto, nunca a cor sozinha.

| Estado | Fundo | Texto | Uso |
|---|---|---|---|
| Atrasado | `oklch(0.94 0.05 25)` | `oklch(0.36 0.14 25)` | Tarefa que passou do domingo |
| Vence logo | `oklch(0.95 0.06 80)` | `oklch(0.36 0.09 60)` | Conta perto do vencimento, parcela em aberto |
| Feito | `oklch(0.95 0.04 150)` | `oklch(0.40 0.10 150)` | Pago, comprado, concluído |
| Pontos | `oklch(0.985 0.02 90)` + borda `oklch(0.90 0.05 85)` | `oklch(0.45 0.11 70)` | Pontuação (só soma) |

## Tipografia

Uma família só: **Bricolage Grotesque** (Google Fonts, pesos 400–800). A hierarquia vem de tamanho e peso.

| Papel | Tamanho | Peso | Observação |
|---|---|---|---|
| Display (landing) | 66 px desktop / 40 px celular | 800 | `letter-spacing: -0.035em`, `text-wrap: balance` |
| Título de seção | 44–48 px / 32 px | 800 | |
| Título de tela | 21–30 px | 700–800 | ex.: "Bom dia, Ana" |
| Corpo | 16–17 px | 400–500 | linhas até ~65 caracteres, `text-wrap: pretty` |
| Legenda | 12–13 px | 500–700 | datas, metadados |

## Forma

- Botões e chips em **pílula** (`border-radius: 999px`).
- Blocos com raio de **18 a 28 px**; cartões grandes até 36 px. Nada de quina viva.
- Sem bordas laterais coloridas, sem glassmorphism, sem gradiente em texto.
- Altura mínima de botão: 44 px (52–56 px nas ações principais).

## Componentes

| Componente | Descrição |
|---|---|
| Botão principal | Pílula tomate, texto branco 700 |
| Botão secundário | Pílula com borda 2 px tinta, fundo transparente |
| Alerta | Bloco com cor de estado, `role="alert"` (atrasado) ou `role="status"` (vence logo), ação curta à direita ("Paguei") |
| "Você deve à casa" | Bloco tomate com o valor em 28–30 px |
| Chip de estado | Pílula pequena (12 px, 700) com par de estado |
| Segmentado | Trilho em superfície + indicador deslizante (ver *geleia*) |
| Checklist | Caixa 18 px, raio 6 px; ao marcar, o check é traçado e o texto é riscado com animação |
| Barra de abas (celular) | 5 abas com ícone + rótulo; indicador desliza com geleia |
| Tabela | TanStack Table onde houver tabela de verdade (transparência de contas, admin) |
| Contador | Campo numérico digitável entre −/+ (sem setas nativas); o número faz geleia ao mudar. Vagas, quantidade, dia do mês, valores da divisão |
| Interruptor | Trilho tomate quando ligado; a bolinha desliza com overshoot e geleia |
| Aviso (toast) | Pílula escura embaixo, `role="status"`, some em ~5 s; ações reversíveis trazem **Desfazer** |
| Painel / folha | Desktop: painel lateral que entra da direita. Celular: folha que sobe de baixo. Confirmações em diálogo central |
| Sem acesso | Estado de página para rota sem permissão: cadeado, explicação, "Voltar para o Início" |

## Ícones

Um conjunto só: **Lucide** (`lucide-react`), traço 2 px com pontas arredondadas, o mesmo desenho dos SVGs dos protótipos. A marca é o componente `Logo` (a casinha da splash). Nada de emoji ou caractere (⌂, ✓, ⋯, ↻) fazendo papel de ícone; ícone sozinho sempre tem `aria-label`.

## Movimento

Todo movimento tem alternativa em `@media (prefers-reduced-motion: reduce)`: troca direta ou fade curto.

### Seleção em geleia
Em abas, segmentados e grupos de opção existe **um único indicador** que desliza até a opção escolhida, passando um pouco do ponto, e faz squash-and-stretch ao chegar.
- Deslize: `left`/`width` com `cubic-bezier(0.34, 1.56, 0.64, 1)`, ~600 ms.
- Geleia: `scale(1,1) → (1.3,.8) → (.9,1.1) → (1.05,.96) → (1,1)` em ~650 ms. Para reiniciar a cada troca, alternar entre dois `@keyframes` idênticos.
- O texto troca de cor com `transition: color .3s`.

### Toque em geleia
Todo botão, pílula, aba e opção: ao apertar, achata (`scale: 1.07 .88` em ~70 ms); ao soltar, volta com overshoot elástico (~600 ms, `cubic-bezier(0.34, 1.9, 0.5, 1)`).

### Risco desenhado
Ao concluir um item de checklist, o risco atravessa o texto da esquerda para a direita (~500 ms, gradiente de 2 px com `background-size` de 0 a 100%) e o ✓ da caixinha é traçado (`stroke-dashoffset`). Desmarcar apaga no sentido inverso.

### Entradas no scroll
Seções da landing sobem e aparecem ao entrar na tela (`animation-timeline: view()` no protótipo; no app, uma solução que funcione em todos os navegadores). O conteúdo é visível por padrão: a animação só melhora, nunca esconde.

### Splash
Versão **vermelha**, na primeira abertura de cada sessão: a tela já nasce tomate, a casa branca grande é desenhada (sem quadrado), assenta em geleia, a porta abre com luz e a câmera mergulha nela até o site. ~2,4–3,2 s, botão Pular, e cobre o refresh silencioso da sessão (logado → Início; deslogado → landing).

### Item que conclui no lugar
Ao marcar um item que muda de grupo (mercado: "Comprado"), ele **não pula** na hora: a caixa faz geleia, o check e o risco são desenhados, o feedback sobe ("+5") e só depois (~1 s) o cartão some de leve e reaparece no grupo novo.

### Comemoração
Momentos de conquista (entrar numa casa) ganham comemoração: ícone que pousa em geleia, confete saindo do ícone nas cores do app, raios suaves girando atrás e textos subindo em sequência. Sem confete com `prefers-reduced-motion`.

## Escrita

- pt-BR, direto, segunda pessoa ("Você deve à casa", "Pra você esta semana").
- Mensagens de erro dizem o que fazer: "E-mail ou senha não conferem. Confira e tente de novo."
- Não revelar se um e-mail tem conta (recuperação de senha responde sempre igual).

## Dados de exemplo (protótipos)

República Jabuticaba: Ana (você), Bruno (admin), Luiza e Tiago. Despesas: Luz R$ 249,60 (Bruno pagou), Gás R$ 400,00 (Luiza pagou), Internet R$ 100,00 e Aluguel R$ 3.400,00 (Bruno, todo mês). A Ana deve R$ 87,40 ao Bruno e R$ 100,00 à Luiza. Mercado: Detergente × 3, Papel higiênico, Café 500 g × 2 (Bruno vai comprar), Ovos × 2 (Ana vai comprar). Candidatos: Marina Costa 85% (420 pts · 8 meses no Nosso Teto) e Rafael Fontes 70% (Novo no Nosso Teto). A compatibilidade sempre fecha em múltiplos de 5.
