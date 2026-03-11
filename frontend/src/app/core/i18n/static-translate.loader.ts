import { Injectable } from '@angular/core';
import {
  TranslateLoader,
  TranslationObject,
} from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import de from '../../../assets/i18n/de.json';
import en from '../../../assets/i18n/en.json';
import fr from '../../../assets/i18n/fr.json';
import it from '../../../assets/i18n/it.json';

const TRANSLATIONS: Record<string, TranslationObject> = {
  it: it as TranslationObject,
  en: en as TranslationObject,
  de: de as TranslationObject,
  fr: fr as TranslationObject,
};

@Injectable()
export class StaticTranslateLoader implements TranslateLoader {
  getTranslation(lang: string): Observable<TranslationObject> {
    const normalized = String(lang || 'it').toLowerCase();
    return of(TRANSLATIONS[normalized] ?? TRANSLATIONS['it']);
  }
}
