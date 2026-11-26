export interface ErrorModule {
  messages?: Record<string, string>;
}

const DEFAULT_FALLBACK =
  'Não foi possível concluir a ação agora. Tente novamente em instantes.';

export function mergeErrorModules(
  modules: Record<string, ErrorModule>,
): Record<string, string> {
  const merged: Record<string, string> = Object.create(null);
  const owners = new Map<string, string>();

  for (const path of Object.keys(modules).sort()) {
    const mod = modules[path];
    if (!mod?.messages) {
      continue;
    }

    for (const [key, value] of Object.entries(mod.messages)) {
      const existingOwner = owners.get(key);
      if (existingOwner !== undefined) {
        throw new Error(
          `Duplicate API error key "${key}" registered in "${path}" (already registered in "${existingOwner}")`,
        );
      }
      owners.set(key, path);
      merged[key] = value;
    }
  }

  return merged;
}

const modules = import.meta.glob<ErrorModule>(
  ['./*.ts', '!./index.ts', '!./*.test.ts'],
  { eager: true },
);

export const errorMessages: Record<string, string> = mergeErrorModules(modules);

export function translateApiError(
  codeOrMessage: string,
  fallback?: string,
): string {
  if (Object.prototype.hasOwnProperty.call(errorMessages, codeOrMessage)) {
    return errorMessages[codeOrMessage];
  }
  return fallback ?? DEFAULT_FALLBACK;
}
