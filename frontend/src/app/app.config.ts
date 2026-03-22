import {
  ApplicationConfig,
  provideAppInitializer,
  provideZoneChangeDetection,
  importProvidersFrom,
  inject,
  REQUEST,
} from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withViewTransitions,
  Router,
} from '@angular/router';
import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  TranslateLoader,
  TranslateModule,
  TranslateService,
} from '@ngx-translate/core';
import { adminAuthInterceptor } from './core/interceptors/admin-auth.interceptor';
import {
  provideClientHydration,
  withEventReplay,
} from '@angular/platform-browser';
import { serverOriginInterceptor } from './core/interceptors/server-origin.interceptor';
import { catchError, firstValueFrom, of } from 'rxjs';
import { StaticTranslateLoader } from './core/i18n/static-translate.loader';
import {
  resolveInitialLanguage,
  SUPPORTED_LANGS,
} from './core/i18n/language-resolution';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withViewTransitions(),
      withInMemoryScrolling({
        scrollPositionRestoration: 'enabled',
      }),
    ),
    provideHttpClient(
      withInterceptors([serverOriginInterceptor, adminAuthInterceptor]),
    ),
    importProvidersFrom(
      TranslateModule.forRoot({
        fallbackLang: 'it',
        loader: {
          provide: TranslateLoader,
          useClass: StaticTranslateLoader,
        },
      }),
    ),
    provideAppInitializer(() => {
      const translate = inject(TranslateService);
      const router = inject(Router);
      const request = inject(REQUEST, { optional: true }) as {
        url?: string;
        headers?: Record<string, string | string[] | undefined>;
      } | null;

      translate.addLangs([...SUPPORTED_LANGS]);
      translate.setFallbackLang('it');
      const requestedUrl =
        (typeof request?.url === 'string' && request.url) || router.url || '/';
      const lang = resolveInitialLanguage({
        url: requestedUrl,
      });

      return firstValueFrom(
        translate.use(lang).pipe(
          catchError((error) => {
            console.error('[i18n] Failed to preload language for SSR', {
              lang,
              requestedUrl,
              error,
            });
            return of({});
          }),
        ),
      ).then(() => undefined);
    }),
    provideClientHydration(withEventReplay()),
  ],
};
