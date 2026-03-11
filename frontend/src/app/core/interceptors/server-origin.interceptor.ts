import { HttpInterceptorFn } from '@angular/common/http';
import { inject, REQUEST } from '@angular/core';

type RequestLike = {
  protocol?: string;
  get?: (name: string) => string | undefined;
  headers?: Record<string, string | string[] | undefined>;
};

function isAbsoluteUrl(url: string): boolean {
  return /^[a-z][a-z\d+\-.]*:/i.test(url) || url.startsWith('//');
}

function firstHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return typeof value === 'string' ? value : null;
}

function resolveOrigin(request: RequestLike | null): string | null {
  if (!request) {
    return null;
  }

  const host =
    request.get?.('host') ??
    firstHeaderValue(request.headers?.['host']) ??
    firstHeaderValue(request.headers?.['x-forwarded-host']);
  if (!host) {
    return null;
  }

  const forwardedProtoRaw = firstHeaderValue(
    request.headers?.['x-forwarded-proto'],
  );
  const forwardedProto = forwardedProtoRaw
    ?.split(',')
    .map((part) => part.trim().toLowerCase())
    .find(Boolean);
  const protocol = forwardedProto || request.protocol || 'http';
  return `${protocol}://${host}`;
}

function normalizeRelativePath(url: string): string {
  const withoutDot = url.replace(/^\.\//, '');
  return withoutDot.startsWith('/') ? withoutDot : `/${withoutDot}`;
}

export const serverOriginInterceptor: HttpInterceptorFn = (req, next) => {
  if (isAbsoluteUrl(req.url)) {
    return next(req);
  }

  const request = inject(REQUEST, { optional: true }) as RequestLike | null;
  const origin = resolveOrigin(request);
  if (!origin) {
    return next(req);
  }

  const absoluteUrl = `${origin}${normalizeRelativePath(req.url)}`;
  return next(req.clone({ url: absoluteUrl }));
};
