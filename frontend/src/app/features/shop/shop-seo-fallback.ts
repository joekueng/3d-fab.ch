const PRODUCT_ID_PREFIX_PATTERN = /^[0-9a-f]{8}-(?=[a-z0-9])/i;
const UPPERCASE_TOKENS = new Set([
  '3d',
  'abs',
  'asa',
  'cad',
  'cf',
  'gf',
  'pa',
  'pc',
  'petg',
  'pla',
  'pp',
  'tpu',
  'uv',
]);

export function humanizeShopSlug(
  value: string | null | undefined,
  options?: {
    stripProductIdPrefix?: boolean;
  },
): string {
  const normalized = normalizeShopSlug(value, options?.stripProductIdPrefix);
  if (!normalized) {
    return '';
  }

  return normalized
    .split('-')
    .filter(Boolean)
    .map(formatSlugToken)
    .join(' ')
    .trim();
}

function normalizeShopSlug(
  value: string | null | undefined,
  stripProductIdPrefix = false,
): string {
  const normalized = String(value ?? '')
    .trim()
    .replace(/^\/+|\/+$/g, '')
    .split('/')
    .filter(Boolean)
    .at(-1)
    ?.toLowerCase();

  if (!normalized) {
    return '';
  }

  return stripProductIdPrefix
    ? normalized.replace(PRODUCT_ID_PREFIX_PATTERN, '')
    : normalized;
}

function formatSlugToken(token: string): string {
  if (!token) {
    return '';
  }

  if (/^\d+$/.test(token)) {
    return token;
  }

  if (UPPERCASE_TOKENS.has(token)) {
    return token.toUpperCase();
  }

  return `${token.charAt(0).toUpperCase()}${token.slice(1)}`;
}
