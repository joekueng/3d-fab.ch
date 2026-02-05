import { Injectable, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class LanguageService {
  currentLang = signal('it');

  constructor(private translate: TranslateService) {
    this.translate.addLangs(['it', 'en']);
    this.translate.setDefaultLang('it');
    this.translate.use('it');
  }

  switchLang(lang: string) {
    this.translate.use(lang);
    this.currentLang.set(lang);
  }
}
