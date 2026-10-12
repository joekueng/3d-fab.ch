import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, of } from 'rxjs';
import deTranslations from '../../../../../assets/i18n/de.json';
import itTranslations from '../../../../../assets/i18n/it.json';
import { LanguageService } from '../../../../core/services/language.service';
import {
  OptionsResponse,
  QuoteEstimatorService,
} from '../../services/quote-estimator.service';
import { UploadFormComponent } from './upload-form.component';

describe('UploadFormComponent', () => {
  let fixture: ComponentFixture<UploadFormComponent>;
  let component: UploadFormComponent;
  let translate: TranslateService;

  const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('de');
  const languageServiceStub = {
    currentLang,
    selectedLang: () => currentLang(),
    localizedPath: (path: string) => `/de${path}`,
  };
  const options: OptionsResponse = {
    materials: [
      {
        code: 'PLA',
        label: 'PLA (Standard)',
        isTechnical: false,
        variants: [],
      },
      {
        code: 'PETG',
        label: 'PETG (Standard)',
        isTechnical: false,
        variants: [],
      },
      { code: 'PA', label: 'PA (Technical)', isTechnical: true, variants: [] },
      {
        code: 'TPU',
        label: 'TPU (Flexible)',
        isTechnical: false,
        variants: [],
      },
    ],
    qualities: [
      { id: 'draft', label: 'Draft' },
      { id: 'standard', label: 'Standard' },
      { id: 'extra_fine', label: 'High Definition' },
    ],
    infillPatterns: [
      { id: 'grid', label: 'Grid' },
      { id: 'gyroid', label: 'Gyroid' },
    ],
    nozzleDiameters: [
      { value: 0.4, label: '0.4 mm (Standard)' },
      { value: 0.8, label: '0.8 mm' },
    ],
    layerHeights: [
      { value: 0.2, label: '0.20 mm' },
      { value: 0.3, label: '0.30 mm' },
    ],
    layerHeightsByNozzle: [
      {
        nozzleDiameter: 0.4,
        layerHeights: [
          { value: 0.2, label: '0.20 mm' },
          { value: 0.3, label: '0.30 mm' },
        ],
      },
    ],
  };

  beforeEach(async () => {
    currentLang.set('de');

    await TestBed.configureTestingModule({
      imports: [UploadFormComponent],
      providers: [
        provideTranslateService(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: QuoteEstimatorService,
          useValue: { getOptions: () => of(options) },
        },
        { provide: LanguageService, useValue: languageServiceStub },
      ],
    }).compileComponents();

    translate = TestBed.inject(TranslateService);
    translate.setFallbackLang('it');
    translate.setTranslation('de', deTranslations);
    translate.setTranslation('it', itTranslations);
    await firstValueFrom(translate.use('de'));

    fixture = TestBed.createComponent(UploadFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('refreshes option labels without changing selections when language changes', async () => {
    component.form.get('quality')?.setValue('extra_fine');

    expect(component.materials()[3]?.label).toBe('TPU (Flexibel)');
    expect(component.qualities().map((quality) => quality.label)).toEqual([
      'Entwurf',
      'Standard',
      'Hohe Auflösung',
    ]);
    expect(component.infillPatterns()[0]?.label).toBe('Gitter');

    await firstValueFrom(translate.use('it'));
    currentLang.set('it');
    fixture.detectChanges();

    expect(component.materials()[3]?.label).toBe('TPU (Flessibile)');
    expect(component.qualities().map((quality) => quality.label)).toEqual([
      'Bozza',
      'Standard',
      'Alta definizione',
    ]);
    expect(component.infillPatterns()[0]?.label).toBe('Griglia');
    expect(component.form.get('quality')?.value).toBe('extra_fine');
  });
  it('keeps every setting isolated and displays the selected file values', async () => {
    fixture.componentRef.setInput('mode', 'advanced');
    fixture.detectChanges();
    const first = new File(['model'], 'same-name.3mf');
    const second = new File(['model'], 'same-name.3mf');
    component.setFiles([first, second]);
    component.onSameSettingsToggle(false);
    fixture.detectChanges();
    await fixture.whenStable();
    const original = { ...component.items()[0] };
    const element: HTMLElement = fixture.nativeElement;
    const panel = () => element.querySelector('app-print-settings')!;
    const select = async (name: string, value: string) => {
      const control = panel().querySelector<HTMLSelectElement>(
        `app-select[formControlName="${name}"] select`,
      )!;
      control.value = Array.from(control.options).find((option) =>
        option.textContent?.includes(value),
      )!.value;
      control.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    await select('material', 'PETG');
    await select('nozzleDiameter', '0.8');
    await select('layerHeight', '0.30');
    await select('infillPattern', 'Gyroid');
    const density = panel().querySelector<HTMLInputElement>(
      'app-input[formControlName="infillDensity"] input',
    )!;
    density.value = '42';
    density.dispatchEvent(new Event('input'));
    const support =
      panel().querySelector<HTMLInputElement>('app-checkbox input')!;
    support.checked = false;
    support.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.items()[0]).toEqual(original);
    expect(component.getCurrentRequestDraft().items[1]).toEqual(
      jasmine.objectContaining({
        material: 'PETG',
        nozzleDiameter: 0.8,
        layerHeight: 0.3,
        infillPattern: 'gyroid',
        infillDensity: 42,
        supportEnabled: false,
      }),
    );
    // Same filenames still represent independent uploads.
    element.querySelectorAll<HTMLElement>('.file-card')[0].click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      panel().querySelector<HTMLSelectElement>(
        'app-select[formControlName="material"] select',
      )!.selectedOptions[0].textContent,
    ).toContain('PLA');
    const selectedText = (name: string) =>
      panel().querySelector<HTMLSelectElement>(
        `app-select[formControlName="${name}"] select`,
      )!.selectedOptions[0].textContent;
    expect(selectedText('nozzleDiameter')).toContain('0.4');
    expect(selectedText('layerHeight')).toContain('0.20');
    expect(selectedText('infillPattern')).toContain('Gitter');
    expect(density.value).toBe(String(original.infillDensity));
    expect(support.checked).toBe(original.supportEnabled);
    element.querySelectorAll<HTMLElement>('.file-card')[1].click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(selectedText('material')).toContain('PETG');
    expect(selectedText('nozzleDiameter')).toContain('0.8');
    expect(selectedText('layerHeight')).toContain('0.30');
    expect(selectedText('infillPattern')).toContain('Gyroid');
    expect(density.value).toBe('42');
    expect(support.checked).toBeFalse();
    expect(component.items()[0]).toEqual(original);
    const draft = component.getCurrentRequestDraft();
    component.restoreRequestDraft(draft, { sameSettingsForAll: false });
    expect(component.getCurrentRequestDraft().items).toEqual(draft.items);
    component.onFilesDropped([new File(['model'], 'third.3mf')]);
    expect(component.getCurrentRequestDraft().items.slice(0, 2)).toEqual(
      draft.items,
    );
    component.removeItem(2);
    expect(component.getCurrentRequestDraft().items).toEqual(draft.items);
    component.onSameSettingsToggle(true);
    expect(component.items()[0].material).toBe('PETG');
    expect(component.items()[0].infillDensity).toBe(42);
    expect(component.items()[0].supportEnabled).toBeFalse();
  });

  it('clamps technical material options only for the edited file', () => {
    fixture.componentRef.setInput('mode', 'advanced');
    fixture.detectChanges();
    const first = new File(['model'], 'first.3mf');
    const second = new File(['model'], 'second.3mf');
    component.setFiles([first, second]);
    component.form.patchValue({ nozzleDiameter: 0.8, layerHeight: 0.3 });
    component.onSameSettingsToggle(false);
    component.form.get('material')!.setValue('PA');
    expect(component.items()[0].material).toBe('PLA');
    expect(component.items()[0].nozzleDiameter).toBe(0.8);
    expect(component.items()[1].material).toBe('PA');
    expect(component.items()[1].nozzleDiameter).toBe(0.4);
    component.selectFile(first);
    expect(component.form.get('nozzleDiameter')!.value).toBe(0.8);
    expect(component.nozzleDiameters().map((option) => option.value)).toContain(
      0.8,
    );
  });
});
