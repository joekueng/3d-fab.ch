import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AppTabsComponent } from '../../shared/components/app-tabs/app-tabs.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import { QuoteEstimatorService, QuoteRequest, QuoteResult } from './services/quote-estimator.service';

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppTabsComponent, AppCardComponent, AppAlertComponent, UploadFormComponent, QuoteResultComponent],
  template: `
    <div class="container hero">
      <h1>{{ 'CALC.TITLE' | translate }}</h1>
      <p class="subtitle">{{ 'CALC.SUBTITLE' | translate }}</p>
    </div>

    <div class="container content-grid">
      <!-- Left Column: Input -->
      <div class="col-input">
        <app-card>
          <div class="tabs-wrapper">
             <div class="sub-tabs">
                <span 
                  class="mode-switch" 
                  [class.active]="mode() === 'easy'"
                  (click)="mode.set('easy')">
                  {{ 'CALC.MODE_EASY' | translate }}
                </span>
                <span class="divider">/</span>
                <span 
                  class="mode-switch" 
                  [class.active]="mode() === 'advanced'"
                  (click)="mode.set('advanced')">
                  {{ 'CALC.MODE_ADVANCED' | translate }}
                </span>
             </div>
          </div>

          <app-upload-form
            [mode]="mode()"
            [loading]="loading()"
            (submitRequest)="onCalculate($event)"
          ></app-upload-form>
        </app-card>
      </div>

      <!-- Right Column: Result or Info -->
      <div class="col-result">
        @if (error()) {
          <app-alert type="error">Si è verificato un errore durante il calcolo del preventivo.</app-alert>
        }

        @if (result()) {
          <app-quote-result [result]="result()!"></app-quote-result>
        } @else {
          <app-card>
            <h3>{{ 'CALC.BENEFITS_TITLE' | translate }}</h3>
            <ul class="benefits">
               <li>{{ 'CALC.BENEFITS_1' | translate }}</li>
               <li>{{ 'CALC.BENEFITS_2' | translate }}</li>
               <li>{{ 'CALC.BENEFITS_3' | translate }}</li>
            </ul>
          </app-card>
        }
      </div>
    </div>
  `,
  styles: [`
    .hero { padding: var(--space-12) 0; text-align: center; }
    .subtitle { font-size: 1.25rem; color: var(--color-text-muted); max-width: 600px; margin: 0 auto; }
    
    .content-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: var(--space-8);
      @media(min-width: 768px) {
        grid-template-columns: 1.5fr 1fr;
      }
    }

    .tabs-wrapper {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: var(--space-6);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: var(--space-2);
    }
    
    .sub-tabs { font-size: 0.875rem; color: var(--color-text-muted); }
    .mode-switch { cursor: pointer; &:hover { color: var(--color-text); } }
    .mode-switch.active { font-weight: 700; color: var(--color-brand); }
    .divider { margin: 0 var(--space-2); }

    .benefits { padding-left: var(--space-4); color: var(--color-text-muted); line-height: 2; }
  `]
})
export class CalculatorPageComponent {
  mode = signal<any>('easy');
  loading = signal(false);
  result = signal<QuoteResult | null>(null);
  error = signal<boolean>(false);

  constructor(private estimator: QuoteEstimatorService) {}

  onCalculate(req: QuoteRequest) {
    this.loading.set(true);
    this.error.set(false);
    this.result.set(null);
    
    this.estimator.calculate(req).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      }
    });
  }
}
