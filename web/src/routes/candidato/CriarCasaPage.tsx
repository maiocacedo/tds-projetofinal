import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowLeft,
  Cigarette,
  Eye,
  MapPin,
  PawPrint,
  Sofa,
  Sparkles,
  Users,
  Vote,
} from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useCreateHouseMutation } from '../../api/candidate';
import { votingRuleSchema } from '../../api/houses';
import { useSession } from '../../auth/SessionContext';
import {
  Alert,
  Button,
  Counter,
  Segmented,
  StatusChip,
  Switch,
  useToast,
} from '../../components';
import { paths } from '../paths';

const formSchema = z.object({
  name: z
    .string()
    .trim()
    .min(2, 'Informe o nome da casa (pelo menos 2 caracteres).'),
  address: z.string().trim().optional(),
  description: z.string().trim().optional(),
  basicRules: z.string().trim().optional(),
  rentAmountText: z
    .string()
    .trim()
    .refine(
      (raw) => {
        if (!raw) return true;
        const parsed = Number(raw.replace(',', '.'));
        return Number.isFinite(parsed) && parsed >= 0;
      },
      { message: 'Informe um valor de aluguel válido (0 ou maior).' },
    ),
  vacancies: z.number().int().min(0, 'As vagas não podem ser negativas.').max(20),
  allowsPets: z.boolean(),
  allowsSmokers: z.boolean(),
  hasFurniture: z.boolean(),
  expectedOrganizationLevel: z.number().int().min(1).max(5),
  visitorTolerance: z.number().int().min(1).max(5),
  votingRule: votingRuleSchema,
  isAcceptingMatches: z.boolean(),
});

type CreateHouseFormValues = z.infer<typeof formSchema>;

const SCALE_OPTIONS = [
  { value: '1', label: '1' },
  { value: '2', label: '2' },
  { value: '3', label: '3' },
  { value: '4', label: '4' },
  { value: '5', label: '5' },
];

const VOTING_OPTIONS: { value: 'MAJORITY' | 'UNANIMITY'; label: string }[] = [
  { value: 'MAJORITY', label: 'Maioria' },
  { value: 'UNANIMITY', label: 'Unanimidade' },
];

function parseRent(raw: string): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed.replace(',', '.'));
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

function formatPreviewRent(raw: string): string {
  const value = parseRent(raw);
  if (value === null) return 'Aluguel a combinar';
  return `${value.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: value % 1 === 0 ? 0 : 2,
  })}/mês`;
}

export function CriarCasaPage() {
  const { user } = useSession();
  const navigate = useNavigate();
  const toast = useToast();
  const createHouseMutation = useCreateHouseMutation();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const alreadyInHouse = Boolean(user?.houseId);

  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<CreateHouseFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: '',
      address: '',
      description: '',
      basicRules: '',
      rentAmountText: '',
      vacancies: 1,
      allowsPets: true,
      allowsSmokers: false,
      hasFurniture: false,
      expectedOrganizationLevel: 3,
      visitorTolerance: 3,
      votingRule: 'MAJORITY',
      isAcceptingMatches: true,
    },
  });

  const preview = watch();

  const onSubmit = async (values: CreateHouseFormValues) => {
    setSubmitError(null);
    try {
      const created = await createHouseMutation.mutateAsync({
        name: values.name,
        address: values.address || undefined,
        description: values.description || undefined,
        basicRules: values.basicRules || undefined,
        rentAmount: parseRent(values.rentAmountText),
        vacancies: values.vacancies,
        allowsPets: values.allowsPets,
        allowsSmokers: values.allowsSmokers,
        hasFurniture: values.hasFurniture,
        expectedOrganizationLevel: values.expectedOrganizationLevel,
        visitorTolerance: values.visitorTolerance,
        votingRule: values.votingRule,
        isAcceptingMatches: values.isAcceptingMatches,
      });
      toast.show({
        message: `${created.name} criada! Bem-vindo ao seu novo teto.`,
        tone: 'feito',
      });
      navigate(paths.inicio, { replace: true });
    } catch (err) {
      setSubmitError(
        err instanceof Error
          ? err.message
          : 'Não foi possível criar a casa agora. Tente novamente.',
      );
    }
  };

  const previewOpen = preview.isAcceptingMatches && preview.vacancies > 0;

  return (
    <section className="space-y-6">
      <Link
        to={paths.explorar}
        className="inline-flex min-h-11 items-center gap-1.5 text-sm font-bold text-texto-suave hover:text-tinta"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Voltar para Explorar
      </Link>

      <div className="space-y-1">
        <h1 className="text-[28px] font-extrabold tracking-tight text-tinta">
          Criar casa
        </h1>
        <p className="text-[15px] text-texto-suave">
          Cadastre sua república e veja ao vivo como os candidatos vão encontrá-la.
        </p>
      </div>

      {alreadyInHouse && (
        <Alert
          tone="vence-logo"
          title="Você já mora em uma casa"
          description="Saia da sua casa atual antes de criar uma nova república."
          action={{ label: 'Ir para minha casa', onClick: () => navigate(paths.casa) }}
        />
      )}

      {submitError && (
        <Alert
          tone="atrasado"
          title="Não foi possível criar a casa"
          description={submitError}
        />
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
        <form
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
          noValidate
          className="space-y-6 rounded-[28px] border border-linha bg-fundo p-6"
        >
          <div className="space-y-4">
            <h2 className="text-lg font-extrabold text-tinta">Dados da república</h2>

            <div className="space-y-1.5">
              <label htmlFor="casa-nome" className="text-sm font-semibold text-tinta">
                Nome da casa
              </label>
              <input
                id="casa-nome"
                type="text"
                placeholder="Ex.: República Girassol"
                aria-invalid={Boolean(errors.name)}
                {...register('name')}
                className="min-h-11 w-full rounded-2xl border border-linha bg-fundo px-4 text-[15px] text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              />
              {errors.name && (
                <p role="alert" className="text-xs font-bold text-atrasado-texto">
                  {errors.name.message}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <label htmlFor="casa-endereco" className="text-sm font-semibold text-tinta">
                Endereço ou bairro
              </label>
              <input
                id="casa-endereco"
                type="text"
                placeholder="Ex.: Rua das Flores, 120 — Centro"
                {...register('address')}
                className="min-h-11 w-full rounded-2xl border border-linha bg-fundo px-4 text-[15px] text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label htmlFor="casa-aluguel" className="text-sm font-semibold text-tinta">
                  Aluguel por pessoa (R$)
                </label>
                <input
                  id="casa-aluguel"
                  type="text"
                  inputMode="decimal"
                  placeholder="Ex.: 950"
                  aria-invalid={Boolean(errors.rentAmountText)}
                  {...register('rentAmountText')}
                  className="min-h-11 w-full rounded-2xl border border-linha bg-fundo px-4 text-[15px] text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
                />
                {errors.rentAmountText && (
                  <p role="alert" className="text-xs font-bold text-atrasado-texto">
                    {errors.rentAmountText.message}
                  </p>
                )}
              </div>

              <Controller
                name="vacancies"
                control={control}
                render={({ field }) => (
                  <Counter
                    label="Vagas disponíveis"
                    value={field.value}
                    onChange={field.onChange}
                    min={0}
                    max={20}
                    suffix={field.value === 1 ? 'vaga' : 'vagas'}
                  />
                )}
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="casa-descricao" className="text-sm font-semibold text-tinta">
                Sobre a casa
              </label>
              <textarea
                id="casa-descricao"
                rows={3}
                placeholder="Conte como é o espaço, a rotina e o clima da república."
                {...register('description')}
                className="w-full rounded-2xl border border-linha bg-fundo p-4 text-[15px] text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="casa-regras" className="text-sm font-semibold text-tinta">
                Regras básicas
              </label>
              <textarea
                id="casa-regras"
                rows={2}
                placeholder="Ex.: Silêncio depois das 22h, louça lavada no mesmo dia."
                {...register('basicRules')}
                className="w-full rounded-2xl border border-linha bg-fundo p-4 text-[15px] text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              />
            </div>
          </div>

          <div className="space-y-4 border-t border-linha pt-5">
            <h2 className="text-lg font-extrabold text-tinta">Convivência e critérios</h2>

            <div className="space-y-2 rounded-2xl bg-superficie/65 p-4">
              <Controller
                name="allowsPets"
                control={control}
                render={({ field }) => (
                  <Switch
                    label="Aceita pets"
                    description="Animais de estimação são bem-vindos na casa."
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
              <Controller
                name="allowsSmokers"
                control={control}
                render={({ field }) => (
                  <Switch
                    label="Permite fumar"
                    description="Moradores ou visitas podem fumar na casa."
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
              <Controller
                name="hasFurniture"
                control={control}
                render={({ field }) => (
                  <Switch
                    label="Casa mobiliada"
                    description="Áreas comuns ou quartos já contam com mobília."
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            <div className="space-y-1.5">
              <span className="block text-sm font-semibold text-tinta">
                Organização esperada
              </span>
              <p className="text-xs text-legenda">1 relaxada · 5 impecável</p>
              <Controller
                name="expectedOrganizationLevel"
                control={control}
                render={({ field }) => (
                  <Segmented
                    aria-label="Organização esperada"
                    options={SCALE_OPTIONS}
                    value={String(field.value)}
                    onChange={(next) => field.onChange(Number(next))}
                  />
                )}
              />
            </div>

            <div className="space-y-1.5">
              <span className="block text-sm font-semibold text-tinta">
                Tolerância a visitas
              </span>
              <p className="text-xs text-legenda">1 raras · 5 sempre bem-vindas</p>
              <Controller
                name="visitorTolerance"
                control={control}
                render={({ field }) => (
                  <Segmented
                    aria-label="Tolerância a visitas"
                    options={SCALE_OPTIONS}
                    value={String(field.value)}
                    onChange={(next) => field.onChange(Number(next))}
                  />
                )}
              />
            </div>
          </div>

          <div className="space-y-4 border-t border-linha pt-5">
            <h2 className="text-lg font-extrabold text-tinta">Votação e pedidos</h2>

            <div className="space-y-1.5">
              <span className="block text-sm font-semibold text-tinta">
                Regra de votação
              </span>
              <Controller
                name="votingRule"
                control={control}
                render={({ field }) => (
                  <Segmented
                    aria-label="Regra de votação"
                    options={VOTING_OPTIONS}
                    value={field.value}
                    onChange={field.onChange}
                  />
                )}
              />
            </div>

            <div className="rounded-2xl bg-superficie/65 p-4">
              <Controller
                name="isAcceptingMatches"
                control={control}
                render={({ field }) => (
                  <Switch
                    label="Receber pedidos de vaga"
                    description="Desligue para pausar novos pedidos sem zerar as vagas."
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>
          </div>

          <div className="pt-2">
            <Button
              type="submit"
              variant="primary"
              size="lg"
              disabled={alreadyInHouse}
              loading={createHouseMutation.isPending}
              className="w-full sm:w-auto"
            >
              Criar casa
            </Button>
          </div>
        </form>

        <aside
          aria-label="Prévia de como os candidatos veem a casa"
          className="space-y-3 rounded-[28px] border border-linha bg-superficie/70 p-5 lg:sticky lg:top-24"
        >
          <div className="flex items-center gap-2 text-xs font-extrabold uppercase tracking-wider text-legenda">
            <Eye aria-hidden="true" className="size-4 text-tomate" />
            Prévia ao vivo
          </div>

          <article className="space-y-3 rounded-[24px] border border-linha bg-fundo p-5">
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip tone={previewOpen ? 'feito' : 'neutro'}>
                {!preview.isAcceptingMatches
                  ? 'Pedidos pausados'
                  : preview.vacancies === 0
                    ? 'Sem vagas'
                    : preview.vacancies === 1
                      ? '1 vaga'
                      : `${preview.vacancies} vagas`}
              </StatusChip>
              <StatusChip tone="neutro">
                <Vote aria-hidden="true" className="size-3.5" />
                {preview.votingRule === 'UNANIMITY' ? 'Unanimidade' : 'Maioria'}
              </StatusChip>
            </div>

            <div className="space-y-1">
              <h2 className="text-xl font-extrabold leading-tight text-tinta">
                {preview.name.trim() || 'Nome da sua casa'}
              </h2>
              <p className="flex items-center gap-1.5 text-[13px] font-medium text-legenda">
                <MapPin aria-hidden="true" className="size-3.5 shrink-0" />
                {preview.address?.trim() || 'Endereço ou bairro'}
              </p>
            </div>

            <p className="text-lg font-extrabold text-tinta">
              {formatPreviewRent(preview.rentAmountText)}
            </p>

            {preview.description?.trim() && (
              <p className="text-sm text-texto-suave">{preview.description.trim()}</p>
            )}

            <ul className="flex flex-wrap gap-2 pt-1 text-xs font-semibold text-texto-suave">
              <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                <PawPrint aria-hidden="true" className="size-3.5" />
                {preview.allowsPets ? 'Aceita pets' : 'Sem pets'}
              </li>
              <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                <Cigarette aria-hidden="true" className="size-3.5" />
                {preview.allowsSmokers ? 'Permite fumar' : 'Não fumantes'}
              </li>
              <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                <Sofa aria-hidden="true" className="size-3.5" />
                {preview.hasFurniture ? 'Mobiliada' : 'Sem mobília'}
              </li>
              <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                <Sparkles aria-hidden="true" className="size-3.5" />
                Org. {preview.expectedOrganizationLevel}/5
              </li>
              <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                <Users aria-hidden="true" className="size-3.5" />
                Visitas {preview.visitorTolerance}/5
              </li>
            </ul>
          </article>

          <p className="text-xs text-legenda">
            {previewOpen
              ? 'Sua casa vai aparecer no Explorar assim que for criada.'
              : 'Com 0 vagas ou pedidos pausados, a casa não aparece no Explorar até você abrir vagas.'}
          </p>
        </aside>
      </div>
    </section>
  );
}
