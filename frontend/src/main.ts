import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeDeCH from '@angular/common/locales/de-CH';
import localeItCH from '@angular/common/locales/it-CH';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

registerLocaleData(localeDeCH);
registerLocaleData(localeItCH);

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
