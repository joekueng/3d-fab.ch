import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { catchError, map, Observable, of } from 'rxjs';
import { AdminAuthService } from '../services/admin-auth.service';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

function resolveLang(route: ActivatedRouteSnapshot): string {
  for (const level of route.pathFromRoot) {
    const candidate = level.paramMap.get('lang');
    if (candidate && SUPPORTED_LANGS.has(candidate)) {
      return candidate;
    }
  }
  return 'it';
}

export const adminAuthGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const authService = inject(AdminAuthService);
  const router = inject(Router);
  const lang = resolveLang(route);

  return authService.me().pipe(
    map((isAuthenticated) => {
      if (isAuthenticated) {
        return true;
      }
      return router.createUrlTree(['/', lang, 'admin', 'login'], {
        queryParams: { redirect: state.url }
      });
    }),
    catchError(() => of(
      router.createUrlTree(['/', lang, 'admin', 'login'], {
        queryParams: { redirect: state.url }
      })
    ))
  );
};
