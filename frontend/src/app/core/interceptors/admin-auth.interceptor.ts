import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

function resolveLangFromUrl(url: string): string {
  const cleanUrl = (url || '').split('?')[0].split('#')[0];
  const segments = cleanUrl.split('/').filter(Boolean);
  if (segments.length > 0 && SUPPORTED_LANGS.has(segments[0])) {
    return segments[0];
  }
  return 'it';
}

export const adminAuthInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/admin/')) {
    return next(req);
  }

  const router = inject(Router);
  const request = req.clone({ withCredentials: true });
  const isLoginRequest = request.url.includes('/api/admin/auth/login');

  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        !isLoginRequest &&
        error instanceof HttpErrorResponse &&
        error.status === 401
      ) {
        const lang = resolveLangFromUrl(router.url);
        if (!router.url.includes('/admin/login')) {
          void router.navigate(['/', lang, 'admin', 'login']);
        }
      }
      return throwError(() => error);
    }),
  );
};
