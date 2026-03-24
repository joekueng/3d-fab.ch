import { HttpInterceptorFn } from '@angular/common/http';
import { inject, REQUEST } from '@angular/core';
import {
  RequestLike,
  resolveRequestOrigin,
} from '../../../core/request-origin';

const FORWARDED_REQUEST_HEADERS = [
  'authorization',
  'cookie',
  'accept-language',
] as const;

function isAbsoluteUrl(url: string): boolean {
  return /^[a-z][a-z\d+\-.]*:/i.test(url) || url.startsWith('//');
}

function normalizeRelativePath(url: string): string {
  const withoutDot = url.replace(/^\.\//, '');
  return withoutDot.startsWith('/') ? withoutDot : `/${withoutDot}`;
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

export const serverOriginInterceptor: HttpInterceptorFn = (req, next) => {
  if (isAbsoluteUrl(req.url)) {
    return next(req);
  }

  const request = inject(REQUEST, { optional: true }) as RequestLike | null;
  const origin = resolveRequestOrigin(request);
  if (!origin) {
    return next(req);
  }

  const absoluteUrl = `${origin}${normalizeRelativePath(req.url)}`;
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
