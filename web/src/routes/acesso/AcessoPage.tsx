import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, MailCheck } from 'lucide-react';
import { useEffect, useId, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import {
  confirmPasswordReset,
  registerUser,
  requestPasswordReset,
} from '../../api/profile';
import { ApiError, SessionSupersededError } from '../../auth/apiClient';
import { useSession } from '../../auth/SessionContext';
import { Alert, Button, Logo, Segmented, useToast } from '../../components';
import { getHomePathForUser, paths } from '../paths';

export interface AcessoPageProps {
  /** `/entrar` usa `entrar`; `/cadastro` usa `cadastro`. */
  mode?: 'entrar' | 'cadastro' | 'esqueci-senha';
}

type AccessView = 'entrar' | 'cadastro' | 'esqueci' | 'nova';

const emailField = z
  .string()
  .trim()
  .min(1, 'Digite um e-mail válido, como nome@exemplo.com.')
  .email('Digite um e-mail válido, como nome@exemplo.com.');

const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, 'Informe sua senha.'),
});

const registerSchema = z.object({
  name: z.string().trim().min(1, 'Informe seu nome para continuar.'),
  email: emailField,
  password: z
    .string()
    .min(8, 'A senha precisa ter pelo menos 8 caracteres.'),
});

const forgotSchema = z.object({
  email: emailField,
});

const resetConfirmSchema = z
  .object({
    token: z.string().trim().min(1, 'Informe o código de recuperação.'),
    newPassword: z
      .string()
      .min(8, 'A senha precisa ter pelo menos 8 caracteres.'),
    confirmPassword: z.string().min(1, 'Repita a nova senha.'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    path: ['confirmPassword'],
    message: 'As duas senhas não são iguais.',
  });

type LoginFormValues = z.infer<typeof loginSchema>;
type RegisterFormValues = z.infer<typeof registerSchema>;
type ForgotFormValues = z.infer<typeof forgotSchema>;
type ResetConfirmFormValues = z.infer<typeof resetConfirmSchema>;

function initialViewForMode(mode: AcessoPageProps['mode']): AccessView {
  if (mode === 'cadastro') return 'cadastro';
  if (mode === 'esqueci-senha') return 'esqueci';
  return 'entrar';
}

const INPUT_BASE =
  'min-h-[50px] w-full rounded-2xl border bg-fundo px-4 text-base font-medium text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20';

function inputClass(hasError: boolean): string {
  return `${INPUT_BASE} ${hasError ? 'border-atrasado-texto' : 'border-linha'}`;
}

export function AcessoPage({ mode = 'entrar' }: AcessoPageProps) {
  const { login } = useSession();
  const toast = useToast();
  const navigate = useNavigate();

  const [view, setView] = useState<AccessView>(() => initialViewForMode(mode));
  const [serverError, setServerError] = useState<string | null>(null);
  const [loginNotice, setLoginNotice] = useState<string | null>(null);
  const [demoToken, setDemoToken] = useState<string | null>(null);

  useEffect(() => {
    setView(initialViewForMode(mode));
    setServerError(null);
  }, [mode]);

  const loginForm = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const registerForm = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: '', email: '', password: '' },
  });

  const forgotForm = useForm<ForgotFormValues>({
    resolver: zodResolver(forgotSchema),
    defaultValues: { email: '' },
  });

  const resetConfirmForm = useForm<ResetConfirmFormValues>({
    resolver: zodResolver(resetConfirmSchema),
    defaultValues: { token: '', newPassword: '', confirmPassword: '' },
  });

  const fieldIdPrefix = useId();

  const switchAuthTab = (next: 'entrar' | 'cadastro') => {
    setServerError(null);
    setLoginNotice(null);
    setView(next);
    navigate(next === 'cadastro' ? paths.cadastro : paths.entrar);
  };

  const openForgot = () => {
    setServerError(null);
    setLoginNotice(null);
    setDemoToken(null);
    forgotForm.reset({ email: loginForm.getValues('email') || '' });
    setView('esqueci');
  };

  const backToLogin = () => {
    setServerError(null);
    setDemoToken(null);
    setView('entrar');
    navigate(paths.entrar);
  };

  const handleLogin = loginForm.handleSubmit(async (values) => {
    setServerError(null);
    setLoginNotice(null);
    try {
      const me = await login(values.email.trim(), values.password);
      navigate(getHomePathForUser(me), { replace: true });
    } catch (error) {
      if (error instanceof SessionSupersededError) return;
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível entrar agora. Tente novamente em instantes.',
      );
    }
  });

  const handleRegister = registerForm.handleSubmit(async (values) => {
    setServerError(null);
    try {
      await registerUser({
        name: values.name.trim(),
        email: values.email.trim(),
        password: values.password,
      });
      const me = await login(values.email.trim(), values.password);
      navigate(getHomePathForUser(me), { replace: true });
    } catch (error) {
      if (error instanceof SessionSupersededError) return;
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível criar sua conta agora. Tente novamente em instantes.',
      );
    }
  });

  const handleForgotSubmit = forgotForm.handleSubmit(async (values) => {
    setServerError(null);
    try {
      const res = await requestPasswordReset({ email: values.email.trim() });
      const token = res.demoToken ?? null;
      setDemoToken(token);
      resetConfirmForm.reset({
        token: token ?? '',
        newPassword: '',
        confirmPassword: '',
      });
      setView('nova');
    } catch (error) {
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível solicitar a recuperação agora. Tente novamente.',
      );
    }
  });

  const handleResetConfirm = resetConfirmForm.handleSubmit(async (values) => {
    setServerError(null);
    try {
      await confirmPasswordReset({
        token: values.token.trim(),
        newPassword: values.newPassword,
      });
      const message = 'Senha trocada. Entre com a senha nova.';
      toast.show({ message, tone: 'feito' });
      setDemoToken(null);
      setLoginNotice(message);
      loginForm.setValue('password', '');
      setView('entrar');
      navigate(paths.entrar, { replace: true });
    } catch (error) {
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível salvar a nova senha agora. Tente novamente.',
      );
    }
  });

  const isAuthView = view === 'entrar' || view === 'cadastro';

  return (
    <main className="min-h-screen bg-fundo px-6 py-10 text-tinta">
      <div className="mx-auto flex w-full max-w-md flex-col gap-6">
        <div className="flex items-center justify-between">
          <Link to={paths.landing} className="nt-press rounded-xl">
            <Logo size="md" />
          </Link>
        </div>

        {isAuthView ? (
          <Segmented
            aria-label="Modo de acesso"
            value={view}
            onChange={switchAuthTab}
            options={[
              { value: 'entrar', label: 'Entrar' },
              { value: 'cadastro', label: 'Criar conta' },
            ]}
          />
        ) : (
          <button
            type="button"
            onClick={backToLogin}
            className="nt-press inline-flex min-h-11 items-center gap-1.5 self-start rounded-full bg-superficie px-4 text-sm font-semibold text-tinta hover:bg-linha cursor-pointer"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            Voltar para entrar
          </button>
        )}

        {view === 'entrar' && (
          <section className="flex flex-col gap-5">
            <header className="space-y-1.5">
              <h1 className="text-[29px] font-extrabold leading-tight tracking-tight text-tinta">
                Entrar
              </h1>
              <p className="text-[15px] text-texto-suave">
                Que bom te ver de novo.
              </p>
            </header>

            {loginNotice && <Alert tone="feito" title={loginNotice} />}
            {serverError && <Alert tone="atrasado" title={serverError} />}

            <form
              noValidate
              onSubmit={(e) => void handleLogin(e)}
              className="flex flex-col gap-4"
            >
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-login-email`}
                  className="text-sm font-semibold text-tinta"
                >
                  E-mail
                </label>
                <input
                  id={`${fieldIdPrefix}-login-email`}
                  type="email"
                  autoComplete="email"
                  placeholder="ana@exemplo.com"
                  aria-invalid={Boolean(loginForm.formState.errors.email)}
                  aria-describedby={
                    loginForm.formState.errors.email
                      ? `${fieldIdPrefix}-login-email-err`
                      : undefined
                  }
                  className={inputClass(
                    Boolean(loginForm.formState.errors.email),
                  )}
                  {...loginForm.register('email')}
                />
                {loginForm.formState.errors.email && (
                  <p
                    id={`${fieldIdPrefix}-login-email-err`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {loginForm.formState.errors.email.message}
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-login-password`}
                  className="text-sm font-semibold text-tinta"
                >
                  Senha
                </label>
                <input
                  id={`${fieldIdPrefix}-login-password`}
                  type="password"
                  autoComplete="current-password"
                  aria-invalid={Boolean(loginForm.formState.errors.password)}
                  aria-describedby={
                    loginForm.formState.errors.password
                      ? `${fieldIdPrefix}-login-password-err`
                      : undefined
                  }
                  className={inputClass(
                    Boolean(loginForm.formState.errors.password),
                  )}
                  {...loginForm.register('password')}
                />
                {loginForm.formState.errors.password && (
                  <p
                    id={`${fieldIdPrefix}-login-password-err`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {loginForm.formState.errors.password.message}
                  </p>
                )}
              </div>

              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={openForgot}
                  className="min-h-11 px-1 text-sm font-bold text-tomate-escuro underline cursor-pointer"
                >
                  Esqueci minha senha
                </button>
              </div>

              <Button
                type="submit"
                size="lg"
                loading={loginForm.formState.isSubmitting}
                className="w-full"
              >
                Entrar
              </Button>
            </form>

            <p className="text-center text-sm text-texto-suave">
              Ainda não tem conta?{' '}
              <button
                type="button"
                onClick={() => switchAuthTab('cadastro')}
                className="min-h-11 font-bold text-tomate-escuro underline cursor-pointer"
              >
                Criar conta
              </button>
            </p>
          </section>
        )}

        {view === 'cadastro' && (
          <section className="flex flex-col gap-5">
            <header className="space-y-1.5">
              <h1 className="text-[29px] font-extrabold leading-tight tracking-tight text-tinta">
                Criar conta
              </h1>
              <p className="text-[15px] text-texto-suave">
                Bora achar sua casa.
              </p>
            </header>

            {serverError && <Alert tone="atrasado" title={serverError} />}

            <form
              noValidate
              onSubmit={(e) => void handleRegister(e)}
              className="flex flex-col gap-4"
            >
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-reg-name`}
                  className="text-sm font-semibold text-tinta"
                >
                  Nome
                </label>
                <input
                  id={`${fieldIdPrefix}-reg-name`}
                  type="text"
                  autoComplete="name"
                  placeholder="Ana Souza"
                  aria-invalid={Boolean(registerForm.formState.errors.name)}
                  aria-describedby={`${fieldIdPrefix}-reg-name-hint`}
                  className={inputClass(
                    Boolean(registerForm.formState.errors.name),
                  )}
                  {...registerForm.register('name')}
                />
                {registerForm.formState.errors.name ? (
                  <p
                    id={`${fieldIdPrefix}-reg-name-hint`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {registerForm.formState.errors.name.message}
                  </p>
                ) : (
                  <p
                    id={`${fieldIdPrefix}-reg-name-hint`}
                    className="text-[13px] text-legenda"
                  >
                    É como a casa vai te chamar.
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-reg-email`}
                  className="text-sm font-semibold text-tinta"
                >
                  E-mail
                </label>
                <input
                  id={`${fieldIdPrefix}-reg-email`}
                  type="email"
                  autoComplete="email"
                  placeholder="ana@exemplo.com"
                  aria-invalid={Boolean(registerForm.formState.errors.email)}
                  aria-describedby={
                    registerForm.formState.errors.email
                      ? `${fieldIdPrefix}-reg-email-err`
                      : undefined
                  }
                  className={inputClass(
                    Boolean(registerForm.formState.errors.email),
                  )}
                  {...registerForm.register('email')}
                />
                {registerForm.formState.errors.email && (
                  <p
                    id={`${fieldIdPrefix}-reg-email-err`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {registerForm.formState.errors.email.message}
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-reg-password`}
                  className="text-sm font-semibold text-tinta"
                >
                  Senha
                </label>
                <input
                  id={`${fieldIdPrefix}-reg-password`}
                  type="password"
                  autoComplete="new-password"
                  aria-invalid={Boolean(registerForm.formState.errors.password)}
                  aria-describedby={`${fieldIdPrefix}-reg-password-hint`}
                  className={inputClass(
                    Boolean(registerForm.formState.errors.password),
                  )}
                  {...registerForm.register('password')}
                />
                {registerForm.formState.errors.password ? (
                  <p
                    id={`${fieldIdPrefix}-reg-password-hint`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {registerForm.formState.errors.password.message}
                  </p>
                ) : (
                  <p
                    id={`${fieldIdPrefix}-reg-password-hint`}
                    className="text-[13px] text-legenda"
                  >
                    Pelo menos 8 caracteres.
                  </p>
                )}
              </div>

              <Button
                type="submit"
                size="lg"
                loading={registerForm.formState.isSubmitting}
                className="mt-1 w-full"
              >
                Criar conta
              </Button>
            </form>

            <p className="text-center text-sm text-texto-suave">
              Já tem conta?{' '}
              <button
                type="button"
                onClick={() => switchAuthTab('entrar')}
                className="min-h-11 font-bold text-tomate-escuro underline cursor-pointer"
              >
                Entrar
              </button>
            </p>
          </section>
        )}

        {view === 'esqueci' && (
          <section className="flex flex-col gap-5">
            <header className="space-y-1.5">
              <h1 className="text-[29px] font-extrabold leading-tight tracking-tight text-tinta">
                Vamos trocar sua senha
              </h1>
              <p className="text-[15px] text-texto-suave">
                Informe o e-mail da sua conta. Se ele estiver cadastrado,
                enviamos as instruções para criar uma senha nova.
              </p>
            </header>

            {serverError && <Alert tone="atrasado" title={serverError} />}

            <form
              noValidate
              onSubmit={(e) => void handleForgotSubmit(e)}
              className="flex flex-col gap-4"
            >
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-forgot-email`}
                  className="text-sm font-semibold text-tinta"
                >
                  E-mail
                </label>
                <input
                  id={`${fieldIdPrefix}-forgot-email`}
                  type="email"
                  autoComplete="email"
                  placeholder="ana@exemplo.com"
                  aria-invalid={Boolean(forgotForm.formState.errors.email)}
                  aria-describedby={
                    forgotForm.formState.errors.email
                      ? `${fieldIdPrefix}-forgot-email-err`
                      : undefined
                  }
                  className={inputClass(
                    Boolean(forgotForm.formState.errors.email),
                  )}
                  {...forgotForm.register('email')}
                />
                {forgotForm.formState.errors.email && (
                  <p
                    id={`${fieldIdPrefix}-forgot-email-err`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {forgotForm.formState.errors.email.message}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                size="lg"
                loading={forgotForm.formState.isSubmitting}
                className="w-full"
              >
                Verificar e-mail
              </Button>
            </form>
          </section>
        )}

        {view === 'nova' && (
          <section className="flex flex-col gap-5">
            <span
              aria-hidden="true"
              className="flex size-14 items-center justify-center rounded-[18px] bg-vence-logo-fundo text-vence-logo-texto"
            >
              <MailCheck className="size-7" />
            </span>

            <header className="space-y-1.5">
              <h1 className="text-[29px] font-extrabold leading-tight tracking-tight text-tinta">
                Crie uma senha nova
              </h1>
            </header>

            <Alert
              tone="neutro"
              title="Se o e-mail estiver cadastrado, enviamos as instruções para redefinir sua senha."
              description={
                demoToken
                  ? 'Modo demonstração: o código foi preenchido abaixo e vale por 15 minutos.'
                  : 'Se você já tiver um código de recuperação, informe-o abaixo junto da nova senha.'
              }
            />

            {serverError && (
              <Alert
                tone="atrasado"
                title={serverError}
                action={{
                  label: 'Pedir novo código',
                  onClick: () => {
                    setServerError(null);
                    setView('esqueci');
                  },
                }}
              />
            )}

            <form
              noValidate
              onSubmit={(e) => void handleResetConfirm(e)}
              className="flex flex-col gap-4"
            >
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-reset-token`}
                  className="text-sm font-semibold text-tinta"
                >
                  Código de recuperação
                </label>
                <input
                  id={`${fieldIdPrefix}-reset-token`}
                  type="text"
                  autoComplete="one-time-code"
                  aria-invalid={Boolean(resetConfirmForm.formState.errors.token)}
                  aria-describedby={
                    resetConfirmForm.formState.errors.token
                      ? `${fieldIdPrefix}-reset-token-err`
                      : undefined
                  }
                  className={inputClass(
                    Boolean(resetConfirmForm.formState.errors.token),
                  )}
                  {...resetConfirmForm.register('token')}
                />
                {resetConfirmForm.formState.errors.token && (
                  <p
                    id={`${fieldIdPrefix}-reset-token-err`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {resetConfirmForm.formState.errors.token.message}
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-reset-new`}
                  className="text-sm font-semibold text-tinta"
                >
                  Nova senha
                </label>
                <input
                  id={`${fieldIdPrefix}-reset-new`}
                  type="password"
                  autoComplete="new-password"
                  aria-invalid={Boolean(
                    resetConfirmForm.formState.errors.newPassword,
                  )}
                  aria-describedby={`${fieldIdPrefix}-reset-new-hint`}
                  className={inputClass(
                    Boolean(resetConfirmForm.formState.errors.newPassword),
                  )}
                  {...resetConfirmForm.register('newPassword')}
                />
                {resetConfirmForm.formState.errors.newPassword ? (
                  <p
                    id={`${fieldIdPrefix}-reset-new-hint`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {resetConfirmForm.formState.errors.newPassword.message}
                  </p>
                ) : (
                  <p
                    id={`${fieldIdPrefix}-reset-new-hint`}
                    className="text-[13px] text-legenda"
                  >
                    Pelo menos 8 caracteres.
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor={`${fieldIdPrefix}-reset-confirm`}
                  className="text-sm font-semibold text-tinta"
                >
                  Repita a nova senha
                </label>
                <input
                  id={`${fieldIdPrefix}-reset-confirm`}
                  type="password"
                  autoComplete="new-password"
                  aria-invalid={Boolean(
                    resetConfirmForm.formState.errors.confirmPassword,
                  )}
                  aria-describedby={
                    resetConfirmForm.formState.errors.confirmPassword
                      ? `${fieldIdPrefix}-reset-confirm-err`
                      : undefined
                  }
                  className={inputClass(
                    Boolean(resetConfirmForm.formState.errors.confirmPassword),
                  )}
                  {...resetConfirmForm.register('confirmPassword')}
                />
                {resetConfirmForm.formState.errors.confirmPassword && (
                  <p
                    id={`${fieldIdPrefix}-reset-confirm-err`}
                    role="alert"
                    className="text-[13px] font-semibold text-atrasado-texto"
                  >
                    {resetConfirmForm.formState.errors.confirmPassword.message}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                size="lg"
                loading={resetConfirmForm.formState.isSubmitting}
                className="w-full"
              >
                Salvar nova senha
              </Button>
            </form>
          </section>
        )}

        <p className="text-center text-xs leading-relaxed text-legenda">
          {view === 'esqueci' || view === 'nova'
            ? 'Por segurança, nunca revelamos se um e-mail possui cadastro.'
            : 'Seu perfil só aparece para as casas em que você pedir vaga.'}
        </p>
      </div>
    </main>
  );
}
