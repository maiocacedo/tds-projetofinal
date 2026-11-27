import { forwardRef, useState } from 'react';

export interface AvatarProps {
  name: string;
  src?: string | null;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZE_CLASSES: Record<NonNullable<AvatarProps['size']>, string> = {
  sm: 'size-8 text-xs',
  md: 'size-11 text-sm',
  lg: 'size-16 text-xl',
};

// Pares fundo/texto com contraste ≥ 4,5:1.
const PALETTE = [
  'bg-tomate text-fundo',
  'bg-tinta text-fundo',
  'bg-pontos-fundo text-pontos-texto',
  'bg-feito-fundo text-feito-texto',
  'bg-superficie text-tinta',
] as const;

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return `${first}${last}`.toLocaleUpperCase('pt-BR');
}

function paletteFor(name: string): string {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return PALETTE[hash % PALETTE.length] ?? PALETTE[0];
}

/** Foto da pessoa ou, sem foto (ou se ela falhar), as iniciais do nome. */
export const Avatar = forwardRef<HTMLSpanElement, AvatarProps>(function Avatar(
  { name, src, size = 'md', className },
  ref,
) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const showImage = Boolean(src) && failedSrc !== src;
  const label = name.trim() || 'Pessoa sem nome';

  return (
    <span
      ref={ref}
      role="img"
      aria-label={label}
      className={[
        'inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full font-extrabold',
        SIZE_CLASSES[size],
        showImage ? 'bg-superficie' : paletteFor(label),
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {showImage && src ? (
        <img
          src={src}
          alt=""
          className="size-full object-cover"
          onError={() => setFailedSrc(src)}
        />
      ) : (
        <span aria-hidden="true">{initialsOf(label)}</span>
      )}
    </span>
  );
});
