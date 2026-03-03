import { of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { CalculatorPageComponent } from './calculator-page.component';
import {
  QuoteEstimatorService,
  QuoteResult,
} from './services/quote-estimator.service';
import { LanguageService } from '../../core/services/language.service';
import { UploadFormComponent } from './components/upload-form/upload-form.component';

describe('CalculatorPageComponent', () => {
  const createResult = (sessionId: string, notes?: string): QuoteResult => ({
    sessionId,
    items: [
      {
        id: 'line-1',
        fileName: 'part-a.stl',
        unitPrice: 4,
        unitTime: 120,
        unitWeight: 2,
        quantity: 1,
      },
    ],
    setupCost: 2,
    globalMachineCost: 0,
    currency: 'CHF',
    totalPrice: 6,
    totalTimeHours: 0,
    totalTimeMinutes: 2,
    totalWeight: 2,
    notes,
  });

  function createComponent() {
    const estimator = jasmine.createSpyObj<QuoteEstimatorService>(
      'QuoteEstimatorService',
      ['updateLineItem', 'getQuoteSession', 'mapSessionToQuoteResult'],
    );
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const route = {
      data: of({}),
      queryParams: of({}),
    } as unknown as ActivatedRoute;
    const languageService = jasmine.createSpyObj<LanguageService>(
      'LanguageService',
      ['selectedLang'],
    );

    const component = new CalculatorPageComponent(
      estimator,
      router,
      route,
      languageService,
    );

    const uploadForm = jasmine.createSpyObj<UploadFormComponent>(
      'UploadFormComponent',
      ['updateItemQuantityByIndex', 'updateItemQuantityByName'],
    );
    component.uploadForm = uploadForm;

    return {
      component,
      estimator,
      uploadForm,
    };
  }

  it('updates left panel quantities even when item id is missing', () => {
    const { component, estimator, uploadForm } = createComponent();

    component.onItemChange({
      index: 0,
      fileName: 'part-a.stl',
      quantity: 4,
    });

    expect(uploadForm.updateItemQuantityByIndex).toHaveBeenCalledWith(0, 4);
    expect(uploadForm.updateItemQuantityByName).toHaveBeenCalledWith(
      'part-a.stl',
      4,
    );
    expect(estimator.updateLineItem).not.toHaveBeenCalled();
  });

  it('refreshes quote totals after successful line item update', () => {
    const { component, estimator } = createComponent();
    component.result.set(createResult('session-1', 'persisted notes'));

    estimator.updateLineItem.and.returnValue(of({ ok: true }));
    estimator.getQuoteSession.and.returnValue(of({ session: { id: 'session-1' } }));
    estimator.mapSessionToQuoteResult.and.returnValue(createResult('session-1'));

    component.onItemChange({
      id: 'line-1',
      index: 0,
      fileName: 'part-a.stl',
      quantity: 7,
    });

    expect(estimator.updateLineItem).toHaveBeenCalledWith('line-1', {
      quantity: 7,
    });
    expect(estimator.getQuoteSession).toHaveBeenCalledWith('session-1');
    expect(component.result()?.notes).toBe('persisted notes');
    expect(component.result()?.items[0].quantity).toBe(1);
  });
});
