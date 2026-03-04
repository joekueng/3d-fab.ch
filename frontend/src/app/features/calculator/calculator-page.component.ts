import {
  Component,
  computed,
  signal,
  ViewChild,
  ElementRef,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import {
  QuoteRequest,
  QuoteResult,
  QuoteEstimatorService,
} from './services/quote-estimator.service';
import { SuccessStateComponent } from '../../shared/components/success-state/success-state.component';
import { Router, ActivatedRoute } from '@angular/router';
import { LanguageService } from '../../core/services/language.service';

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AppCardComponent,
    AppAlertComponent,
    AppButtonComponent,
    UploadFormComponent,
    QuoteResultComponent,
    SuccessStateComponent,
  ],
  templateUrl: './calculator-page.component.html',
  styleUrl: './calculator-page.component.scss',
})
export class CalculatorPageComponent implements OnInit {
  mode = signal<any>('easy');
  step = signal<'upload' | 'quote' | 'details' | 'success'>('upload');

  loading = signal(false);
  uploadProgress = signal(0);
  result = signal<QuoteResult | null>(null);
  cadSessionLocked = signal(false);
  error = signal<boolean>(false);
  errorKey = signal<string>('CALC.ERROR_GENERIC');
  isZeroQuoteError = computed(
    () => this.error() && this.errorKey() === 'CALC.ERROR_ZERO_PRICE',
  );

  orderSuccess = signal(false);

  @ViewChild('uploadForm') uploadForm!: UploadFormComponent;
  @ViewChild('resultCol') resultCol!: ElementRef;

  constructor(
    private estimator: QuoteEstimatorService,
    private router: Router,
    private route: ActivatedRoute,
    private languageService: LanguageService,
  ) {}

  ngOnInit() {
    this.route.data.subscribe((data) => {
      if (data['mode']) {
        this.mode.set(data['mode']);
      }
    });

    this.route.queryParams.subscribe((params) => {
      const sessionId = params['session'];
      if (sessionId) {
        // Avoid reloading if we just calculated this session
        const currentRes = this.result();
        if (!currentRes || currentRes.sessionId !== sessionId) {
          this.loadSession(sessionId);
        }
      }
    });
  }

  loadSession(sessionId: string) {
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

        this.error.set(false);
        this.errorKey.set('CALC.ERROR_GENERIC');
        this.result.set(result);
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
    if (!items || items.length === 0) {
      this.loading.set(false);
      return;
    }

    // Download all files
    const downloads = items.map((item) =>
      forkJoin({
        originalBlob: this.estimator.getLineItemContent(session.id, item.id),
        previewBlob: this.estimator
          .getLineItemContent(session.id, item.id, true)
          .pipe(catchError(() => of(null))),
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
        const files = results.map(
          (res) =>
            new File([res.originalBlob], res.fileName, {
              type: 'application/octet-stream',
            }),
        );

        if (this.uploadForm) {
          this.uploadForm.setFiles(files);
          results.forEach((res, index) => {
            if (!res.hasConvertedPreview || !res.previewBlob) {
              return;
            }
            const previewName = res.fileName
              .replace(/\.[^.]+$/, '')
              .concat('.stl');
            const previewFile = new File([res.previewBlob], previewName, {
              type: 'model/stl',
            });
            this.uploadForm.setPreviewFileByIndex(index, previewFile);
          });
          this.uploadForm.patchSettings(session);

          // Also restore colors?
          // setFiles inits with 'Black'. We need to update them if they differ.
          // items has colorCode.
          setTimeout(() => {
            if (this.uploadForm) {
              items.forEach((item, index) => {
                // Assuming index matches.
                // Need to be careful if items order changed, but usually ID sort or insert order.
                if (item.colorCode) {
                  this.uploadForm.updateItemColor(index, {
                    colorName: item.colorCode,
                    filamentVariantId: item.filamentVariantId,
                  });
                }
              });
            }
          });
        }
        this.loading.set(false);
      },
      error: (err: any) => {
        console.error('Failed to download files', err);
        this.loading.set(false);
        // Still show result? Yes.
      },
    });
  }

  onCalculate(req: QuoteRequest) {
    // ... (logic remains the same, simplified for diff)
    this.currentRequest = req;
    this.loading.set(true);
    this.uploadProgress.set(0);
    this.error.set(false);
    this.errorKey.set('CALC.ERROR_GENERIC');
    this.result.set(null);
    this.cadSessionLocked.set(false);
    this.orderSuccess.set(false);

    // Auto-scroll on mobile to make analysis visible
    setTimeout(() => {
      if (this.resultCol && window.innerWidth < 768) {
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

          this.error.set(false);
          this.errorKey.set('CALC.ERROR_GENERIC');
          this.result.set(res);
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
      error: () => {
        this.setQuoteError('CALC.ERROR_GENERIC');
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
  }) {
    // 1. Update local form for consistency (UI feedback)
    if (this.uploadForm) {
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

                this.error.set(false);
                this.errorKey.set('CALC.ERROR_GENERIC');
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

  onSubmitOrder(orderData: any) {
    console.log('Order Submitted:', orderData);
    this.orderSuccess.set(true);
    this.step.set('success');
  }

  onNewQuote() {
    this.step.set('upload');
    this.result.set(null);
    this.cadSessionLocked.set(false);
    this.orderSuccess.set(false);
    this.mode.set('easy'); // Reset to default
  }

  private currentRequest: QuoteRequest | null = null;

  onConsult() {
    if (!this.currentRequest) {
      this.router.navigate([
        '/',
        this.languageService.selectedLang(),
        'contact',
      ]);
      return;
    }

    const req = this.currentRequest;
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

  private setQuoteError(key: string): void {
    this.errorKey.set(key);
    this.error.set(true);
    this.result.set(null);
  }
}
