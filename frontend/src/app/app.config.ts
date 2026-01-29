import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient } from '@angular/common/http';

const resolveLocale = () => {
  if (typeof navigator === 'undefined') {
    return 'de-CH';
  }
  const languages = navigator.languages ?? [];
  if (navigator.language === 'it-CH' || languages.includes('it-CH')) {
    return 'it-CH';
  }
  if (navigator.language === 'de-CH' || languages.includes('de-CH')) {
    return 'de-CH';
  }
  return 'de-CH';
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(),
    { provide: LOCALE_ID, useFactory: resolveLocale }
  ]
};
