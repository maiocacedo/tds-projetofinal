import {
  forwardRef,
  useRef,
  type ForwardedRef,
  type KeyboardEvent,
  type ReactElement,
  type ReactNode,
} from 'react';
import { useJellyIndicator } from '../motion/useJellyIndicator';

export interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  icon?: ReactNode;
}

export interface SegmentedProps<T extends string> {
  options: SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  'aria-label': string;
  disabled?: boolean;
  className?: string;
}

function SegmentedInner<T extends string>(
  { options, value, onChange, 'aria-label': ariaLabel, disabled = false, className }: SegmentedProps<T>,
  forwardedRef: ForwardedRef<HTMLDivElement>,
) {
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const activeIndex = options.findIndex((opt) => opt.value === value);
  const indicator = useJellyIndicator(containerRef, activeIndex);

  const setGroupRef = (node: HTMLDivElement | null) => {
    containerRef.current = node;
    if (typeof forwardedRef === 'function') {
      forwardedRef(node);
    } else if (forwardedRef) {
      forwardedRef.current = node;
    }
  };

  const selectIndex = (index: number) => {
    if (disabled || options.length === 0) return;
    const wrapped = (index + options.length) % options.length;
    const target = options[wrapped];
    if (!target) return;
    if (target.value !== value) {
      onChange(target.value);
    }
    buttonRefs.current[wrapped]?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (disabled || options.length === 0) return;
    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        event.preventDefault();
        selectIndex(index + 1);
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        event.preventDefault();
        selectIndex(index - 1);
        break;
      case 'Home':
        event.preventDefault();
        selectIndex(0);
        break;
      case 'End':
        event.preventDefault();
        selectIndex(options.length - 1);
        break;
      case ' ':
      case 'Enter': {
        event.preventDefault();
        const opt = options[index];
        if (opt && opt.value !== value) {
          onChange(opt.value);
        }
        break;
      }
      default:
        break;
    }
  };

  return (
    <div
      ref={setGroupRef}
      role="radiogroup"
      aria-label={ariaLabel}
      aria-disabled={disabled || undefined}
      className={[
        'relative inline-flex w-full items-stretch rounded-full bg-superficie p-1',
        disabled ? 'opacity-60' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {indicator.ready && (
        <span
          aria-hidden="true"
          className="nt-ind top-1 bottom-1 rounded-full bg-tinta"
          style={indicator.style}
        />
      )}
      {options.map((option, index) => {
        const isSelected = option.value === value;
        const tabIndex = isSelected || (activeIndex === -1 && index === 0) ? 0 : -1;
        return (
          <button
            key={option.value}
            ref={(el) => {
              buttonRefs.current[index] = el;
            }}
            type="button"
            role="radio"
            aria-checked={isSelected}
            tabIndex={disabled ? -1 : tabIndex}
            disabled={disabled}
            data-jelly-item="true"
            data-jelly-key={option.value}
            onClick={() => {
              if (!disabled && option.value !== value) {
                onChange(option.value);
              }
            }}
            onKeyDown={(e) => handleKeyDown(e, index)}
            className={[
              'nt-press relative z-[1] flex min-h-11 flex-1 items-center justify-center gap-1.5 rounded-full px-3 text-sm cursor-pointer select-none transition-colors duration-300',
              isSelected
                ? indicator.ready
                  ? 'font-bold text-fundo'
                  : 'bg-tinta font-bold text-fundo'
                : 'font-semibold text-tinta hover:text-tomate-escuro',
            ].join(' ')}
          >
            {option.icon && (
              <span aria-hidden="true" className="inline-flex shrink-0 items-center">
                {option.icon}
              </span>
            )}
            <span>{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}

export const Segmented = forwardRef(SegmentedInner) as <T extends string>(
  props: SegmentedProps<T> & { ref?: ForwardedRef<HTMLDivElement> },
) => ReactElement;
