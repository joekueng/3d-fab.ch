import { of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { TestBed } from '@angular/core/testing';
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

  function createComponent(
    platformId?: Object,
    queryParams: Record<string, unknown> = {},
  ) {
    TestBed.resetTestingModule();

    const estimator = jasmine.createSpyObj<QuoteEstimatorService>(
      'QuoteEstimatorService',
      [
        'updateLineItem',
        'getQuoteSession',
        'getLineItemContent',
        'mapSessionToQuoteResult',
        'calculate',
        'setPendingCalculatorDraft',
        'consumePendingCalculatorDraft',
      ],
    );
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const route = {
      data: of({}),
      queryParams: of(queryParams),
      snapshot: {
        routeConfig: { path: 'basic' },
        queryParams,
        queryParamMap: {
          get: (key: string) => {
            const value = queryParams[key];
            return typeof value === 'string' ? value : null;
          },
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
      platformId,
    );

    const uploadForm = jasmine.createSpyObj<UploadFormComponent>(
      'UploadFormComponent',
      [
        'setFiles',
        'setPreviewFileByIndex',
        'patchSettings',
        'setItemPrintSettingsByIndex',
        'updateItemColor',
        'selectFile',
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

  it('shows partial quote results when some files fail', () => {
    const { component, estimator } = createComponent();
    const request = createDraftRequest();
    const result = createResult('session-1');
    result.failedItems = [
      {
        fileName: 'cube-256.stl',
        code: 'MODEL_OUT_OF_PRINT_VOLUME',
        message:
          'This model could not be placed fully inside the printer volume for Bambu Lab A1 0.4 nozzle.',
      },
    ];

    estimator.calculate.and.returnValue(of(result));
    estimator.getQuoteSession.and.returnValue(
      of({ session: { id: 'session-1' }, items: [] }),
    );

    component.onCalculate(request);

    expect(component.error()).toBeFalse();
    expect(component.result()?.sessionId).toBe('session-1');
    expect(component.warningMessage()).toContain('cube-256.stl');
    expect(component.warningMessage()).toContain(
      'This model could not be placed fully inside the printer volume',
    );
  });

  it('shows backend failure message when calculation fails completely', () => {
    const { component, estimator } = createComponent();
    const request = createDraftRequest();

    estimator.calculate.and.returnValue(
      throwError(() => ({
        fileName: 'cube-257.stl',
        code: 'MODEL_OUT_OF_PRINT_VOLUME',
        message:
          'This model could not be placed fully inside the printer volume for Bambu Lab A1 0.4 nozzle.',
      })),
    );

    component.onCalculate(request);

    expect(component.error()).toBeTrue();
    expect(component.errorMessage()).toBe(
      'This model could not be placed fully inside the printer volume for Bambu Lab A1 0.4 nozzle.',
    );
  });

  it('downloads converted previews only for items that expose them', () => {
    const { component, estimator, uploadForm } = createComponent();
    const originalBlob = new Blob(['original']);
    const previewBlob = new Blob(['preview']);

    estimator.getLineItemContent.and.callFake(
      (_sessionId: string, _lineItemId: string, preview = false) =>
        of(preview ? previewBlob : originalBlob),
    );
    (uploadForm.selectedFile as jasmine.Spy).and.returnValue(null);

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
        materialCode: 'PLA',
        layerHeightMm: 0.2,
        nozzleDiameterMm: 0.4,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-stl',
          originalFilename: 'legacy.stl',
          quantity: 1,
        },
        {
          id: 'line-3mf',
          originalFilename: 'converted.3mf',
          quantity: 1,
          convertedStoredPath: 'storage_quotes/session-1/converted.stl',
        },
      ],
    );

    expect(estimator.getLineItemContent.calls.allArgs()).toEqual([
      ['session-1', 'line-stl'],
      ['session-1', 'line-3mf'],
      ['session-1', 'line-3mf', true],
    ]);
    expect(uploadForm.setPreviewFileByIndex).toHaveBeenCalledTimes(1);
    expect(uploadForm.setPreviewFileByIndex).toHaveBeenCalledWith(
      1,
      jasmine.any(File),
    );
  });

  it('skips binary session restore during SSR', () => {
    const { component, estimator, uploadForm } = createComponent('server');

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
      },
      [
        {
          id: 'line-stl',
          originalFilename: 'legacy.stl',
          quantity: 1,
        },
      ],
    );

    expect(estimator.getLineItemContent).not.toHaveBeenCalled();
    expect(uploadForm.setFiles).not.toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  });

  it('does not restore a quote session from query params during SSR', () => {
    const { component, estimator } = createComponent('server', {
      session: 'session-1',
    });

    component.ngOnInit();

    expect(estimator.getQuoteSession).not.toHaveBeenCalled();
  });

  it('restores a quote session from query params in the browser', () => {
    const { component, estimator } = createComponent('browser', {
      session: 'session-1',
    });

    estimator.getQuoteSession.and.returnValue(
      of({
        session: {
          id: 'session-1',
          status: 'ACTIVE',
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.2,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
        },
        items: [],
      }),
    );
    estimator.mapSessionToQuoteResult.and.returnValue(createResult('session-1'));

    component.ngOnInit();

    expect(estimator.getQuoteSession).toHaveBeenCalledWith('session-1');
    expect(component.result()?.sessionId).toBe('session-1');
    expect(component.loading()).toBeFalse();
  });

  it('applies a pending session restore after the upload form becomes available', () => {
    const { component, estimator } = createComponent();
    const originalBlob = new Blob(['original']);
    const previewBlob = new Blob(['preview']);

    component.uploadForm = undefined as unknown as UploadFormComponent;
    component.result.set(createResult('session-1'));
    component.error.set(true);

    estimator.getLineItemContent.and.callFake(
      (_sessionId: string, _lineItemId: string, preview = false) =>
        of(preview ? previewBlob : originalBlob),
    );

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
        materialCode: 'PLA',
        layerHeightMm: 0.2,
        nozzleDiameterMm: 0.4,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-3mf',
          originalFilename: 'converted.3mf',
          quantity: 2,
          colorCode: 'White',
          filamentVariantId: 7,
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.2,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
          convertedStoredPath: 'storage_quotes/session-1/converted.stl',
        },
      ],
    );

    const uploadForm = jasmine.createSpyObj<UploadFormComponent>(
      'UploadFormComponent',
      [
        'setFiles',
        'setPreviewFileByIndex',
        'patchSettings',
        'setItemPrintSettingsByIndex',
        'updateItemColor',
        'selectFile',
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
    component.ngAfterViewInit();

    expect(uploadForm.setFiles).toHaveBeenCalledTimes(1);
    expect(uploadForm.setFiles).toHaveBeenCalledWith(jasmine.any(Array), {
      autoSelect: false,
    });
    expect(uploadForm.setPreviewFileByIndex).toHaveBeenCalledWith(
      0,
      jasmine.any(File),
    );
    expect(uploadForm.patchSettings).toHaveBeenCalled();
    expect(uploadForm.updateItemQuantityByIndex).toHaveBeenCalledWith(0, 2);
    expect(uploadForm.updateItemColor).toHaveBeenCalledWith(0, {
      colorName: 'White',
      filamentVariantId: 7,
    });
    expect(uploadForm.selectFile).toHaveBeenCalledWith(jasmine.any(File));
    expect(component.error()).toBeFalse();
  });
});
