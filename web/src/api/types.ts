import { z } from 'zod';

// Schemas sem `.strict()`: campos extras das próximas versões da API passam.
export const userRefSchema = z.object({
  id: z.string(),
  name: z.string(),
});

export type UserRef = z.infer<typeof userRefSchema>;

/** Liga o Mercado (F9) à Nova despesa (F7): "virar conta" pré-preenche o formulário. */
export interface ExpensePrefill {
  title: string;
  itemsSummary?: string;
  totalAmount?: number;
}
