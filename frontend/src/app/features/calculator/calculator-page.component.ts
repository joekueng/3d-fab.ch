import {
  AfterViewInit,
  Component,
  computed,
  signal,
  ViewChild,
  ElementRef,
  Inject,
  OnInit,
  Optional,
  PLATFORM_ID,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { BrandAnimationLogoComponent } from '../../shared/components/brand-animation-logo/brand-animation-logo.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import {
  PendingCalculatorDraft,
  QuoteCalculationFailure,
  QuoteRequest,
  QuoteResult,
  QuoteEstimatorService,
} from './services/quote-estimator.service';
import { SuccessStateComponent } from '../../shared/components/success-state/success-state.component';
import { Router, ActivatedRoute } from '@angular/router';
import { LanguageService } from '../../core/services/language.service';

type TrackedPrintSettings = {
  mode: 'easy' | 'advanced';
  material: string;
  quality: string;
  nozzleDiameter: number;
  layerHeight: number;
  infillDensity: number;
  infillPattern: string;
  supportEnabled: boolean;
};

type PendingSessionRestore = {
  session: any;
  items: any[];
  files: File[];
  previewFiles: Array<{
    index: number;
    file: File;
  }>;
};

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AppCardComponent,
    AppAlertComponent,
    AppButtonComponent,
    BrandAnimationLogoComponent,
    UploadFormComponent,
    QuoteResultComponent,
    SuccessStateComponent,
  ],
  templateUrl: './calculator-page.component.html',
  styleUrl: './calculator-page.component.scss',
})
export class CalculatorPageComponent implements OnInit, AfterViewInit {
  private readonly isBrowser: boolean;
  mode = signal<'easy' | 'advanced'>('easy');
  step = signal<'upload' | 'quote' | 'details' | 'success'>('upload');

  loading = signal(false);
  uploadProgress = signal(0);
  result = signal<QuoteResult | null>(null);
  cadSessionLocked = signal(false);
  error = signal<boolean>(false);
  errorKey = signal<string>('CALC.ERROR_GENERIC');
  errorMessage = signal<string | null>(null);
  warningMessage = signal<string | null>(null);
  showErrorAlert = computed(() => this.error() && !this.result());
  isZeroQuoteError = computed(
    () => this.error() && this.errorKey() === 'CALC.ERROR_ZERO_PRICE',
  );
  readonly faqIds = [
    'FILES',
    'MODE',
    'NO_MODEL',
    'PRICE',
    'BEFORE_UPLOAD',
  ] as const;
  readonly modelSources = [
    {
      id: 'PRINTABLES',
      label: 'Printables',
      url: 'https://www.printables.com',
    },
    {
      id: 'MAKERWORLD',
      label: 'MakerWorld',
      url: 'https://makerworld.com',
    },
    {
      id: 'THINGIVERSE',
      label: 'Thingiverse',
      url: 'https://www.thingiverse.com',
    },
    {
      id: 'THANGS',
      label: 'Thangs',
      url: 'https://thangs.com',
    },
    {
      id: 'CULTS3D',
      label: 'Cults3D',
      url: 'https://cults3d.com',
    },
    {
      id: 'YEGGI',
      label: 'Yeggi',
      url: 'https://www.yeggi.com',
    },
  ] as const;
  readonly favoriteModelSourceIds = ['PRINTABLES', 'MAKERWORLD'] as const;
  readonly favoriteModelSources = this.modelSources.filter((source) =>
    this.favoriteModelSourceIds.includes(
      source.id as (typeof this.favoriteModelSourceIds)[number],
    ),
  );
  readonly otherModelSources = this.modelSources.filter(
    (source) =>
      !this.favoriteModelSourceIds.includes(
        source.id as (typeof this.favoriteModelSourceIds)[number],
      ),
  );

  orderSuccess = signal(false);
  requiresRecalculation = signal(false);
  itemSettingsDiffByFileName = signal<
    Record<string, { differences: string[] }>
  >({});
  private baselinePrintSettings: TrackedPrintSettings | null = null;
  private baselineItemSettingsByFileName = new Map<
    string,
    TrackedPrintSettings
  >();
  private pendingSessionRestore: PendingSessionRestore | null = null;
  private isRestoringQuoteState = false;
  private quoteStateVersion = 0;
  private restoreDebugRun = 0;

  @ViewChild('uploadForm') uploadForm!: UploadFormComponent;
  @ViewChild('resultCol') resultCol!: ElementRef;

  constructor(
    private estimator: QuoteEstimatorService,
    private router: Router,
    private route: ActivatedRoute,
    private languageService: LanguageService,
    @Optional() @Inject(PLATFORM_ID) platformId?: Object,
  ) {
    this.isBrowser = isPlatformBrowser(platformId ?? 'browser');
  }

  ngOnInit() {
    this.route.data.subscribe((data) => {
      if (data['mode']) {
        this.mode.set(data['mode']);
      }
    });

    if (!this.isBrowser) {
      return;
    }

    this.route.queryParams.subscribe((params) => {
      const sessionId = params['session'];
      if (sessionId) {
        // Avoid reloading if we just calculated this session
        const currentRes = this.result();
        if (!currentRes || currentRes.sessionId !== sessionId) {
          this.loadSession(sessionId);
        } else {
          this.clearQuoteErrorState();
          this.applyPendingSessionRestoreIfNeeded();
        }
      }
    });
  }

  ngAfterViewInit() {
    this.applyPendingSessionRestoreIfNeeded();

    const pendingDraft = this.estimator.consumePendingCalculatorDraft();
    if (!pendingDraft || this.currentSessionId()) {
      return;
    }

    this.uploadForm?.restoreRequestDraft(pendingDraft.request, {
      sameSettingsForAll: pendingDraft.sameSettingsForAll,
      selectedFileName: pendingDraft.selectedFileName,
    });
  }

  modeContentKey(field: string): string {
    const modeKey = this.mode() === 'easy' ? 'BASIC' : 'ADVANCED';
    return `CALC.MODES.${modeKey}.${field}`;
  }

  modelSourceDescriptionKey(id: string): string {
    return `CALC.MODEL_SOURCES.ITEMS.${id}`;
  }

  faqKey(id: string, field: string): string {
    return `CALC.FAQ.ITEMS.${id}.${field}`;
  }

  loadSession(sessionId: string) {
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.loading.set(true);
    this.estimator.getQuoteSession(sessionId).subscribe({
      next: (data) => {
        // 1. Map to Result
        const result = this.estimator.mapSessionToQuoteResult(data);
        if (this.isInvalidQuote(result)) {
          this.setQuoteError('CALC.ERROR_ZERO_PRICE');
          this.loading.set(false);
          return;
        }

        this.clearQuoteErrorState();
        this.warningMessage.set(null);
        this.result.set(result);
        this.baselinePrintSettings = this.toTrackedSettingsFromSession(
          data.session,
        );
        this.baselineItemSettingsByFileName = this.buildBaselineMapFromSession(
          data.items || [],
          this.baselinePrintSettings,
        );
        this.requiresRecalculation.set(false);
        this.itemSettingsDiffByFileName.set({});
        const isCadSession = data?.session?.status === 'CAD_ACTIVE';
        this.cadSessionLocked.set(isCadSession);
        this.step.set('quote');

        // 2. Determine Mode (Heuristic)
        // If we have custom settings, maybe Advanced?
        // For now, let's stick to current mode or infer from URL if possible.
        // Actually, we can check if settings deviate from Easy defaults.
        // But let's leave it as is or default to Advanced if not sure.
        // data.session.materialCode etc.

        // 3. Download Files & Restore Form
        this.restoreFilesAndSettings(data.session, data.items);
      },
      error: (err) => {
        console.error('Failed to load session', err);
        this.setQuoteError('CALC.ERROR_GENERIC');
        this.loading.set(false);
      },
    });
  }

  restoreFilesAndSettings(session: any, items: any[]) {
    const restoreStateVersion = this.quoteStateVersion;
    if (!items || items.length === 0) {
      this.loading.set(false);
      return;
    }

    // File restore is client-only: SSR can render the quote summary, but binary
    // session content must be fetched fresh in the browser to avoid transfer
    // cache serializing blobs into unusable objects during hydration.
    if (!this.isBrowser) {
      this.loading.set(false);
      return;
    }

    const restoreRun = ++this.restoreDebugRun;
    console.debug('[restoreFilesAndSettings:start]', {
      restoreRun,
      sessionId: session?.id,
      itemIds: items.map((item) => item?.id),
      fileNames: items.map((item) => item?.originalFilename),
    });

    // Download all files
    const downloads = items.map((item) =>
      forkJoin({
        originalBlob: this.estimator.getLineItemContent(session.id, item.id),
        previewBlob: item?.convertedStoredPath
          ? this.estimator
              .getLineItemContent(session.id, item.id, true)
              .pipe(catchError(() => of(null)))
          : of(null),
      }).pipe(
        map(({ originalBlob, previewBlob }) => {
          return {
            originalBlob,
            previewBlob,
            fileName: item.originalFilename,
            hasConvertedPreview: !!item.convertedStoredPath,
          };
        }),
      ),
    );

    forkJoin(downloads).subscribe({
      next: (results: any[]) => {
        if (
          restoreStateVersion !== this.quoteStateVersion ||
          !this.isCurrentSessionRestore(session)
        ) {
          if (
            restoreStateVersion !== this.quoteStateVersion &&
            this.isCurrentSessionRestore(session)
          ) {
            this.loading.set(false);
          }
          return;
        }

        console.debug('[restoreFilesAndSettings:downloaded]', {
          restoreRun,
          sessionId: session?.id,
          blobs: results.map((res) => ({
            fileName: res.fileName,
            originalBlobSize: res.originalBlob?.size ?? null,
            previewBlobSize: res.previewBlob?.size ?? null,
            hasConvertedPreview: res.hasConvertedPreview,
          })),
        });
        const files = results.map(
          (res) =>
            new File([res.originalBlob], res.fileName, {
              type: 'application/octet-stream',
            }),
        );

        console.debug('[restoreFilesAndSettings:files-built]', {
          restoreRun,
          sessionId: session?.id,
          files: files.map((file) => ({
            name: file.name,
            size: file.size,
          })),
        });

        const previewFiles = results.flatMap((res, index) => {
          if (!res.hasConvertedPreview || !res.previewBlob) {
            return [];
          }

          const previewName = res.fileName
            .replace(/\.[^.]+$/, '')
            .concat('.stl');
          const previewFile = new File([res.previewBlob], previewName, {
            type: 'model/stl',
          });
          return [{ index, file: previewFile }];
        });

        this.pendingSessionRestore = {
          session,
          items,
          files,
          previewFiles,
        };
        this.applyPendingSessionRestoreIfNeeded();
        this.loading.set(false);
      },
      error: (err: any) => {
        if (restoreStateVersion !== this.quoteStateVersion) {
          if (this.isCurrentSessionRestore(session)) {
            this.loading.set(false);
          }
          return;
        }
        console.error('Failed to download files', err);
        this.loading.set(false);
        // Still show result? Yes.
      },
    });
  }

  onCalculate(req: QuoteRequest) {
    // ... (logic remains the same, simplified for diff)
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.currentRequest = req;
    this.loading.set(true);
    this.uploadProgress.set(0);
    this.clearQuoteErrorState();
    this.warningMessage.set(null);
    this.result.set(null);
    this.cadSessionLocked.set(false);
    this.orderSuccess.set(false);

    // Auto-scroll on mobile to make analysis visible
    setTimeout(() => {
      if (this.isBrowser && this.resultCol && window.innerWidth < 768) {
        this.resultCol.nativeElement.scrollIntoView({
          behavior: 'smooth',
          block: 'start',
        });
      }
    }, 100);

    this.estimator.calculate(req).subscribe({
      next: (event) => {
        if (typeof event === 'number') {
          this.uploadProgress.set(event);
        } else {
          // It's the result
          const res = event as QuoteResult;
          if (this.isInvalidQuote(res)) {
            this.setQuoteError('CALC.ERROR_ZERO_PRICE');
            this.loading.set(false);
            return;
          }

          this.clearQuoteErrorState();
          this.warningMessage.set(
            this.buildPartialFailureMessage(res.failedItems || []),
          );
          this.result.set(res);
          this.baselinePrintSettings = this.toTrackedSettingsFromRequest(req);
          this.baselineItemSettingsByFileName =
            this.buildBaselineMapFromRequest(req);
          this.requiresRecalculation.set(false);
          this.itemSettingsDiffByFileName.set({});
          this.loading.set(false);
          this.uploadProgress.set(100);
          this.step.set('quote');

          // Update URL with session ID without reloading
          if (res.sessionId) {
            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { session: res.sessionId },
              queryParamsHandling: 'merge', // merge with existing params like 'mode' if any
              replaceUrl: true, // prevent cluttering history, or false if we want back button to work. replaceUrl seems safer for "state update"
            });
            this.estimator.getQuoteSession(res.sessionId).subscribe({
              next: (sessionData) => {
                this.restoreFilesAndSettings(
                  sessionData.session,
                  sessionData.items || [],
                );
              },
              error: (err) => {
                console.warn('Failed to refresh files for preview', err);
              },
            });
          }
        }
      },
      error: (err) => {
        const failure = this.normalizeCalculationFailure(err);
        this.setQuoteError('CALC.ERROR_GENERIC', failure?.message || null);
        this.loading.set(false);
      },
    });
  }

  onProceed() {
    const res = this.result();
    if (res && res.sessionId) {
      const segments = this.cadSessionLocked()
        ? ['/', this.languageService.selectedLang(), 'checkout', 'cad']
        : ['/', this.languageService.selectedLang(), 'checkout'];
      this.router.navigate(segments, {
        queryParams: { session: res.sessionId },
      });
    } else {
      console.error('No session ID found in quote result');
      // Fallback or error handling
    }
  }

  onCancelDetails() {
    this.step.set('quote');
  }

  onItemChange(event: {
    id?: string;
    index: number;
    fileName: string;
    quantity: number;
    source?: 'left' | 'right';
  }) {
    // 1. Update local form for consistency (UI feedback)
    if (event.source !== 'left' && this.uploadForm) {
      this.uploadForm.updateItemQuantityByIndex(event.index, event.quantity);
      this.uploadForm.updateItemQuantityByName(event.fileName, event.quantity);
    }

    // 2. Update backend session if ID exists
    if (event.id) {
      const currentSessionId = this.result()?.sessionId;
      if (!currentSessionId) return;

      this.estimator
        .updateLineItem(event.id, { quantity: event.quantity })
        .subscribe({
          next: () => {
            // 3. Fetch the updated session totals from the backend
            this.estimator.getQuoteSession(currentSessionId).subscribe({
              next: (sessionData) => {
                const newResult =
                  this.estimator.mapSessionToQuoteResult(sessionData);
                // Preserve notes
                newResult.notes = this.result()?.notes;

                if (this.isInvalidQuote(newResult)) {
                  this.setQuoteError('CALC.ERROR_ZERO_PRICE');
                  return;
                }

                this.clearQuoteErrorState();
                this.result.set(newResult);
              },
              error: (err) => {
                console.error('Failed to refresh session totals', err);
              },
            });
          },
          error: (err) => {
            console.error('Failed to update line item', err);
          },
        });
    }
  }

  onUploadItemQuantityChange(event: {
    index: number;
    fileName: string;
    quantity: number;
  }) {
    const resultItems = this.result()?.items || [];
    const byIndex = resultItems[event.index];
    const byName = resultItems.find((item) => item.fileName === event.fileName);
    const id = byIndex?.id ?? byName?.id;

    this.onItemChange({
      ...event,
      id,
      source: 'left',
    });
  }

  onQuoteItemQuantityPreviewChange(event: {
    index: number;
    fileName: string;
    quantity: number;
  }) {
    if (!this.uploadForm) return;
    this.uploadForm.updateItemQuantityByIndex(event.index, event.quantity);
    this.uploadForm.updateItemQuantityByName(event.fileName, event.quantity);
  }

  onSubmitOrder(orderData: any) {
    console.log('Order Submitted:', orderData);
    this.orderSuccess.set(true);
    this.step.set('success');
  }

  onNewQuote() {
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.step.set('upload');
    this.result.set(null);
    this.requiresRecalculation.set(false);
    this.itemSettingsDiffByFileName.set({});
    this.baselinePrintSettings = null;
    this.baselineItemSettingsByFileName = new Map<
      string,
      TrackedPrintSettings
    >();
    this.cadSessionLocked.set(false);
    this.orderSuccess.set(false);
    this.switchMode('easy'); // Reset to default and sync URL
  }

  private currentRequest: QuoteRequest | null = null;

  onUploadPrintSettingsChange(_: TrackedPrintSettings) {
    void _;
    if (this.isRestoringQuoteState) return;
    if (!this.result()) return;
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.refreshRecalculationRequirement();
  }

  onItemSettingsDiffChange(
    diffByFileName: Record<string, { differences: string[] }>,
  ) {
    this.itemSettingsDiffByFileName.set(diffByFileName || {});
  }

  onConsult() {
    const currentFormRequest = this.uploadForm?.getCurrentRequestDraft();
    const req = currentFormRequest ?? this.currentRequest;

    if (!req) {
      this.router.navigate([
        '/',
        this.languageService.selectedLang(),
        'contact',
      ]);
      return;
    }

    let details = `Richiesta Preventivo:\n`;
    details += `- Materiale: ${req.material}\n`;
    details += `- Qualità: ${req.quality}\n`;

    details += `- File:\n`;
    req.items.forEach((item) => {
      details += `  * ${item.file.name} (Qtà: ${item.quantity}`;
      if (item.color) {
        details += `, Colore: ${item.color}`;
      }
      details += `)\n`;
    });

    if (req.mode === 'advanced') {
      if (req.infillDensity) details += `- Infill: ${req.infillDensity}%\n`;
    }

    if (req.notes) details += `\nNote: ${req.notes}`;

    this.estimator.setPendingConsultation({
      files: req.items.map((i) => i.file),
      message: details,
    });

    this.router.navigate(['/', this.languageService.selectedLang(), 'contact']);
  }

  private isInvalidQuote(result: QuoteResult): boolean {
    const invalidPrice =
      !Number.isFinite(result.totalPrice) || result.totalPrice <= 0;
    const invalidWeight =
      !Number.isFinite(result.totalWeight) || result.totalWeight <= 0;
    const invalidTime =
      !Number.isFinite(result.totalTimeHours) ||
      !Number.isFinite(result.totalTimeMinutes) ||
      (result.totalTimeHours <= 0 && result.totalTimeMinutes <= 0);

    return invalidPrice || invalidWeight || invalidTime;
  }

  private setQuoteError(key: string, message: string | null = null): void {
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.errorKey.set(key);
    this.errorMessage.set(message);
    this.warningMessage.set(null);
    this.error.set(true);
    this.result.set(null);
    this.requiresRecalculation.set(false);
    this.itemSettingsDiffByFileName.set({});
    this.baselinePrintSettings = null;
    this.baselineItemSettingsByFileName = new Map<
      string,
      TrackedPrintSettings
    >();
  }

  private clearQuoteErrorState(): void {
    this.error.set(false);
    this.errorKey.set('CALC.ERROR_GENERIC');
    this.errorMessage.set(null);
  }

  private normalizeCalculationFailure(
    error: unknown,
  ): QuoteCalculationFailure | null {
    if (!error || typeof error !== 'object') {
      return null;
    }

    const maybeFailure = error as Partial<QuoteCalculationFailure>;
    if (
      typeof maybeFailure.message === 'string' &&
      maybeFailure.message.trim().length > 0
    ) {
      return {
        fileName:
          typeof maybeFailure.fileName === 'string'
            ? maybeFailure.fileName
            : '',
        status:
          typeof maybeFailure.status === 'number'
            ? maybeFailure.status
            : undefined,
        code:
          typeof maybeFailure.code === 'string' ? maybeFailure.code : undefined,
        message: maybeFailure.message.trim(),
      };
    }

    return null;
  }

  private buildPartialFailureMessage(
    failures: QuoteCalculationFailure[],
  ): string | null {
    if (!failures.length) {
      return null;
    }

    if (failures.length === 1) {
      const failure = failures[0];
      return `${failure.fileName} was not included in the quote. ${failure.message}`;
    }

    const fileNames = failures
      .map((failure) => failure.fileName)
      .filter((fileName) => fileName.trim().length > 0);
    const sharedMessage = failures.every(
      (failure) => failure.message === failures[0].message,
    )
      ? failures[0].message
      : null;

    let message = `${failures.length} files were not included in the quote`;
    if (fileNames.length > 0) {
      message += `: ${fileNames.join(', ')}`;
    }
    message += '.';
    if (sharedMessage) {
      message += ` ${sharedMessage}`;
    }
    return message;
  }

  switchMode(nextMode: 'easy' | 'advanced'): void {
    if (this.cadSessionLocked()) return;

    const targetPath = nextMode === 'easy' ? 'basic' : 'advanced';
    const currentPath = this.route.snapshot?.routeConfig?.path;

    this.mode.set(nextMode);

    if (currentPath === targetPath) {
      return;
    }

    if (!this.currentSessionId()) {
      this.persistPendingDraftForModeSwitch();
    }

    this.router.navigate(['..', targetPath], {
      relativeTo: this.route,
      queryParamsHandling: 'preserve',
    });
  }

  private currentSessionId(): string | null {
    const fromResult = this.result()?.sessionId;
    if (fromResult) {
      return fromResult;
    }

    const snapshot = this.route.snapshot;
    const fromQueryParamMap = snapshot?.queryParamMap?.get?.('session');
    if (fromQueryParamMap) {
      return fromQueryParamMap;
    }

    const fromQueryParams = snapshot?.queryParams?.['session'];
    return typeof fromQueryParams === 'string' && fromQueryParams.length > 0
      ? fromQueryParams
      : null;
  }

  private persistPendingDraftForModeSwitch(): void {
    if (!this.uploadForm) {
      this.estimator.setPendingCalculatorDraft(null);
      return;
    }

    const request = this.uploadForm.getCurrentRequestDraft();
    if (!request.items.length) {
      this.estimator.setPendingCalculatorDraft(null);
      return;
    }

    const draft: PendingCalculatorDraft = {
      request,
      sameSettingsForAll: this.uploadForm.sameSettingsForAll(),
      selectedFileName: this.uploadForm.selectedFile()?.name ?? null,
    };
    this.estimator.setPendingCalculatorDraft(draft);
  }

  private applyPendingSessionRestoreIfNeeded(): void {
    if (!this.uploadForm || !this.pendingSessionRestore) {
      return;
    }

    const payload = this.pendingSessionRestore;
    if (!this.isCurrentSessionRestore(payload.session)) {
      this.pendingSessionRestore = null;
      return;
    }

    console.debug('[applyPendingSessionRestoreIfNeeded]', {
      restoreRun: this.restoreDebugRun,
      sessionId: payload.session?.id,
      files: payload.files.map((file) => ({
        name: file.name,
        size: file.size,
      })),
    });
    const baselineSessionSettings = this.toTrackedSettingsFromSession(
      payload.session,
    );

    this.isRestoringQuoteState = true;
    try {
      this.uploadForm.setFiles(payload.files, { autoSelect: false });
      payload.previewFiles.forEach((preview) => {
        this.uploadForm.setPreviewFileByIndex(preview.index, preview.file);
      });
      this.uploadForm.patchSettings(payload.session);

      payload.items.forEach((item, index) => {
        // Preserve persisted quantities when restoring from session.
        // Without this, setFiles() defaults every item back to 1.
        this.uploadForm.updateItemQuantityByIndex(
          index,
          Number(item.quantity || 1),
        );

        const tracked = this.toTrackedSettingsFromSessionItem(
          item,
          baselineSessionSettings,
        );
        this.uploadForm.setItemPrintSettingsByIndex(index, {
          material: tracked.material.toUpperCase(),
          quality: tracked.quality,
          nozzleDiameter: tracked.nozzleDiameter,
          layerHeight: tracked.layerHeight,
          infillDensity: tracked.infillDensity,
          infillPattern: tracked.infillPattern,
          supportEnabled: tracked.supportEnabled,
        });

        if (item.colorCode) {
          this.uploadForm.updateItemColor(index, {
            colorName: item.colorCode,
            filamentVariantId: item.filamentVariantId,
          });
        }
      });

      const selected = payload.files[payload.files.length - 1] ?? null;
      if (selected) {
        this.uploadForm.selectFile(selected);
      }
    } finally {
      this.isRestoringQuoteState = false;
    }

    this.baselinePrintSettings = baselineSessionSettings;
    this.baselineItemSettingsByFileName = this.buildBaselineMapFromSession(
      payload.items || [],
      baselineSessionSettings,
    );
    this.requiresRecalculation.set(false);
    this.refreshRecalculationRequirement();
    this.clearQuoteErrorState();
    this.pendingSessionRestore = null;
  }

  private toTrackedSettingsFromRequest(
    req: QuoteRequest,
  ): TrackedPrintSettings {
    return {
      mode: req.mode,
      material: this.normalizeString(req.material || 'PLA'),
      quality: this.normalizeString(req.quality || 'standard'),
      nozzleDiameter: this.normalizeNumber(req.nozzleDiameter, 0.4, 2),
      layerHeight: this.normalizeNumber(req.layerHeight, 0.2, 3),
      infillDensity: this.normalizeNumber(req.infillDensity, 20, 2),
      infillPattern: this.normalizeString(req.infillPattern || 'grid'),
      supportEnabled: Boolean(req.supportEnabled),
    };
  }

  private toTrackedSettingsFromItem(
    req: QuoteRequest,
    item: QuoteRequest['items'][number],
  ): TrackedPrintSettings {
    return {
      mode: req.mode,
      material: this.normalizeString(item.material || req.material || 'PLA'),
      quality: this.normalizeString(item.quality || req.quality || 'standard'),
      nozzleDiameter: this.normalizeNumber(
        item.nozzleDiameter ?? req.nozzleDiameter,
        0.4,
        2,
      ),
      layerHeight: this.normalizeNumber(
        item.layerHeight ?? req.layerHeight,
        0.2,
        3,
      ),
      infillDensity: this.normalizeNumber(
        item.infillDensity ?? req.infillDensity,
        20,
        2,
      ),
      infillPattern: this.normalizeString(
        item.infillPattern || req.infillPattern || 'grid',
      ),
      supportEnabled: Boolean(item.supportEnabled ?? req.supportEnabled),
    };
  }

  private toTrackedSettingsFromSession(session: any): TrackedPrintSettings {
    const layer = this.normalizeNumber(session?.layerHeightMm, 0.2, 3);
    return {
      mode: this.mode(),
      material: this.normalizeString(session?.materialCode || 'PLA'),
      quality:
        layer >= 0.24 ? 'draft' : layer <= 0.12 ? 'extra_fine' : 'standard',
      nozzleDiameter: this.normalizeNumber(session?.nozzleDiameterMm, 0.4, 2),
      layerHeight: layer,
      infillDensity: this.normalizeNumber(session?.infillPercent, 20, 2),
      infillPattern: this.normalizeString(session?.infillPattern || 'grid'),
      supportEnabled: Boolean(session?.supportsEnabled),
    };
  }

  private toTrackedSettingsFromSessionItem(
    item: any,
    fallback: TrackedPrintSettings,
  ): TrackedPrintSettings {
    const layer = this.normalizeNumber(
      item?.layerHeightMm,
      fallback.layerHeight,
      3,
    );
    return {
      mode: this.mode(),
      material: this.normalizeString(item?.materialCode || fallback.material),
      quality: this.normalizeString(
        item?.quality ||
          (layer >= 0.24 ? 'draft' : layer <= 0.12 ? 'extra_fine' : 'standard'),
      ),
      nozzleDiameter: this.normalizeNumber(
        item?.nozzleDiameterMm,
        fallback.nozzleDiameter,
        2,
      ),
      layerHeight: layer,
      infillDensity: this.normalizeNumber(
        item?.infillPercent,
        fallback.infillDensity,
        2,
      ),
      infillPattern: this.normalizeString(
        item?.infillPattern || fallback.infillPattern,
      ),
      supportEnabled: Boolean(item?.supportsEnabled ?? fallback.supportEnabled),
    };
  }

  private buildBaselineMapFromRequest(
    req: QuoteRequest,
  ): Map<string, TrackedPrintSettings> {
    const map = new Map<string, TrackedPrintSettings>();
    req.items.forEach((item) => {
      map.set(
        this.normalizeFileName(item.file?.name || ''),
        this.toTrackedSettingsFromItem(req, item),
      );
    });
    return map;
  }

  private buildBaselineMapFromSession(
    items: any[],
    defaultSettings: TrackedPrintSettings | null,
  ): Map<string, TrackedPrintSettings> {
    const map = new Map<string, TrackedPrintSettings>();
    const fallback = defaultSettings ?? this.defaultTrackedSettings();
    items.forEach((item) => {
      map.set(
        this.normalizeFileName(item?.originalFilename || ''),
        this.toTrackedSettingsFromSessionItem(item, fallback),
      );
    });
    return map;
  }

  private defaultTrackedSettings(): TrackedPrintSettings {
    return {
      mode: this.mode(),
      material: 'pla',
      quality: 'standard',
      nozzleDiameter: 0.4,
      layerHeight: 0.2,
      infillDensity: 20,
      infillPattern: 'grid',
      supportEnabled: false,
    };
  }

  private refreshRecalculationRequirement(): void {
    if (!this.result()) return;

    const draft = this.uploadForm?.getCurrentRequestDraft();
    if (!draft || draft.items.length === 0) {
      this.requiresRecalculation.set(false);
      return;
    }

    const fallback = this.baselinePrintSettings;
    if (!fallback) {
      this.requiresRecalculation.set(false);
      return;
    }

    const changed = draft.items.some((item) => {
      const key = this.normalizeFileName(item.file?.name || '');
      const baseline = this.baselineItemSettingsByFileName.get(key) || fallback;
      const current = this.toTrackedSettingsFromItem(draft, item);
      return !this.sameTrackedSettings(baseline, current);
    });

    this.requiresRecalculation.set(changed);
  }

  private sameTrackedSettings(
    a: TrackedPrintSettings,
    b: TrackedPrintSettings,
  ): boolean {
    return (
      a.mode === b.mode &&
      a.material === this.normalizeString(b.material) &&
      a.quality === this.normalizeString(b.quality) &&
      Math.abs(
        a.nozzleDiameter - this.normalizeNumber(b.nozzleDiameter, 0.4, 2),
      ) < 0.0001 &&
      Math.abs(a.layerHeight - this.normalizeNumber(b.layerHeight, 0.2, 3)) <
        0.0001 &&
      Math.abs(a.infillDensity - this.normalizeNumber(b.infillDensity, 20, 2)) <
        0.0001 &&
      a.infillPattern === this.normalizeString(b.infillPattern) &&
      a.supportEnabled === Boolean(b.supportEnabled)
    );
  }

  private normalizeFileName(fileName: string): string {
    return (fileName || '').split(/[\\/]/).pop()?.trim().toLowerCase() ?? '';
  }

  private normalizeString(value: string): string {
    return String(value || '')
      .trim()
      .toLowerCase();
  }

  private normalizeNumber(
    value: unknown,
    fallback: number,
    decimals: number,
  ): number {
    const numeric = Number(value);
    const resolved = Number.isFinite(numeric) ? numeric : fallback;
    const factor = 10 ** decimals;
    return Math.round(resolved * factor) / factor;
  }

  private isCurrentSessionRestore(session: any): boolean {
    const restoreSessionId = String(session?.id || '');
    const currentResultSessionId = String(this.result()?.sessionId || '');
    return (
      restoreSessionId.length > 0 &&
      currentResultSessionId.length > 0 &&
      restoreSessionId === currentResultSessionId
    );
  }
}
