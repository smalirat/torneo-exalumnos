import messages from './es-AR.json'

/**
 * Minimal translator: t('standings.pts'), t('categories.zones', { n: 2 }).
 * Returns the key itself when it does not exist, so missing texts are visible.
 */
export function t(key: string, params: Record<string, string | number> = {}): string {
  const value = key.split('.').reduce<unknown>(
    (node, part) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined),
    messages,
  )
  if (typeof value !== 'string') return key
  return value.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? `{${name}}`))
}
