import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  provideTranslateLoader,
  provideTranslateService,
  TranslateService,
  TranslationObject,
} from '@ngx-translate/core';
import { firstValueFrom, of, Subject } from 'rxjs';
import { options } from '../../../../scripts/fixtures/guides-data.mjs';
import enTranslations from '../../../assets/i18n/en.json';
import itTranslations from '../../../assets/i18n/it.json';
import { LanguageService } from '../../core/services/language.service';
import { PublicMediaService } from '../../core/services/public-media.service';
import { CalculatorOptionsService } from '../calculator/services/calculator-options.service';
import { ColorsFinishesPageComponent } from './colors-finishes-page.component';

describe('ColorsFinishesPageComponent', () => {
  it('updates both filters after the selected language finishes loading', async () => {
    const currentLang = signal('it');
    const enBundle = new Subject<TranslationObject>();
    TestBed.configureTestingModule({
      imports: [ColorsFinishesPageComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({
          loader: provideTranslateLoader(() => ({
            getTranslation: (lang: string) =>
              lang === 'en' ? enBundle : of(itTranslations),
          })),
        }),
        {
          provide: LanguageService,
          useValue: { currentLang, localizedPath: (path: string) => path },
        },
        {
          provide: CalculatorOptionsService,
          useValue: { getOptions: () => of(options) },
        },
        {
          provide: PublicMediaService,
          useValue: { getUsageCollectionsBatch: () => of({}) },
        },
      ],
    });

    const translate = TestBed.inject(TranslateService);
    await firstValueFrom(translate.setFallbackLang('it'));
    await firstValueFrom(translate.use('it'));
    const fixture = TestBed.createComponent(ColorsFinishesPageComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();

    const filterLabels = () =>
      Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll(
          'select option',
        ),
        (option) => option.textContent?.trim(),
      );
    expect(filterLabels()).toEqual(
      jasmine.arrayContaining([
        'Tutti i materiali',
        'Tutte le finiture',
        'Opachi',
      ]),
    );

    const languageLoaded = firstValueFrom(translate.use('en'));
    currentLang.set('en');
    await fixture.whenStable();
    expect(filterLabels()).toContain('Tutte le finiture');
    enBundle.next(enTranslations);
    enBundle.complete();
    await languageLoaded;
    await fixture.whenStable();

    expect(filterLabels()).toEqual(
      jasmine.arrayContaining(['All materials', 'All finishes', 'Matte']),
    );
  });
});
