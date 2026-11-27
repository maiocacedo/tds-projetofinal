import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useId } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import type { UserProfile } from '../../api/me';
import {
  formatResidenceMonths,
  type UpdateProfilePayload,
} from '../../api/profile';
import {
  Alert,
  Avatar,
  Button,
  Segmented,
  StatusChip,
  Switch,
} from '../../components';

const SCALE_OPTIONS = [
  { value: '1', label: '1' },
  { value: '2', label: '2' },
  { value: '3', label: '3' },
  { value: '4', label: '4' },
  { value: '5', label: '5' },
];

const BUDGET_PRESETS = [600, 850, 1200, 1500] as const;

const fichaSchema = z.object({
  budget: z
    .string()
    .trim()
    .min(1, 'Informe quanto cabe no seu bolso por mês.')
    .refine((val) => {
      const parsed = Number(val.replace(',', '.'));
      return Number.isFinite(parsed) && parsed > 0;
    }, 'Digite um valor mensal maior que zero.'),
  hasPets: z.boolean(),
  isSmoker: z.boolean(),
  organizationLevel: z
    .string()
    .refine(
      (v) => ['1', '2', '3', '4', '5'].includes(v),
      'Escolha seu nível de organização de 1 a 5.',
    ),
  visitorTolerance: z
    .string()
    .refine(
      (v) => ['1', '2', '3', '4', '5'].includes(v),
      'Escolha sua tolerância a visitas de 1 a 5.',
    ),
  hasFurniture: z.boolean(),
  profession: z.string(),
  bio: z.string(),
  allergies: z.string(),
});

type FichaFormValues = z.infer<typeof fichaSchema>;

function toFormValues(profile: UserProfile | null): FichaFormValues {
  return {
    budget: profile?.budget != null ? String(profile.budget) : '',
    hasPets: profile?.hasPets ?? false,
    isSmoker: profile?.isSmoker ?? false,
    organizationLevel:
      profile?.organizationLevel != null
        ? String(profile.organizationLevel)
        : '',
    visitorTolerance:
      profile?.visitorTolerance != null ? String(profile.visitorTolerance) : '',
    hasFurniture: profile?.hasFurniture ?? false,
    profession: profile?.profession ?? '',
    bio: profile?.bio ?? '',
    allergies: profile?.allergies ?? '',
  };
}

function optionalText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export interface FichaFormProps {
  userName: string;
  residenceMonths: number | null;
  pointsTotal: number;
  initialProfile: UserProfile | null;
  submitLabel: string;
  submitting?: boolean;
  serverError?: string | null;
  onSubmit: (payload: UpdateProfilePayload) => Promise<void> | void;
}

export function FichaForm({
  userName,
  residenceMonths,
  pointsTotal,
  initialProfile,
  submitLabel,
  submitting = false,
  serverError = null,
  onSubmit,
}: FichaFormProps) {
  const idPrefix = useId();
  const form = useForm<FichaFormValues>({
    resolver: zodResolver(fichaSchema),
    defaultValues: toFormValues(initialProfile),
  });

  useEffect(() => {
    form.reset(toFormValues(initialProfile));
  }, [initialProfile, form]);

  const watched = form.watch();
  const parsedBudget = Number(watched.budget.trim().replace(',', '.'));
  const hasValidBudget =
    watched.budget.trim().length > 0 &&
    Number.isFinite(parsedBudget) &&
    parsedBudget > 0;
  const hasOrg = ['1', '2', '3', '4', '5'].includes(watched.organizationLevel);
  const hasVisitors = ['1', '2', '3', '4', '5'].includes(
    watched.visitorTolerance,
  );

  // hasPets e isSmoker iniciam definidos (Não = false), como no protótipo E-onb-ficha.
  const answeredCount = [
    hasValidBudget,
    true,
    true,
    hasOrg,
    hasVisitors,
  ].filter(Boolean).length;
  const missingCount = 5 - answeredCount;

  const previewChips: string[] = [];
  if (hasValidBudget) {
    previewChips.push(`Até R$ ${parsedBudget.toLocaleString('pt-BR')}`);
  }
  previewChips.push(watched.hasPets ? 'Tem pet' : 'Sem pet');
  previewChips.push(watched.isSmoker ? 'Fuma' : 'Não fuma');
  if (hasOrg) {
    previewChips.push(`Organização ${watched.organizationLevel}/5`);
  }
  if (hasVisitors) {
    previewChips.push(`Visitas ${watched.visitorTolerance}/5`);
  }
  if (watched.hasFurniture) {
    previewChips.push('Tem móveis');
  }
  if (watched.profession.trim()) {
    previewChips.push(watched.profession.trim());
  }

  const handleValidSubmit = form.handleSubmit(async (values) => {
    const budgetNumber = Number(values.budget.trim().replace(',', '.'));
    await onSubmit({
      budget: budgetNumber,
      hasPets: values.hasPets,
      isSmoker: values.isSmoker,
      organizationLevel: Number(values.organizationLevel),
      visitorTolerance: Number(values.visitorTolerance),
      hasFurniture: values.hasFurniture,
      profession: optionalText(values.profession),
      bio: optionalText(values.bio),
      allergies: optionalText(values.allergies),
    });
  });

  const isBusy = submitting || form.formState.isSubmitting;

  return (
    <form
      noValidate
      onSubmit={(e) => void handleValidSubmit(e)}
      className="grid gap-8 md:grid-cols-[minmax(0,1fr)_320px] md:items-start"
    >
      <div className="flex flex-col gap-6">
        <Alert
          tone={missingCount === 0 ? 'feito' : 'vence-logo'}
          title={
            missingCount === 0
              ? 'Tudo certo para calcular sua compatibilidade.'
              : `Faltam ${missingCount} de 5 respostas que pesam no match.`
          }
        />

        {serverError && <Alert tone="atrasado" title={serverError} />}

        <section aria-labelledby={`${idPrefix}-match-heading`} className="space-y-5">
          <h2
            id={`${idPrefix}-match-heading`}
            className="text-xl font-extrabold tracking-tight text-tinta"
          >
            O que pesa no match
          </h2>

          <div className="flex flex-col gap-2">
            <div className="flex items-baseline justify-between gap-2">
              <label
                htmlFor={`${idPrefix}-budget`}
                className="text-[15px] font-bold text-tinta"
              >
                Aluguel que cabe no bolso
              </label>
              <span className="text-xs font-medium text-legenda">
                por mês (R$)
              </span>
            </div>

            <input
              id={`${idPrefix}-budget`}
              type="number"
              inputMode="numeric"
              min={1}
              step={50}
              placeholder="Ex.: 900"
              aria-invalid={Boolean(form.formState.errors.budget)}
              aria-describedby={
                form.formState.errors.budget
                  ? `${idPrefix}-budget-err`
                  : undefined
              }
              className={[
                'min-h-12 w-full rounded-2xl border bg-fundo px-4 text-base font-semibold text-tinta focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20',
                form.formState.errors.budget
                  ? 'border-atrasado-texto'
                  : 'border-linha',
              ].join(' ')}
              {...form.register('budget')}
            />

            <div className="flex flex-wrap gap-2">
              {BUDGET_PRESETS.map((preset) => {
                const selected = hasValidBudget && parsedBudget === preset;
                return (
                  <button
                    key={preset}
                    type="button"
                    onClick={() =>
                      form.setValue('budget', String(preset), {
                        shouldValidate: true,
                        shouldDirty: true,
                      })
                    }
                    className={[
                      'nt-press min-h-11 rounded-full px-3.5 text-sm font-bold cursor-pointer',
                      selected
                        ? 'bg-tinta text-fundo'
                        : 'bg-superficie text-tinta hover:bg-linha',
                    ].join(' ')}
                  >
                    R$ {preset.toLocaleString('pt-BR')}
                  </button>
                );
              })}
            </div>

            {form.formState.errors.budget && (
              <p
                id={`${idPrefix}-budget-err`}
                role="alert"
                className="text-[13px] font-semibold text-atrasado-texto"
              >
                {form.formState.errors.budget.message}
              </p>
            )}
          </div>

          <div className="space-y-3 rounded-[20px] bg-superficie p-4">
            <Controller
              control={form.control}
              name="hasPets"
              render={({ field }) => (
                <Switch
                  label="Tem pet?"
                  description="Conta se você já tem ou pretende levar animais."
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              )}
            />
            <div className="border-t border-linha pt-3">
              <Controller
                control={form.control}
                name="isSmoker"
                render={({ field }) => (
                  <Switch
                    label="Fuma?"
                    description="Ajuda a combinar com casas que permitem ou restringem."
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-[15px] font-bold text-tinta">
                Organização
              </span>
              <span className="text-xs font-medium text-legenda">
                1 bagunça · 5 tudo no lugar
              </span>
            </div>
            <Controller
              control={form.control}
              name="organizationLevel"
              render={({ field }) => (
                <Segmented
                  aria-label="Organização"
                  value={field.value}
                  onChange={field.onChange}
                  options={SCALE_OPTIONS}
                />
              )}
            />
            {form.formState.errors.organizationLevel && (
              <p
                role="alert"
                className="text-[13px] font-semibold text-atrasado-texto"
              >
                {form.formState.errors.organizationLevel.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-[15px] font-bold text-tinta">Visitas</span>
              <span className="text-xs font-medium text-legenda">
                1 refúgio · 5 sempre tem gente
              </span>
            </div>
            <Controller
              control={form.control}
              name="visitorTolerance"
              render={({ field }) => (
                <Segmented
                  aria-label="Visitas"
                  value={field.value}
                  onChange={field.onChange}
                  options={SCALE_OPTIONS}
                />
              )}
            />
            {form.formState.errors.visitorTolerance && (
              <p
                role="alert"
                className="text-[13px] font-semibold text-atrasado-texto"
              >
                {form.formState.errors.visitorTolerance.message}
              </p>
            )}
          </div>
        </section>

        <section
          aria-labelledby={`${idPrefix}-optional-heading`}
          className="space-y-4 border-t border-linha pt-6"
        >
          <div className="flex items-baseline gap-2">
            <h2
              id={`${idPrefix}-optional-heading`}
              className="text-xl font-extrabold tracking-tight text-tinta"
            >
              Para a casa te conhecer
            </h2>
            <span className="text-xs font-semibold text-legenda">opcional</span>
          </div>

          <div className="rounded-[20px] bg-superficie p-4">
            <Controller
              control={form.control}
              name="hasFurniture"
              render={({ field }) => (
                <Switch
                  label="Móveis próprios"
                  description="Informe se você leva cama, mesa ou outros móveis."
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              )}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor={`${idPrefix}-profession`}
              className="text-[15px] font-bold text-tinta"
            >
              Profissão
            </label>
            <input
              id={`${idPrefix}-profession`}
              type="text"
              placeholder="Ex.: estudante de Engenharia"
              className="min-h-12 w-full rounded-2xl border border-linha bg-fundo px-4 text-[15px] font-medium text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              {...form.register('profession')}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor={`${idPrefix}-bio`}
              className="text-[15px] font-bold text-tinta"
            >
              Sobre você
            </label>
            <textarea
              id={`${idPrefix}-bio`}
              rows={3}
              placeholder="Rotina, manias, o que você gosta numa casa"
              className="w-full resize-y rounded-2xl border border-linha bg-fundo px-4 py-3 text-[15px] font-medium text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              {...form.register('bio')}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor={`${idPrefix}-allergies`}
              className="text-[15px] font-bold text-tinta"
            >
              Alergias
            </label>
            <input
              id={`${idPrefix}-allergies`}
              type="text"
              placeholder="Ex.: pelo de gato, poeira"
              className="min-h-12 w-full rounded-2xl border border-linha bg-fundo px-4 text-[15px] font-medium text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              {...form.register('allergies')}
            />
          </div>

          <p className="text-[13px] leading-relaxed text-legenda">
            Bio e alergias ficam guardadas cifradas e só aparecem para as casas
            em que você pedir vaga.
          </p>
        </section>

        <Button type="submit" size="lg" loading={isBusy} className="w-full md:w-auto md:self-start">
          {submitLabel}
        </Button>
      </div>

      <aside
        aria-label="Prévia da ficha"
        className="flex flex-col gap-3 rounded-[24px] bg-superficie p-5 md:sticky md:top-24"
      >
        <span className="text-xs font-bold uppercase tracking-wide text-legenda">
          Como a casa te vê
        </span>
        <div className="flex items-center gap-3">
          <Avatar name={userName} size="md" />
          <div className="min-w-0">
            <p className="truncate text-base font-extrabold text-tinta">
              {userName}
            </p>
            <p className="text-xs font-semibold text-legenda">
              {formatResidenceMonths(residenceMonths)} · {pointsTotal} pts
            </p>
          </div>
        </div>

        <div className="flex flex-wrap gap-1.5">
          {previewChips.map((chip) => (
            <StatusChip key={chip} tone="neutro" className="bg-fundo text-tinta">
              {chip}
            </StatusChip>
          ))}
        </div>

        {watched.bio.trim() && (
          <p className="rounded-2xl bg-fundo px-3.5 py-2.5 text-sm leading-relaxed text-texto-suave">
            {watched.bio.trim()}
          </p>
        )}
      </aside>
    </form>
  );
}
