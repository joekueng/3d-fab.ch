import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, of } from 'rxjs';
import enTranslations from '../../../assets/i18n/en.json';
import itTranslations from '../../../assets/i18n/it.json';
import { MaterialsPageComponent } from './materials-page.component';
import { PublicMediaService } from '../../core/services/public-media.service';
import { LanguageService } from '../../core/services/language.service';

describe('MaterialsPageComponent', () => {
  let fixture: ComponentFixture<MaterialsPageComponent>;
  let component: MaterialsPageComponent;
  let translate: TranslateService;
  let publicMediaService: jasmine.SpyObj<PublicMediaService>;

  const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('it');
  const languageServiceStub = {
    currentLang,
    localizedPath: (path: string) => `/${currentLang()}${path}`,
  };

  async function switchLanguage(lang: 'it' | 'en') {
    await firstValueFrom(translate.use(lang));
    currentLang.set(lang);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    currentLang.set('it');

    publicMediaService = jasmine.createSpyObj<PublicMediaService>(
      'PublicMediaService',
      ['getUsageCollections', 'pickPrimaryUsage', 'toDisplayImage'],
    );
    publicMediaService.getUsageCollections.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [MaterialsPageComponent, TranslateModule.forRoot()],
      providers: [
        { provide: PublicMediaService, useValue: publicMediaService },
        { provide: LanguageService, useValue: languageServiceStub },
      ],
    }).compileComponents();

    translate = TestBed.inject(TranslateService);
    translate.setFallbackLang('it');
    translate.setTranslation('it', itTranslations);
    translate.setTranslation('en', enTranslations);
    await firstValueFrom(translate.use('it'));

    fixture = TestBed.createComponent(MaterialsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the selector below the radar without a duplicate legend', () => {
    const host = fixture.nativeElement as HTMLElement;
    const heroTitle = host.querySelector('.ui-simple-hero__title');
    const chartCard = host.querySelector(
      '.materials-panel--chart',
    ) as HTMLElement;
    const selectorPanel = chartCard.querySelector(
      '.selector-panel',
    ) as HTMLElement;

    expect(heroTitle?.textContent?.trim()).toBe('Qualita e Materiali');
    expect(chartCard).toBeTruthy();
    expect(selectorPanel).toBeTruthy();
    expect(chartCard.querySelector('.chart-legend')).toBeNull();
    expect(host.querySelectorAll('.material-card').length).toBe(0);
  });

  it('updates radar and table content when a material is toggled', () => {
    component.toggleMaterial('pla-matte');
    fixture.detectChanges();

    expect(
      component.selectedMaterials().map((material) => material.id),
    ).toEqual(['pla-basic', 'asa', 'pet-cf', 'pla-matte']);

    const tableHeaders = Array.from(
      fixture.nativeElement.querySelectorAll(
        'thead th',
      ) as NodeListOf<HTMLTableCellElement>,
    ).map((cell) => cell.textContent?.trim());

    expect(tableHeaders).toContain('PLA Matte');
    expect(
      fixture.nativeElement.querySelectorAll('.selector-chip.is-selected')
        .length,
    ).toBe(4);
  });

  it('keeps selected chip and radar colors aligned by selection order', () => {
    component.toggleMaterial('pla-matte');
    component.toggleMaterial('tpu-95a-hf');
    fixture.detectChanges();

    expect(component.legendDotColor('pla-basic')).toBe('#c23b22');
    expect(component.legendDotColor('asa')).toBe('#2663d3');
    expect(component.legendDotColor('pet-cf')).toBe('#0f8f6f');
    expect(component.legendDotColor('pla-matte')).toBe('#8a44c9');
    expect(component.radarSeries().map((series) => series.color)).toEqual([
      '#c23b22',
      '#2663d3',
      '#0f8f6f',
      '#8a44c9',
      '#c77510',
    ]);
  });

  it('updates static copy, computed content and localized links when the language changes', async () => {
    const host = fixture.nativeElement as HTMLElement;
    const heroLink = host.querySelector(
      '.materials-inline-link',
    ) as HTMLAnchorElement;
    const calculatorSection = host.querySelector(
      '.materials-section--muted',
    ) as HTMLElement;

    expect(host.querySelector('.ui-simple-hero__title')?.textContent?.trim()).toBe(
      'Qualita e Materiali',
    );
    expect(calculatorSection.textContent).toContain(
      'Come usare il calcolatore',
    );
    expect(calculatorSection.textContent).toContain(
      'Parametri del calcolatore',
    );
    expect(calculatorSection.textContent).toContain(
      'prezzo finale automatico di stampa',
    );
    expect(component.radarAxes()[1]?.label).toBe('Stampabilita');
    expect(component.comparisonRows()[0]?.label).toBe(
      'Stampabilita [indice 0-100]',
    );
    expect(heroLink.getAttribute('href')).toBe('#materials-calculator');
    expect(calculatorSection.getAttribute('id')).toBe('materials-calculator');
    expect(
      Array.from(
        calculatorSection.querySelectorAll('.calculator-fact-actions a'),
      ).map((link) => link.getAttribute('href')),
    ).toEqual([
      '/it/calculator',
      '/it/calculator/basic#calculator-workspace',
      '/it/calculator/advanced#calculator-workspace',
    ]);

    await switchLanguage('en');

    expect(host.querySelector('.ui-simple-hero__title')?.textContent?.trim()).toBe(
      'Quality & Materials',
    );
    expect(calculatorSection.textContent).toContain(
      'How to use the calculator',
    );
    expect(calculatorSection.textContent).toContain(
      'Calculator parameters',
    );
    expect(calculatorSection.textContent).toContain(
      'automatic final print price',
    );
    expect(component.radarAxes()[1]?.label).toBe('Printability');
    expect(component.comparisonRows()[0]?.label).toBe(
      'Printability [0-100 index]',
    );
    expect(
      Array.from(
        calculatorSection.querySelectorAll('.calculator-fact-actions a'),
      ).map((link) => link.getAttribute('href')),
    ).toEqual([
      '/en/calculator',
      '/en/calculator/basic#calculator-workspace',
      '/en/calculator/advanced#calculator-workspace',
    ]);
  });
});
