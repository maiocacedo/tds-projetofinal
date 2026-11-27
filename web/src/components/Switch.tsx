import * as SwitchPrimitive from '@radix-ui/react-switch';
import { forwardRef, useId } from 'react';

export interface SwitchProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  label: string;
  description?: string;
  disabled?: boolean;
  id?: string;
  className?: string;
}

export const Switch = forwardRef<HTMLButtonElement, SwitchProps>(function Switch(
  { checked, onCheckedChange, label, description, disabled = false, id, className },
  ref,
) {
  const generatedId = useId();
  const switchId = id ?? generatedId;
  const descriptionId = description ? `${switchId}-desc` : undefined;

  return (
    <div
      className={[
        'flex min-h-11 items-center justify-between gap-4',
        disabled ? 'opacity-60' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="flex flex-col gap-0.5">
        <label
          htmlFor={switchId}
          className={[
            'text-[15px] font-semibold text-tinta',
            disabled ? 'cursor-not-allowed' : 'cursor-pointer',
          ].join(' ')}
        >
          {label}
        </label>
        {description && (
          <p id={descriptionId} className="text-[13px] text-legenda">
            {description}
          </p>
        )}
      </div>
      <SwitchPrimitive.Root
        ref={ref}
        id={switchId}
        checked={checked}
        onCheckedChange={onCheckedChange}
        disabled={disabled}
        aria-describedby={descriptionId}
        className={[
          'relative inline-flex h-8 w-[52px] shrink-0 items-center rounded-full p-1 transition-colors duration-300 cursor-pointer',
          'disabled:cursor-not-allowed',
          checked ? 'bg-tomate' : 'bg-linha',
        ].join(' ')}
      >
        <SwitchPrimitive.Thumb
          className={[
            'nt-switch-thumb block size-6 rounded-full bg-fundo shadow-xs',
            checked ? 'translate-x-5' : 'translate-x-0',
          ].join(' ')}
        />
      </SwitchPrimitive.Root>
    </div>
  );
});
