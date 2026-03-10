import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { FormBuilder } from '@angular/forms';
import { CheckoutComponent } from './checkout.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { LanguageService } from '../../core/services/language.service';

describe('CheckoutComponent', () => {
  let component: CheckoutComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        FormBuilder,
        {
          provide: QuoteEstimatorService,
          useValue: jasmine.createSpyObj<QuoteEstimatorService>(
            'QuoteEstimatorService',
            ['getQuoteSession'],
          ),
        },
        {
          provide: Router,
          useValue: jasmine.createSpyObj<Router>('Router', ['navigate']),
        },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({}),
          },
        },
        {
          provide: LanguageService,
          useValue: {
            selectedLang: () => 'it',
          },
        },
      ],
    });

    component = TestBed.runInInjectionContext(() => new CheckoutComponent());
  });

  it('prefers shop variant metadata for labels and swatches', () => {
    const item = {
      lineItemType: 'SHOP_PRODUCT',
      displayName: 'Desk Cable Clip',
      shopProductName: 'Desk Cable Clip',
      shopVariantLabel: 'Coral Red',
      shopVariantColorName: 'Coral Red',
      shopVariantColorHex: '#ff6b6b',
      colorCode: 'Rosso',
    };

    expect(component.isShopItem(item)).toBeTrue();
    expect(component.itemDisplayName(item)).toBe('Desk Cable Clip');
    expect(component.itemVariantLabel(item)).toBe('Coral Red');
    expect(component.itemColorLabel(item)).toBe('Coral Red');
    expect(component.itemColorSwatch(item)).toBe('#ff6b6b');
    expect(component.showItemMaterial(item)).toBeFalse();
    expect(component.showItemPrintMetrics(item)).toBeFalse();
  });
});
