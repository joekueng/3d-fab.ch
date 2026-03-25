import { HttpInterceptorFn } from '@angular/common/http';
import { inject, REQUEST } from '@angular/core';
import {
  RequestLike,
  resolveRequestOrigin,
} from '../../../core/request-origin';

type ServerRequestLike = RequestLike & {
  originalUrl?: string;
  url?: string;
};

const FORWARDED_REQUEST_HEADERS = [
  'authorization',
  'cookie',
  'accept-language',
] as const;

const SHOP_DISCOVERY_API_PATTERNS = [
  /^\/api\/shop\/categories(?:\/[^/?#]+)?$/i,
  /^\/api\/shop\/products$/i,
  /^\/api\/shop\/products\/by-id-prefix\/[^/?#]+$/i,
  /^\/api\/shop\/products\/by-path\/[^/?#]+$/i,
  /^\/api\/shop\/products\/[^/?#]+$/i,
] as const;

const SHOP_PAGE_PATH_PATTERN = /^\/(?:it|en|de|fr)\/shop(?:\/.*)?$/i;

function isAbsoluteUrl(url: string): boolean {
  return /^[a-z][a-z\d+\-.]*:/i.test(url) || url.startsWith('//');
}

function normalizeRelativePath(url: string): string {
  const withoutDot = url.replace(/^\.\//, '');
  return withoutDot.startsWith('/') ? withoutDot : `/${withoutDot}`;
}

function stripQueryAndHash(url: string): string {
  return String(url ?? '').split(/[?#]/, 1)[0] || '/';
}

function normalizeOrigin(origin: string): string {
  return origin.replace(/\/+$/, '');
}

function readRequestHeader(
  request: RequestLike | null,
  name: (typeof FORWARDED_REQUEST_HEADERS)[number],
): string | null {
  const normalizedName = name.toLowerCase();
  const headerValue =
    request?.headers?.[normalizedName] ?? request?.get?.(normalizedName);
  if (Array.isArray(headerValue)) {
    return headerValue[0] ?? null;
  }

  return typeof headerValue === 'string' ? headerValue : null;
}

function readRequestPath(request: ServerRequestLike | null): string | null {
  const rawPath =
    (typeof request?.originalUrl === 'string' && request.originalUrl) ||
    (typeof request?.url === 'string' && request.url) ||
    null;
  if (!rawPath) {
    return null;
  }

  if (isAbsoluteUrl(rawPath)) {
    try {
      return stripQueryAndHash(new URL(rawPath).pathname || '/');
    } catch {
      return null;
    }
  }

  return stripQueryAndHash(rawPath.startsWith('/') ? rawPath : `/${rawPath}`);
}

function isPublicShopPageRequest(request: ServerRequestLike | null): boolean {
  const requestPath = readRequestPath(request);
  return !!requestPath && SHOP_PAGE_PATH_PATTERN.test(requestPath);
}

function isPublicShopDiscoveryApi(url: string): boolean {
  const normalizedPath = stripQueryAndHash(normalizeRelativePath(url));
  return SHOP_DISCOVERY_API_PATTERNS.some((pattern) =>
    pattern.test(normalizedPath),
  );
}

function readInternalApiOrigin(): string | null {
  const globalObject = globalThis as {
    __SSR_INTERNAL_API_ORIGIN__?: string;
    process?: {
      env?: Record<string, string | undefined>;
    };
  };
  const explicitOverride =
    typeof globalObject.__SSR_INTERNAL_API_ORIGIN__ === 'string'
      ? globalObject.__SSR_INTERNAL_API_ORIGIN__
      : null;
  const env = (
    globalObject as {
      process?: {
        env?: Record<string, string | undefined>;
      };
    }
  ).process?.env;
  const rawValue = explicitOverride ?? env?.['SSR_INTERNAL_API_ORIGIN'];
  if (typeof rawValue !== 'string') {
    return null;
  }

  const normalized = rawValue.trim();
  return normalized ? normalizeOrigin(normalized) : null;
}

function resolveApiOrigin(
  request: ServerRequestLike | null,
  relativeUrl: string,
): string | null {
  const internalOrigin = readInternalApiOrigin();
  if (
    internalOrigin &&
    isPublicShopPageRequest(request) &&
    isPublicShopDiscoveryApi(relativeUrl)
  ) {
    return internalOrigin;
  }

  return resolveRequestOrigin(request);
}

export const serverOriginInterceptor: HttpInterceptorFn = (req, next) => {
  if (isAbsoluteUrl(req.url)) {
    return next(req);
  }

  const request = inject(REQUEST, { optional: true }) as ServerRequestLike | null;
  const origin = resolveApiOrigin(request, req.url);
  if (!origin) {
    return next(req);
  }

  const absoluteUrl = `${normalizeOrigin(origin)}${normalizeRelativePath(req.url)}`;
  const forwardedHeaders = FORWARDED_REQUEST_HEADERS.reduce<
    Record<string, string>
  >((headers, name) => {
    if (req.headers.has(name)) {
      return headers;
    }

    const value = readRequestHeader(request, name);
    if (value) {
      headers[name] = value;
    }
    return headers;
  }, {});

  return next(
    req.clone({
      url: absoluteUrl,
      setHeaders: forwardedHeaders,
    }),
  );
};
