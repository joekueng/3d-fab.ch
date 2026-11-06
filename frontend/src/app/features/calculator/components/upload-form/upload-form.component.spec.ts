import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { NEVER, Subject, firstValueFrom, of, finalize } from 'rxjs';
import deTranslations from '../../../../../assets/i18n/de.json';
import enTranslations from '../../../../../assets/i18n/en.json';
import frTranslations from '../../../../../assets/i18n/fr.json';
import itTranslations from '../../../../../assets/i18n/it.json';
import { LanguageService } from '../../../../core/services/language.service';
import {
  OptionsResponse,
  QuoteEstimatorService,
} from '../../services/quote-estimator.service';
import { UploadFormComponent } from './upload-form.component';
import { ModelPreviewService } from '../../services/model-preview.service';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';

@Component({ selector: 'app-stl-viewer', standalone: true, template: '' })
class PreviewViewerStub {
  file = input<Blob | null>(null);
  color = input<string>('');
}

describe('UploadFormComponent', () => {
  let fixture: ComponentFixture<UploadFormComponent>;
  let component: UploadFormComponent;
  let translate: TranslateService;
  let previews: jasmine.SpyObj<ModelPreviewService>;

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
      ...[
        ['ASA', 'UV Resistent'],
        ['PA12-CF', 'Very High Temperature resistence'],
        ['PC-ABS', 'High Temperature resistance'],
        ['PET-CF', 'High Resistence'],
        ['PETG-FOOD', 'Food Grade'],
        ['TPU 85A', 'Flessibile'],
        ['CUSTOM', 'Workshop label (custom)'],
      ].map(([code, label]) => ({
        code,
        label: `${code} (${label})`,
        isTechnical: true,
        variants: [],
      })),
      {
        code: 'PLA-CF',
        label: 'PLA-CF (Standard)',
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
      { value: 0.2, label: '0.2 mm' },
      { value: 0.6, label: '0.6 mm' },
    ],
    layerHeights: [0.08, 0.12, 0.16, 0.18, 0.2, 0.24, 0.28, 0.3].map(
      (value) => ({
        value,
        label: `${value.toFixed(2)} mm`,
      }),
    ),
    layerHeightsByNozzle: [
      {
        nozzleDiameter: 0.4,
        layerHeights: [0.08, 0.12, 0.16, 0.2, 0.24, 0.28, 0.3].map((value) => ({
          value,
          label: `${value.toFixed(2)} mm`,
        })),
      },
      {
        nozzleDiameter: 0.6,
        layerHeights: [0.16, 0.18, 0.2, 0.24, 0.3].map((value) => ({
          value,
          label: `${value.toFixed(2)} mm`,
        })),
      },
    ],
  };

  beforeEach(async () => {
    currentLang.set('de');
    previews = jasmine.createSpyObj<ModelPreviewService>(
      'ModelPreviewService',
      ['createStlPreview'],
    );
    previews.createStlPreview.and.returnValue(NEVER);

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
        { provide: ModelPreviewService, useValue: previews },
      ],
    })
      .overrideComponent(UploadFormComponent, {
        remove: { imports: [StlViewerComponent] },
        add: { imports: [PreviewViewerStub] },
      })
      .compileComponents();

    translate = TestBed.inject(TranslateService);
    translate.setFallbackLang('it');
    translate.setTranslation('de', deTranslations);
    translate.setTranslation('it', itTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('fr', frTranslations);
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

  it('renders configured material labels in every locale and preserves selections', async () => {
    component.setFiles([new File(['model'], 'material.3mf')]);
    component.form.get('material')?.setValue('ASA');
    const expectedByLocale = {
      it: [
        'ASA (Resistente ai raggi UV)',
        'PA12-CF (Resistente a temperature molto elevate)',
        'PC-ABS (Resistente alle alte temperature)',
        'PET-CF (Alta resistenza)',
        'PETG-FOOD (Per uso alimentare)',
        'TPU 85A (Flessibile)',
      ],
      en: [
        'ASA (UV resistant)',
        'PA12-CF (Very high temperature resistance)',
        'PC-ABS (High temperature resistance)',
        'PET-CF (High resistance)',
        'PETG-FOOD (Food grade)',
        'TPU 85A (Flexible)',
      ],
      de: [
        'ASA (UV-beständig)',
        'PA12-CF (Sehr hohe Temperaturbeständigkeit)',
        'PC-ABS (Hohe Temperaturbeständigkeit)',
        'PET-CF (Hohe Festigkeit)',
        'PETG-FOOD (Für Lebensmittel geeignet)',
        'TPU 85A (Flexibel)',
      ],
      fr: [
        'ASA (Résistant aux UV)',
        'PA12-CF (Résistant aux très hautes températures)',
        'PC-ABS (Résistant aux températures élevées)',
        'PET-CF (Haute résistance)',
        'PETG-FOOD (Pour usage alimentaire)',
        'TPU 85A (Flexible)',
      ],
    };
    for (const lang of ['it', 'en', 'de', 'fr'] as const) {
      await firstValueFrom(translate.use(lang));
      currentLang.set(lang);
      fixture.detectChanges();
      await fixture.whenStable();
      const element: HTMLElement = fixture.nativeElement;
      const labels = Array.from(
        element.querySelectorAll('select[formControlName="material"] option'),
        (option) => option.textContent?.trim(),
      );
      for (const label of expectedByLocale[lang]) {
        expect(labels).withContext(lang).toContain(label);
      }
      expect(labels).toContain('CUSTOM (Workshop label (custom))');
      expect(component.form.get('material')?.value).toBe('ASA');
      expect(component.items()[0].material).toBe('ASA');
    }
  });

  it('keeps long translated material labels within narrow and wide settings panels', () => {
    component.setFiles([new File(['model'], 'material.3mf')]);
    component.form.get('material')?.setValue('PA12-CF');
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const controls = element.querySelector<HTMLElement>(
      '.easy-global-controls',
    )!;
    for (const width of [320, 1200]) {
      element.style.display = 'block';
      element.style.width = `${width}px`;
      expect(controls.scrollWidth)
        .withContext(`${width}px`)
        .toBeLessThanOrEqual(controls.clientWidth);
    }
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
    expect(component.items()[1].layerHeight).toBe(0.16);
    component.selectFile(first);
    expect(component.form.get('nozzleDiameter')!.value).toBe(0.8);
    expect(component.nozzleDiameters().map((option) => option.value)).toContain(
      0.8,
    );
  });

  it('uses material-specific quick quality presets in the form and outgoing request', () => {
    component.setFiles([
      new File(['model'], 'first.3mf'),
      new File(['model'], 'second.3mf'),
    ]);
    for (const [quality, technicalLayer, standardLayer] of [
      ['draft', 0.24, 0.28],
      ['standard', 0.2, 0.2],
      ['extra_fine', 0.16, 0.12],
    ] as const) {
      component.form.get('quality')!.setValue(quality);
      for (const [material, layer] of [
        ['PA', technicalLayer],
        ['PLA', standardLayer],
      ] as const) {
        component.form.get('material')!.setValue(material);
        expect(component.form.get('layerHeight')!.value)
          .withContext(`${material} ${quality}`)
          .toBe(layer);
        const request = component.getCurrentRequestDraft();
        expect(request.layerHeight).toBe(layer);
        expect(request.items.map((item) => item.layerHeight)).toEqual([
          layer,
          layer,
        ]);
        expect(component.items().map((item) => item.layerHeight)).toEqual([
          layer,
          layer,
        ]);
      }
    }
  });

  it('shows only the three technical layer heights in advanced mode for each nozzle', () => {
    fixture.componentRef.setInput('mode', 'advanced');
    fixture.detectChanges();
    component.setFiles([new File(['model'], 'technical.3mf')]);
    component.form.get('material')!.setValue('PA');
    expect(component.nozzleDiameters().map((option) => option.value)).toEqual([
      0.4, 0.6,
    ]);
    for (const nozzle of [0.4, 0.6]) {
      component.form.get('nozzleDiameter')!.setValue(nozzle);
      fixture.detectChanges();
      expect(component.layerHeights().map((option) => option.value)).toEqual([
        0.16, 0.2, 0.24,
      ]);
      const element: HTMLElement = fixture.nativeElement;
      expect(
        Array.from(
          element.querySelectorAll(
            'app-select[formControlName="layerHeight"] option',
          ),
          (option) => option.textContent?.trim(),
        ),
      ).toEqual(['0.16 mm', '0.20 mm', '0.24 mm']);
    }
    const request = component.getCurrentRequestDraft();
    request.layerHeight = 0.28;
    request.items[0].layerHeight = 0.12;
    component.restoreRequestDraft(request);
    expect(component.getCurrentRequestDraft().items[0].layerHeight).toBe(0.16);
  });

  it('excludes the 0.2 mm nozzle for non-technical PLA-CF without restricting ordinary PLA', () => {
    fixture.componentRef.setInput('mode', 'advanced');
    fixture.detectChanges();
    component.setFiles([new File(['model'], 'carbon.3mf')]);
    component.form.patchValue({ nozzleDiameter: 0.2, layerHeight: 0.12 });
    component.form.get('material')!.setValue('PLA-CF');
    expect(
      component.nozzleDiameters().map((option) => option.value),
    ).not.toContain(0.2);
    expect(component.getCurrentRequestDraft().items[0].nozzleDiameter).toBe(
      0.4,
    );
    expect(component.getCurrentRequestDraft().items[0].layerHeight).toBe(0.12);
    component.setItemPrintSettingsByIndex(0, { nozzleDiameter: 0.2 });
    expect(component.items()[0].nozzleDiameter).toBe(0.4);
    component.form.get('material')!.setValue('PLA');
    expect(component.nozzleDiameters().map((option) => option.value)).toContain(
      0.2,
    );
  });

  for (const extension of ['3mf', 'obj', 'step', 'stp']) {
    it(`converts a ${extension} before calculating and keeps the uploaded original`, async () => {
      fixture.autoDetectChanges();
      const response = new Subject<Blob>();
      previews.createStlPreview.and.returnValue(response);
      const original = new File(['original project'], `assembly.${extension}`);

      component.onFilesDropped([original]);
      fixture.detectChanges();
      expect(component.previewLoading()).toBeTrue();
      expect(previews.createStlPreview).toHaveBeenCalledOnceWith(
        original,
        'mm',
      );

      response.next(new Blob(['solid preview'], { type: 'model/stl' }));
      response.complete();
      await fixture.whenStable();

      expect(component.previewLoading()).toBeFalse();
      expect(component.canPreviewSelectedFile()).toBeTrue();
      expect(component.getSelectedPreviewFile()?.name).toBe('assembly.stl');
      expect(component.getCurrentRequestDraft().items[0].file).toBe(original);
      expect(
        fixture.nativeElement.querySelector('app-stl-viewer'),
      ).not.toBeNull();
    });
  }

  it('cancels stale previews and distinguishes files with identical names', () => {
    const firstResponse = new Subject<Blob>();
    const secondResponse = new Subject<Blob>();
    previews.createStlPreview.and.returnValues(
      firstResponse,
      secondResponse,
      NEVER,
    );
    const first = new File(['first project'], 'same.3mf');
    const second = new File(['second project'], 'same.3mf');
    component.onFilesDropped([first]);
    component.onFilesDropped([second]);
    expect(firstResponse.observed).toBeFalse();
    firstResponse.next(new Blob(['stale']));
    expect(component.items()[0].previewFile).toBeUndefined();
    secondResponse.next(new Blob(['correct preview']));
    secondResponse.complete();
    const preview = component.getSelectedPreviewFile();
    component.selectFile(first);
    component.selectFile(second);
    expect(component.getSelectedPreviewFile()).toBe(preview);
    expect(
      previews.createStlPreview.calls
        .allArgs()
        .filter(([file]) => file === second).length,
    ).toBe(1);
    expect(component.items().map((item) => item.file)).toEqual([first, second]);
  });

  it('retains an OBJ for quoting when its preview fails', () => {
    const response = new Subject<Blob>();
    previews.createStlPreview.and.returnValue(response);
    const original = new File(['v 0 0 0'], 'part.obj');
    component.onFilesDropped([original]);
    response.error(new Error('conversion unavailable'));
    expect(component.previewLoading()).toBeFalse();
    expect(component.canPreviewSelectedFile()).toBeFalse();
    expect(component.getCurrentRequestDraft().items[0].file).toBe(original);
  });

  it('changes OBJ preview and quote units together and caches each unit separately', () => {
    const millimetres = new Subject<Blob>();
    const metres = new Subject<Blob>();
    previews.createStlPreview.and.returnValues(millimetres, metres);
    const original = new File(['v 0 0 0'], 'part.obj');
    component.onFilesDropped([original]);
    millimetres.next(new Blob(['millimetre preview']));
    const smallPreview = component.getSelectedPreviewFile();
    component.setItemReviewStateByIndex(0, 'error', 'too small');
    component.updateItemModelUnit(0, 'm');
    expect(component.getSelectedPreviewFile()).toBeNull();
    expect(component.items()[0].reviewLevel).toBeUndefined();
    expect(previews.createStlPreview).toHaveBeenCalledWith(original, 'm');
    expect(component.getCurrentRequestDraft().items[0].modelUnit).toBe('m');
    metres.next(new Blob(['metre preview']));
    expect(component.items()[0].previewUnit).toBe('m');
    expect(component.getSelectedPreviewFile()).not.toBe(smallPreview);
    component.updateItemModelUnit(0, 'mm');
    expect(component.getSelectedPreviewFile()).toBe(smallPreview);
    expect(previews.createStlPreview.calls.count()).toBe(2);
    expect(component.getCurrentRequestDraft().items[0].file).toBe(original);
  });

  it('discards a restored preview when its units do not match the selected units', () => {
    const original = new File(['project'], 'part.obj');
    component.setFiles([original], { autoSelect: false });
    component.setPreviewFileByIndex(0, new File(['old'], 'part.stl'), 'mm');
    component.setItemPrintSettingsByIndex(0, { modelUnit: 'm' });
    component.selectFile(original);
    expect(component.getSelectedPreviewFile()).toBeNull();
    expect(previews.createStlPreview).toHaveBeenCalledWith(original, 'm');
  });

  it('cancels conversion when the selected file is removed and ignores its late response', () => {
    const response = new Subject<Blob>();
    const cancelled = jasmine.createSpy('cancelled');
    previews.createStlPreview.and.returnValue(
      response.pipe(finalize(cancelled)),
    );
    const original = new File(['CAD'], 'part.step');
    component.onFilesDropped([original]);
    component.removeItem(0);
    expect(cancelled).toHaveBeenCalled();
    response.next(new Blob(['late preview']));
    expect(component.items()).toEqual([]);
    expect(component.selectedFile()).toBeNull();
    expect(component.previewLoading()).toBeFalse();
  });

  it('preserves different OBJ units and matching previews through a draft restore', () => {
    const first = new File(['first'], 'first.obj');
    const second = new File(['second'], 'second.obj');
    component.setFiles([first, second], { autoSelect: false });
    component.setItemPrintSettingsByIndex(0, { modelUnit: 'm' });
    component.setItemPrintSettingsByIndex(1, { modelUnit: 'in' });
    const previewsByIndex = [
      new File(['metres'], 'first.stl'),
      new File(['inches'], 'second.stl'),
    ];
    const draft = component.getCurrentRequestDraft();
    component.restoreRequestDraft(draft, {
      selectedFileName: 'first.obj',
      previewFiles: previewsByIndex,
    });
    expect(
      component.getCurrentRequestDraft().items.map((item) => item.modelUnit),
    ).toEqual(['m', 'in']);
    expect(component.getSelectedPreviewFile()).toBe(previewsByIndex[0]);
    component.selectFile(second);
    expect(component.getSelectedPreviewFile()).toBe(previewsByIndex[1]);
    expect(previews.createStlPreview).not.toHaveBeenCalled();
  });

  it('reuses local and restored STL previews without another upload', () => {
    const original = new File(['project'], 'part.3mf');
    const preview = new File(['solid preview'], 'part.stl');
    component.setFiles([original], { autoSelect: false });
    component.setPreviewFileByIndex(0, preview);
    component.selectFile(original);
    component.onFilesDropped([new File(['solid local'], 'local.stl')]);
    expect(component.canPreviewSelectedFile()).toBeTrue();
    expect(previews.createStlPreview).not.toHaveBeenCalled();
  });
});
