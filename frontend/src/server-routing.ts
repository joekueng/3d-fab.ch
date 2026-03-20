const SUPPORTED_LANG_LIST = ['it', 'en', 'de', 'fr'] as const;

export const SUPPORTED_LANGS = new Set<string>(SUPPORTED_LANG_LIST);
export const DEFAULT_LANG = 'it';

export function resolvePublicRedirectTarget(pathname: string): string | null {
  const normalizedPath = normalizePathname(pathname);
  if (shouldBypassRedirect(normalizedPath)) {
    return null;
  }

  const trimmedPath =
    normalizedPath === '/' ? '/' : normalizedPath.replace(/\/+$/, '');
  const segments = splitSegments(trimmedPath);
  if (segments.length === 0) {
    return null;
  }

  const firstSegment = segments[0].toLowerCase();
  if (SUPPORTED_LANGS.has(firstSegment)) {
    const canonicalSegments = [firstSegment, ...segments.slice(1)];
    const canonicalPath = `/${canonicalSegments.join('/')}`;
    const directRedirect = resolveCanonicalRedirect(canonicalSegments);
    if (directRedirect) {
      return directRedirect;
    }
    return canonicalPath === normalizedPath ? null : canonicalPath;
  }

  const effectiveSegments = looksLikeLangToken(firstSegment)
    ? segments.slice(1)
    : segments;
  if (effectiveSegments.length === 0) {
    return `/${DEFAULT_LANG}`;
  }

  const directRedirect = resolveCanonicalRedirect([
    DEFAULT_LANG,
    ...effectiveSegments,
  ]);
  if (directRedirect) {
    return directRedirect;
  }

  return `/${[DEFAULT_LANG, ...effectiveSegments].join('/')}`;
}

function resolveCanonicalRedirect(segments: string[]): string | null {
  const [lang, section, thirdSegment, fourthSegment] = segments;

  if (section?.toLowerCase() === 'calculator' && segments.length === 2) {
    return `/${lang}/calculator/basic`;
  }

  if (
    section?.toLowerCase() === 'shop' &&
    segments.length === 4 &&
    thirdSegment?.toLowerCase() !== 'p' &&
    fourthSegment
  ) {
    return `/${lang}/shop/p/${fourthSegment}`;
  }

  return null;
}

function normalizePathname(pathname: string): string {
  const rawValue = String(pathname || '/').trim();
  if (!rawValue) {
    return '/';
  }

  return rawValue.startsWith('/') ? rawValue : `/${rawValue}`;
}

function shouldBypassRedirect(pathname: string): boolean {
  if (
    pathname.startsWith('/api/') ||
    pathname.startsWith('/assets/') ||
    pathname.startsWith('/media/')
  ) {
    return true;
  }

  if (
    pathname === '/robots.txt' ||
    pathname === '/sitemap.xml' ||
    pathname === '/sitemap-static.xml' ||
    pathname === '/favicon.ico'
  ) {
    return true;
  }

  return /\.[^/]+$/.test(pathname);
}

function splitSegments(pathname: string): string[] {
  return pathname.split('/').filter(Boolean);
}

function looksLikeLangToken(segment: string | null | undefined): boolean {
  return typeof segment === 'string' && /^[a-z]{2}(?:-[a-z]{2})?$/i.test(segment);
}
