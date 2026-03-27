import { of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { CalculatorPageComponent } from './calculator-page.component';
import {
  PendingCalculatorDraft,
  QuoteEstimatorService,
  QuoteRequest,
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

  const createDraftRequest = (): QuoteRequest => ({
    items: [
      {
        file: new File(['mesh'], 'part-a.stl', { type: 'model/stl' }),
        quantity: 2,
        material: 'PLA',
        quality: 'standard',
        color: 'Black',
        supportEnabled: true,
        infillDensity: 15,
        infillPattern: 'grid',
        layerHeight: 0.2,
        nozzleDiameter: 0.4,
      },
    ],
    material: 'PLA',
    quality: 'standard',
    notes: 'draft note',
    infillDensity: 15,
    infillPattern: 'grid',
    supportEnabled: true,
    layerHeight: 0.2,
    nozzleDiameter: 0.4,
    mode: 'easy',
  });

  function createComponent() {
    const estimator = jasmine.createSpyObj<QuoteEstimatorService>(
      'QuoteEstimatorService',
      [
        'updateLineItem',
        'getQuoteSession',
        'mapSessionToQuoteResult',
        'setPendingCalculatorDraft',
        'consumePendingCalculatorDraft',
      ],
    );
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const route = {
      data: of({}),
      queryParams: of({}),
      snapshot: {
        routeConfig: { path: 'basic' },
        queryParams: {},
        queryParamMap: {
          get: () => null,
        },
      },
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
      [
        'updateItemQuantityByIndex',
        'updateItemQuantityByName',
        'getCurrentRequestDraft',
        'restoreRequestDraft',
      ],
    );
    uploadForm.sameSettingsForAll = jasmine
      .createSpy('sameSettingsForAll')
      .and.returnValue(true) as any;
    uploadForm.selectedFile = jasmine
      .createSpy('selectedFile')
      .and.returnValue(null) as any;
    component.uploadForm = uploadForm;

    return {
      component,
      estimator,
      route,
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
    estimator.getQuoteSession.and.returnValue(
      of({ session: { id: 'session-1' } }),
    );
    estimator.mapSessionToQuoteResult.and.returnValue(
      createResult('session-1'),
    );

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

  it('builds mode-specific content keys', () => {
    const { component } = createComponent();

    component.mode.set('easy');
    expect(component.modeContentKey('TITLE')).toBe('CALC.MODES.BASIC.TITLE');

    component.mode.set('advanced');
    expect(component.modeContentKey('TITLE')).toBe('CALC.MODES.ADVANCED.TITLE');
  });

  it('exposes the expected external model sources and faq entries', () => {
    const { component } = createComponent();

    expect(component.favoriteModelSources.map((entry) => entry.id)).toEqual([
      'PRINTABLES',
      'MAKERWORLD',
    ]);
    expect(component.otherModelSources.map((entry) => entry.id)).toEqual([
      'THINGIVERSE',
      'THANGS',
      'CULTS3D',
      'YEGGI',
    ]);
    expect(component.modelSources.map((entry) => entry.id)).toEqual([
      'PRINTABLES',
      'MAKERWORLD',
      'THINGIVERSE',
      'THANGS',
      'CULTS3D',
      'YEGGI',
    ]);
    expect(component.faqIds).toEqual([
      'FILES',
      'MODE',
      'NO_MODEL',
      'PRICE',
      'BEFORE_UPLOAD',
    ]);
  });

  it('stores the current draft before switching mode without a session', () => {
    const { component, estimator, uploadForm } = createComponent();
    const draftRequest = createDraftRequest();
    uploadForm.getCurrentRequestDraft.and.returnValue(draftRequest);
    (uploadForm.sameSettingsForAll as jasmine.Spy).and.returnValue(false);
    (uploadForm.selectedFile as jasmine.Spy).and.returnValue(
      draftRequest.items[0].file,
    );

    component.switchMode('advanced');

    expect(estimator.setPendingCalculatorDraft).toHaveBeenCalledWith({
      request: draftRequest,
      sameSettingsForAll: false,
      selectedFileName: 'part-a.stl',
    });
  });

  it('restores a pending draft after view init when there is no session', () => {
    const { component, estimator, uploadForm } = createComponent();
    const draftRequest = createDraftRequest();
    const pendingDraft: PendingCalculatorDraft = {
      request: draftRequest,
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
    };
    estimator.consumePendingCalculatorDraft.and.returnValue(pendingDraft);

    component.ngAfterViewInit();

    expect(uploadForm.restoreRequestDraft).toHaveBeenCalledWith(draftRequest, {
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
    });
  });
});
