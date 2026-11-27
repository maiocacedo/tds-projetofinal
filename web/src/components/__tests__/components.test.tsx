import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { useRef, useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { server } from '../../mocks/server';
import { useReducedMotion } from '../../motion/useReducedMotion';
import { useScrollReveal } from '../../motion/useScrollReveal';
import { AppRoutes } from '../../routes/router';
import { renderWithProviders } from '../../test/renderWithProviders';
import { setPrefersReducedMotion, triggerIntersection } from '../../test/motion';
import {
  Alert,
  Avatar,
  Button,
  Celebration,
  ChecklistItem,
  CompatibilityRing,
  Counter,
  Dialog,
  EmptyState,
  Logo,
  Panel,
  Segmented,
  SPLASH_REDUCED_MS,
  SPLASH_SEEN_KEY,
  SplashOverlay,
  StatusChip,
  Switch,
  ToastProvider,
  useToast,
} from '..';

describe('Logo', () => {
  it('renders the wordmark without any heading', () => {
    render(<Logo />);
    expect(screen.getByText('Nosso Teto')).toBeInTheDocument();
    expect(screen.queryByRole('heading')).toBeNull();
  });

  it('exposes an image name when the wordmark is hidden', () => {
    render(<Logo showWordmark={false} title="Nosso Teto" />);
    expect(screen.getByRole('img', { name: 'Nosso Teto' })).toBeInTheDocument();
  });
});

describe('Button', () => {
  it('is disabled and busy while loading', () => {
    render(<Button loading>Salvar</Button>);
    const button = screen.getByRole('button', { name: 'Salvar' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });

  it('renders the child element with asChild', () => {
    render(
      <Button asChild>
        <a href="/sobre">Sobre</a>
      </Button>,
    );
    expect(screen.getByRole('link', { name: 'Sobre' })).toHaveAttribute('href', '/sobre');
  });
});

describe('Segmented', () => {
  function Harness({ onChange }: { onChange?: (value: string) => void }) {
    const [value, setValue] = useState<'todas' | 'minhas' | 'feitas'>('todas');
    return (
      <Segmented
        aria-label="Filtro de tarefas"
        value={value}
        onChange={(next) => {
          setValue(next);
          onChange?.(next);
        }}
        options={[
          { value: 'todas', label: 'Todas' },
          { value: 'minhas', label: 'Minhas' },
          { value: 'feitas', label: 'Feitas' },
        ]}
      />
    );
  }

  it('is a radiogroup with aria-checked radios and keeps labels visible in jsdom', () => {
    render(<Harness />);
    const group = screen.getByRole('radiogroup', { name: 'Filtro de tarefas' });
    const radios = within(group).getAllByRole('radio');
    expect(radios).toHaveLength(3);
    expect(radios[0]).toHaveAttribute('aria-checked', 'true');
    expect(radios[1]).toHaveAttribute('aria-checked', 'false');
    // getBoundingClientRect = 0 no jsdom: o indicador não aparece, o texto sim.
    expect(screen.getByText('Todas')).toBeVisible();
  });

  it('moves selection with arrows, Home and End and selects on click', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await user.click(screen.getByRole('radio', { name: 'Minhas' }));
    expect(screen.getByRole('radio', { name: 'Minhas' })).toHaveAttribute('aria-checked', 'true');

    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('radio', { name: 'Feitas' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'Feitas' })).toHaveFocus();

    await user.keyboard('{Home}');
    expect(screen.getByRole('radio', { name: 'Todas' })).toHaveAttribute('aria-checked', 'true');

    await user.keyboard('{End}');
    expect(screen.getByRole('radio', { name: 'Feitas' })).toHaveAttribute('aria-checked', 'true');

    await user.keyboard('{ArrowLeft}');
    expect(screen.getByRole('radio', { name: 'Minhas' })).toHaveAttribute('aria-checked', 'true');
    expect(onChange).toHaveBeenLastCalledWith('minhas');
  });

  it('selects the focused option with Space and Enter', () => {
    render(<Harness />);
    const feitas = screen.getByRole('radio', { name: 'Feitas' });
    fireEvent.keyDown(feitas, { key: ' ' });
    expect(feitas).toHaveAttribute('aria-checked', 'true');
    const minhas = screen.getByRole('radio', { name: 'Minhas' });
    fireEvent.keyDown(minhas, { key: 'Enter' });
    expect(minhas).toHaveAttribute('aria-checked', 'true');
  });
});

describe('Switch', () => {
  it('is labelled by its label and toggles', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [on, setOn] = useState(false);
      return (
        <Switch
          checked={on}
          onCheckedChange={setOn}
          label="Aceitando pedidos"
          description="Candidatos podem pedir vaga"
        />
      );
    }
    render(<Harness />);
    const toggle = screen.getByRole('switch', { name: 'Aceitando pedidos' });
    expect(toggle).toHaveAttribute('aria-checked', 'false');
    expect(toggle).toHaveAccessibleDescription('Candidatos podem pedir vaga');
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-checked', 'true');
    await user.click(screen.getByText('Aceitando pedidos'));
    expect(toggle).toHaveAttribute('aria-checked', 'false');
  });
});

describe('Counter', () => {
  function Harness({ initial = 2 }: { initial?: number }) {
    const [value, setValue] = useState(initial);
    return (
      <>
        <Counter label="Vagas" value={value} onChange={setValue} min={1} max={5} />
        <output data-testid="valor">{value}</output>
      </>
    );
  }

  it('exposes a typable spinbutton with clear −/+ labels', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByRole('spinbutton', { name: 'Vagas' });
    expect(input).toHaveValue('2');
    await user.click(screen.getByRole('button', { name: 'Aumentar Vagas' }));
    expect(screen.getByTestId('valor')).toHaveTextContent('3');
    await user.click(screen.getByRole('button', { name: 'Diminuir Vagas' }));
    expect(screen.getByTestId('valor')).toHaveTextContent('2');
  });

  it('clamps typed values on blur and Enter', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByRole('spinbutton', { name: 'Vagas' });

    await user.clear(input);
    await user.type(input, '42');
    await user.tab();
    expect(input).toHaveValue('5');
    expect(screen.getByTestId('valor')).toHaveTextContent('5');

    await user.clear(input);
    await user.type(input, '0{Enter}');
    expect(input).toHaveValue('1');
    expect(screen.getByTestId('valor')).toHaveTextContent('1');
  });

  it('steps with ArrowUp/ArrowDown like a spinbutton, respecting the limits', async () => {
    const user = userEvent.setup();
    render(<Harness initial={4} />);
    const input = screen.getByRole('spinbutton', { name: 'Vagas' });
    await user.click(input);
    await user.keyboard('{ArrowUp}{ArrowUp}');
    expect(screen.getByTestId('valor')).toHaveTextContent('5');
    expect(input).toHaveValue('5');
    await user.keyboard('{ArrowDown}');
    expect(screen.getByTestId('valor')).toHaveTextContent('4');
  });

  it('disables − and + at the limits', () => {
    const { unmount } = render(<Harness initial={1} />);
    expect(screen.getByRole('button', { name: 'Diminuir Vagas' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Aumentar Vagas' })).toBeEnabled();
    unmount();
    render(<Harness initial={5} />);
    expect(screen.getByRole('button', { name: 'Aumentar Vagas' })).toBeDisabled();
  });
});

describe('ChecklistItem', () => {
  function Harness({ onSettled, settleDelayMs }: { onSettled: () => void; settleDelayMs?: number }) {
    const [checked, setChecked] = useState(false);
    return (
      <ChecklistItem
        checked={checked}
        onCheckedChange={setChecked}
        label="Café 500 g"
        meta="× 2"
        settleDelayMs={settleDelayMs}
        onSettled={onSettled}
      />
    );
  }

  it('completes in place and settles after the delay', () => {
    vi.useFakeTimers();
    const onSettled = vi.fn();
    render(<Harness onSettled={onSettled} settleDelayMs={900} />);
    const checkbox = screen.getByRole('checkbox', { name: /café 500 g/i });
    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
    expect(onSettled).not.toHaveBeenCalled();
    act(() => {
      vi.advanceTimersByTime(899);
    });
    expect(onSettled).not.toHaveBeenCalled();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onSettled).toHaveBeenCalledTimes(1);
  });

  it('cancels the settle timer when unchecked or unmounted', () => {
    vi.useFakeTimers();
    const onSettled = vi.fn();
    const { unmount } = render(<Harness onSettled={onSettled} settleDelayMs={900} />);
    const checkbox = screen.getByRole('checkbox', { name: /café 500 g/i });
    fireEvent.click(checkbox);
    fireEvent.click(checkbox);
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(onSettled).not.toHaveBeenCalled();

    fireEvent.click(checkbox);
    unmount();
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(onSettled).not.toHaveBeenCalled();
  });

  it('settles immediately with reduced motion', () => {
    setPrefersReducedMotion(true);
    const onSettled = vi.fn();
    render(<Harness onSettled={onSettled} settleDelayMs={900} />);
    fireEvent.click(screen.getByRole('checkbox', { name: /café 500 g/i }));
    expect(onSettled).toHaveBeenCalledTimes(1);
  });
});

describe('StatusChip and Alert', () => {
  it('renders the chip content', () => {
    render(<StatusChip tone="feito">Pago</StatusChip>);
    expect(screen.getByText('Pago')).toBeInTheDocument();
  });

  it('uses role="alert" only for atrasado and runs the action', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    const { unmount } = render(
      <Alert
        tone="atrasado"
        title="Luz atrasada"
        description="R$ 62,40 para o Bruno"
        action={{ label: 'Paguei', onClick }}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Luz atrasada');
    await user.click(screen.getByRole('button', { name: 'Paguei' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    unmount();

    render(<Alert tone="vence-logo" title="Internet vence amanhã" />);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('Internet vence amanhã');
  });
});

describe('Toast', () => {
  function Trigger({ onAction }: { onAction?: () => void }) {
    const toast = useToast();
    return (
      <button
        type="button"
        onClick={() =>
          toast.show({
            message: 'Item removido',
            action: onAction ? { label: 'Desfazer', onAction } : undefined,
          })
        }
      >
        Remover
      </button>
    );
  }

  it('keeps a polite live region without an empty status role', () => {
    const { container } = render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('runs Desfazer and closes the toast', async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    render(
      <ToastProvider>
        <Trigger onAction={onAction} />
      </ToastProvider>,
    );
    await user.click(screen.getByRole('button', { name: 'Remover' }));
    const toast = screen.getByRole('status');
    expect(toast).toHaveTextContent('Item removido');
    await user.click(within(toast).getByRole('button', { name: 'Desfazer' }));
    expect(onAction).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('disappears by itself after 5 s and clears timers on unmount', () => {
    vi.useFakeTimers();
    const { unmount } = render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Remover' }));
    expect(screen.getByRole('status')).toHaveTextContent('Item removido');
    act(() => {
      vi.advanceTimersByTime(4999);
    });
    expect(screen.getByRole('status')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(screen.queryByRole('status')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Remover' }));
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('is available in every screen rendered with renderWithProviders', () => {
    renderWithProviders(<Trigger />);
    fireEvent.click(screen.getByRole('button', { name: 'Remover' }));
    expect(screen.getByRole('status')).toHaveTextContent('Item removido');
  });

  it('throws a clear error outside the provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => render(<Trigger />)).toThrow(/ToastProvider/);
    spy.mockRestore();
  });
});

describe('Dialog', () => {
  function Harness({ onConfirm }: { onConfirm?: () => void | Promise<void> }) {
    const [open, setOpen] = useState(true);
    return (
      <>
        <p data-testid="estado">{open ? 'aberto' : 'fechado'}</p>
        <Dialog
          open={open}
          onOpenChange={setOpen}
          title="Sair da casa?"
          description="Você deixa de ver as contas da República Jabuticaba."
          confirmLabel="Sair da casa"
          tone="danger"
          onConfirm={onConfirm}
        />
      </>
    );
  }

  it('is a labelled modal dialog that closes with Esc', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const dialog = screen.getByRole('dialog', { name: 'Sair da casa?' });
    expect(dialog).toHaveAccessibleDescription(/república jabuticaba/i);
    expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByTestId('estado')).toHaveTextContent('fechado');
  });

  it('confirms and closes; stays open when the action fails', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockRejectedValueOnce(new Error('falhou')).mockResolvedValueOnce(undefined);
    render(<Harness onConfirm={onConfirm} />);
    await user.click(screen.getByRole('button', { name: 'Sair da casa' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sair da casa' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(onConfirm).toHaveBeenCalledTimes(2);
  });

  it('cancels with the Cancelar button', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});

describe('Panel', () => {
  it('opens as a labelled dialog and closes with Fechar or Esc', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            Nova despesa
          </button>
          <Panel
            open={open}
            onOpenChange={setOpen}
            title="Nova despesa"
            description="Divida entre os moradores"
            footer={<Button>Salvar</Button>}
          >
            <label>
              Descrição
              <input />
            </label>
          </Panel>
        </>
      );
    }
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'Nova despesa' }));
    const panel = screen.getByRole('dialog', { name: 'Nova despesa' });
    expect(panel).toHaveAccessibleDescription('Divida entre os moradores');
    expect(within(panel).getByRole('button', { name: 'Salvar' })).toBeInTheDocument();
    await user.click(within(panel).getByRole('button', { name: 'Fechar' }));
    expect(screen.queryByRole('dialog')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Nova despesa' }));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});

describe('CompatibilityRing', () => {
  it('shows "Novo no Nosso Teto" without inventing a percentage', () => {
    render(<CompatibilityRing score={null} />);
    expect(screen.getByText('Novo no Nosso Teto')).toBeInTheDocument();
    expect(screen.queryByText(/%/)).toBeNull();
  });

  it('labels the ring with the score', () => {
    render(<CompatibilityRing score={85} />);
    expect(screen.getByRole('img', { name: 'Compatibilidade de 85%' })).toBeInTheDocument();
  });
});

describe('Avatar and EmptyState', () => {
  it('falls back to initials with the full name as label', () => {
    render(<Avatar name="Marina Costa" />);
    const avatar = screen.getByRole('img', { name: 'Marina Costa' });
    expect(avatar).toHaveTextContent('MC');
  });

  it('renders the empty state', () => {
    render(<EmptyState title="Nada por aqui" description="Crie a primeira tarefa" action={<Button>Nova tarefa</Button>} />);
    expect(screen.getByText('Nada por aqui')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nova tarefa' })).toBeInTheDocument();
  });
});

describe('Celebration', () => {
  it('throws confetti and calls onDone by timeout', () => {
    vi.useFakeTimers();
    const onDone = vi.fn();
    render(<Celebration title="Bem-vindo, Rafael!" subtitle="Casa Verde · agora é sua casa" onDone={onDone} />);
    expect(screen.getByRole('heading', { name: 'Bem-vindo, Rafael!' })).toBeInTheDocument();
    expect(screen.getByTestId('celebration-confetti')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('renders no confetti with prefers-reduced-motion', () => {
    setPrefersReducedMotion(true);
    render(<Celebration title="Bem-vindo, Rafael!" />);
    expect(screen.getByRole('heading', { name: 'Bem-vindo, Rafael!' })).toBeInTheDocument();
    expect(screen.queryByTestId('celebration-confetti')).toBeNull();
  });
});

describe('motion hooks and polyfills', () => {
  it('useReducedMotion follows matchMedia change events', () => {
    function Probe() {
      return <p>{useReducedMotion() ? 'reduzido' : 'normal'}</p>;
    }
    render(<Probe />);
    expect(screen.getByText('normal')).toBeInTheDocument();
    act(() => setPrefersReducedMotion(true));
    expect(screen.getByText('reduzido')).toBeInTheDocument();
    act(() => setPrefersReducedMotion(false));
    expect(screen.getByText('normal')).toBeInTheDocument();
  });

  it('matchMedia supports the legacy listener API', () => {
    const listener = vi.fn();
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
    mql.addListener(listener);
    setPrefersReducedMotion(true);
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ matches: true }));
    mql.removeListener(listener);
    setPrefersReducedMotion(false);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('matchMedia removes both function and EventListenerObject listeners', () => {
    const fnListener = vi.fn();
    const objListener: EventListenerObject = { handleEvent: vi.fn() };
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
    mql.addEventListener('change', fnListener);
    mql.addEventListener('change', objListener);
    setPrefersReducedMotion(true);
    expect(fnListener).toHaveBeenCalledTimes(1);
    expect(objListener.handleEvent).toHaveBeenCalledTimes(1);

    mql.removeEventListener('change', fnListener);
    mql.removeEventListener('change', objListener);
    setPrefersReducedMotion(false);
    expect(fnListener).toHaveBeenCalledTimes(1);
    expect(objListener.handleEvent).toHaveBeenCalledTimes(1);
  });

  function RevealProbe() {
    const ref = useRef<HTMLDivElement>(null);
    const { isVisible } = useScrollReveal(ref);
    return (
      <div ref={ref} data-testid="secao">
        {isVisible ? 'visível' : 'aguardando'}
      </div>
    );
  }

  it('useScrollReveal reveals on intersection', () => {
    render(<RevealProbe />);
    expect(screen.getByText('aguardando')).toBeInTheDocument();
    act(() => triggerIntersection(screen.getByTestId('secao')));
    expect(screen.getByText('visível')).toBeInTheDocument();
  });

  it('useScrollReveal is visible right away with reduced motion', () => {
    setPrefersReducedMotion(true);
    render(<RevealProbe />);
    expect(screen.getByText('visível')).toBeInTheDocument();
  });

  it('installs jsdom polyfills', () => {
    expect(typeof ResizeObserver).toBe('function');
    expect(typeof IntersectionObserver).toBe('function');
    const el = document.createElement('div');
    expect(() => {
      el.scrollIntoView();
      el.setPointerCapture(1);
      el.releasePointerCapture(1);
      window.scrollTo(0, 0);
    }).not.toThrow();
    expect(el.hasPointerCapture(1)).toBe(false);
  });
});

describe('SplashOverlay', () => {
  it('does not show when the session flag already exists', () => {
    renderWithProviders(<SplashOverlay />);
    expect(screen.queryByTestId('splash-overlay')).toBeNull();
  });

  it('shows once per session, sets the flag and closes with Pular', async () => {
    sessionStorage.clear();
    const user = userEvent.setup();
    renderWithProviders(<SplashOverlay />);
    const splash = screen.getByTestId('splash-overlay');
    expect(within(splash).queryByRole('heading')).toBeNull();
    expect(sessionStorage.getItem(SPLASH_SEEN_KEY)).toBe('1');
    await user.click(screen.getByRole('button', { name: 'Pular' }));
    expect(screen.queryByTestId('splash-overlay')).toBeNull();
  });

  it('merges custom className onto the root overlay container', () => {
    sessionStorage.clear();
    renderWithProviders(<SplashOverlay className="custom-splash-class" />);
    const splash = screen.getByTestId('splash-overlay');
    expect(splash).toHaveClass('fixed', 'inset-0', 'custom-splash-class');
  });

  it('closes by itself after the minimum time once the session is resolved', async () => {
    sessionStorage.clear();
    setPrefersReducedMotion(true);

    let resolveRefresh!: () => void;
    const refreshPending = new Promise<void>((resolve) => {
      resolveRefresh = resolve;
    });
    server.use(
      http.post('/oauth/token', async () => {
        await refreshPending;
        return HttpResponse.json({ error: 'invalid_grant' }, { status: 400 });
      }),
    );

    renderWithProviders(<SplashOverlay />);
    expect(screen.getByTestId('splash-overlay')).toBeInTheDocument();

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, SPLASH_REDUCED_MS + 100));
    });
    expect(screen.getByTestId('splash-overlay')).toBeInTheDocument();

    resolveRefresh();
    await waitFor(() => expect(screen.queryByTestId('splash-overlay')).toBeNull(), {
      timeout: 3000,
    });
  });

  it('does not block the routes rendered underneath', async () => {
    sessionStorage.clear();
    renderWithProviders(
      <>
        <AppRoutes />
        <SplashOverlay />
      </>,
    );
    expect(screen.getByTestId('splash-overlay')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { level: 1, name: /nosso teto/i })).toBeInTheDocument();
  });
});
