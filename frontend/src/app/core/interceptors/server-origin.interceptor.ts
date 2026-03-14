import { HttpInterceptorFn } from '@angular/common/http';
import { inject, REQUEST } from '@angular/core';
import {
  RequestLike,
  resolveRequestOrigin,
} from '../../../core/request-origin';

function isAbsoluteUrl(url: string): boolean {
  return /^[a-z][a-z\d+\-.]*:/i.test(url) || url.startsWith('//');
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
  const origin = resolveRequestOrigin(request);
  if (!origin) {
    return next(req);
  }

  const absoluteUrl = `${origin}${normalizeRelativePath(req.url)}`;
  return next(req.clone({ url: absoluteUrl }));
};
