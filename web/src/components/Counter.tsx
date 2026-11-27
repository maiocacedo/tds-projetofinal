import { Minus, Plus } from 'lucide-react';
import {
  forwardRef,
  useEffect,
  useId,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
} from 'react';
import { useReducedMotion } from '../motion/useReducedMotion';

export interface CounterProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  label: string;
  suffix?: string;
  disabled?: boolean;
  id?: string;
  className?: string;
}

function clampValue(num: number, min?: number, max?: number): number {
  let result = num;
  if (min !== undefined && result < min) result = min;
  if (max !== undefined && result > max) result = max;
  return result;
}

export const Counter = forwardRef<HTMLInputElement, CounterProps>(function Counter(
  {
    value,
    onChange,
    min,
    max,
    step = 1,
    label,
    suffix,
    disabled = false,
    id,
    className,
  },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const reducedMotion = useReducedMotion();

  const [draft, setDraft] = useState<string>(String(value));
  const [popCycle, setPopCycle] = useState(0);
  const prevValueRef = useRef(value);

  useEffect(() => {
    setDraft(String(value));
    if (prevValueRef.current !== value) {
      prevValueRef.current = value;
      setPopCycle((c) => c + 1);
    }
  }, [value]);

  const atMin = min !== undefined && value <= min;
  const atMax = max !== undefined && value >= max;

  const commitDraft = (raw: string) => {
    const trimmed = raw.trim().replace(',', '.');
    if (trimmed === '' || trimmed === '-' || trimmed === '.') {
      setDraft(String(value));
      return;
    }
    const parsed = Number(trimmed);
    if (!Number.isFinite(parsed)) {
      setDraft(String(value));
      return;
    }
    const clamped = clampValue(parsed, min, max);
    setDraft(String(clamped));
    if (clamped !== value) {
      onChange(clamped);
    }
  };

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const raw = event.target.value;
    setDraft(raw);

    const trimmed = raw.trim().replace(',', '.');
    if (
      trimmed === '' ||
      trimmed === '-' ||
      trimmed.endsWith('.') ||
      trimmed.endsWith(',')
    ) {
      return;
    }
    const parsed = Number(trimmed);
    if (!Number.isFinite(parsed)) return;

    const withinMin = min === undefined || parsed >= min;
    const withinMax = max === undefined || parsed <= max;
    if (withinMin && withinMax && parsed !== value) {
      onChange(parsed);
    }
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      commitDraft(draft);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      increment();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      decrement();
    }
  };

  const decrement = () => {
    if (disabled || atMin) return;
    const next = clampValue(Number((value - step).toFixed(6)), min, max);
    setDraft(String(next));
    onChange(next);
  };

  const increment = () => {
    if (disabled || atMax) return;
    const next = clampValue(Number((value + step).toFixed(6)), min, max);
    setDraft(String(next));
    onChange(next);
  };

  const popClass =
    reducedMotion || popCycle === 0
      ? ''
      : popCycle % 2 === 1
        ? 'nt-num-pop-a'
        : 'nt-num-pop-b';

  return (
    <div
      className={[
        'inline-flex flex-col gap-1.5',
        disabled ? 'opacity-60' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <label htmlFor={inputId} className="text-sm font-semibold text-tinta">
        {label}
      </label>
      <div className="inline-flex items-center gap-2">
        <button
          type="button"
          aria-label={`Diminuir ${label}`}
          disabled={disabled || atMin}
          onClick={decrement}
          className="nt-press inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-linha bg-superficie text-tinta hover:bg-linha cursor-pointer disabled:cursor-not-allowed disabled:opacity-45"
        >
          <Minus aria-hidden="true" className="size-4" />
        </button>

        <div className="inline-flex items-center gap-1.5">
          <input
            ref={ref}
            id={inputId}
            type="text"
            inputMode={Number.isInteger(step) ? 'numeric' : 'decimal'}
            role="spinbutton"
            aria-valuemin={min}
            aria-valuemax={max}
            aria-valuenow={value}
            disabled={disabled}
            value={draft}
            onChange={handleInputChange}
            onBlur={() => commitDraft(draft)}
            onKeyDown={handleKeyDown}
            className={[
              'nt-numin min-h-11 w-16 rounded-xl border border-linha bg-fundo px-2 text-center text-base font-extrabold text-tinta',
              'focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/25',
              'disabled:cursor-not-allowed disabled:bg-superficie',
              popClass,
            ]
              .filter(Boolean)
              .join(' ')}
          />
          {suffix && (
            <span className="text-sm font-semibold text-texto-suave select-none">
              {suffix}
            </span>
          )}
        </div>

        <button
          type="button"
          aria-label={`Aumentar ${label}`}
          disabled={disabled || atMax}
          onClick={increment}
          className="nt-press inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-linha bg-superficie text-tinta hover:bg-linha cursor-pointer disabled:cursor-not-allowed disabled:opacity-45"
        >
          <Plus aria-hidden="true" className="size-4" />
        </button>
      </div>
    </div>
  );
});
