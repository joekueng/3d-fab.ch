import { TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AdminHomeMediaComponent } from './admin-home-media.component';
import {
  AdminMediaAsset,
  AdminMediaService,
} from '../services/admin-media.service';
import { AdminTranslationService } from '../services/admin-translation.service';
import { CalculatorOptionsService } from '../../calculator/services/calculator-options.service';
import type { OptionsResponse } from '../../calculator/services/quote-estimator.service';
import type { PricingExample } from '../../../core/services/public-media.service';
import en from '../../../../assets/i18n/en.json';

const text = { it: 'text', en: 'text', de: 'text', fr: 'text' };
const example: PricingExample = {
  material: 'PLA',
  dimensionsMm: '20 × 20 × 10',
  quantity: 1,
  priceChf: 12.5,
  quotedOn: '2026-10-01',
  settings: text,
  costsIncluded: text,
  verified: true,
};
const asset: AdminMediaAsset = {
  id: 'asset',
  originalFilename: 'sample.jpg',
  storageKey: 'sample.jpg',
  mimeType: 'image/jpeg',
  fileSizeBytes: 12,
  sha256Hex: 'test',
  widthPx: 100,
  heightPx: 100,
  status: 'READY',
  visibility: 'PUBLIC',
  title: 'Sample',
  altText: 'Printed sample',
  createdAt: '2026-10-01',
  updatedAt: '2026-10-01',
  variants: [],
  usages: [
    {
      id: 'usage',
      usageType: 'PRICING_EXAMPLE',
      usageKey: 'functional',
      ownerId: null,
      mediaAssetId: 'asset',
      sortOrder: 0,
      isPrimary: true,
      isActive: true,
      createdAt: '2026-10-01',
      pricingExample: example,
      translations: {
        it: { title: 'Campione', altText: 'Foto campione' },
        en: { title: 'Sample', altText: 'Sample photo' },
        de: { title: 'Muster', altText: 'Musterfoto' },
        fr: { title: 'Échantillon', altText: 'Photo échantillon' },
      },
    },
  ],
};

describe('Guide media back-office', () => {
  it('creates stable filament scopes and saves existing pricing data without uploading another image', async () => {
    const media = jasmine.createSpyObj<AdminMediaService>('AdminMediaService', [
      'listAssets',
      'updateUsage',
      'uploadAsset',
    ]);
    media.listAssets.and.returnValue(of([asset]));
    media.updateUsage.and.returnValue(of(asset.usages[0]));
    const options: OptionsResponse = {
      materials: [
        {
          code: 'PLA',
          label: 'PLA',
          isTechnical: false,
          variants: [
            {
              id: 17,
              name: 'Black',
              colorName: 'black',
              hexColor: '#222222',
              finishType: 'MATTE',
              stockSpools: 0,
              stockFilamentGrams: 0,
              isOutOfStock: true,
            },
          ],
        },
      ],
      qualities: [],
      infillPatterns: [],
      layerHeights: [],
      nozzleDiameters: [],
      layerHeightsByNozzle: [],
    };
    await TestBed.configureTestingModule({
      imports: [AdminHomeMediaComponent],
      providers: [
        provideTranslateService(),
        { provide: AdminMediaService, useValue: media },
        { provide: AdminTranslationService, useValue: {} },
        {
          provide: CalculatorOptionsService,
          useValue: { getOptions: () => of(options) },
        },
      ],
    }).compileComponents();
    TestBed.inject(TranslateService).setTranslation('en', en);
    TestBed.inject(TranslateService).use('en');
    const fixture = TestBed.createComponent(AdminHomeMediaComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(
      component.sectionConfigs.find(
        (section) => section.id === 'FILAMENT_VARIANT::17',
      )?.usageKey,
    ).toBe('17');
    const section = component.sections.find(
      (section) => section.id === 'PRICING_EXAMPLE::functional',
    )!;
    component.prepareReplace(section.id, section.items[0]);
    component.getFormState(section.id).pricingExample.priceChf = 19.5;
    component.uploadForSection(section.id);
    expect(media.uploadAsset).not.toHaveBeenCalled();
    expect(media.updateUsage).toHaveBeenCalledWith(
      'usage',
      jasmine.objectContaining({
        pricingExample: jasmine.objectContaining({
          priceChf: 19.5,
          verified: true,
        }),
      }),
    );
    expect(example.priceChf).toBe(12.5); // editing cannot mutate the loaded asset before saving.
    component.prepareReplace(section.id, {
      ...section.items[0],
      pricingExample: { settings: null, costsIncluded: null, verified: false },
    });
    expect(component.getFormState(section.id).pricingExample.settings.en).toBe(
      '',
    );
    component.prepareAdd('PRICING_EXAMPLE::series');
    expect(
      component.getFormState('PRICING_EXAMPLE::series').pricingExample.quantity,
    ).toBe(10);
  });
});
