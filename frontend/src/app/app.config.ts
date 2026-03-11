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

type SupportedLang = 'it' | 'en' | 'de' | 'fr';
const SUPPORTED_LANGS: readonly SupportedLang[] = ['it', 'en', 'de', 'fr'];

function resolveLangFromUrl(url: string): SupportedLang {
  const firstSegment = (url || '/')
    .split('?')[0]
    .split('#')[0]
    .split('/')
    .filter(Boolean)[0]
    ?.toLowerCase();
  return SUPPORTED_LANGS.includes(firstSegment as SupportedLang)
    ? (firstSegment as SupportedLang)
    : 'it';
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withViewTransitions(),
      withInMemoryScrolling({
        scrollPositionRestoration: 'top',
      }),
    ),
    provideHttpClient(
      withInterceptors([serverOriginInterceptor, adminAuthInterceptor]),
    ),
    importProvidersFrom(
      TranslateModule.forRoot({
        defaultLanguage: 'it',
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
      } | null;

      translate.addLangs([...SUPPORTED_LANGS]);
      translate.setDefaultLang('it');
      const requestedUrl =
        (typeof request?.url === 'string' && request.url) || router.url || '/';
      const lang = resolveLangFromUrl(requestedUrl);

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
