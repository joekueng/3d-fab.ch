import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import { QuoteEstimatorService, QuoteRequest, QuoteResult } from './services/quote-estimator.service';

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppCardComponent, AppAlertComponent, UploadFormComponent, QuoteResultComponent],
  template: `
    <div class="container hero">
      <h1>{{ 'CALC.TITLE' | translate }}</h1>
      <p class="subtitle">{{ 'CALC.SUBTITLE' | translate }}</p>
    </div>

    <div class="container content-grid">
      <!-- Left Column: Input -->
      <div class="col-input">
        <app-card>
          <div class="mode-selector">
            <div class="mode-option" 
                 [class.active]="mode() === 'easy'"
                 (click)="mode.set('easy')">
              {{ 'CALC.MODE_EASY' | translate }}
            </div>
            <div class="mode-option" 
                 [class.active]="mode() === 'advanced'"
                 (click)="mode.set('advanced')">
              {{ 'CALC.MODE_ADVANCED' | translate }}
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

        @if (loading()) {
            <app-card class="loading-state">
                <div class="spinner"></div>
                <p>Analisi geometria e slicing in corso...</p>
                <small class="text-muted">Potrebbe richiedere qualche secondo.</small>
            </app-card>
        } @else if (result()) {
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

    /* Mode Selector (Segmented Control style) */
    .mode-selector {
      display: flex;
      background-color: var(--color-neutral-100);
      border-radius: var(--radius-md);
      padding: 4px;
      margin-bottom: var(--space-6);
      gap: 4px;
      width: 100%;
    }
    
    .mode-option {
      flex: 1;
      text-align: center;
      padding: 8px 16px;
      border-radius: var(--radius-sm);
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--color-text-muted);
      transition: all 0.2s ease;
      user-select: none;
      
      &:hover { color: var(--color-text); }
      
      &.active {
        background-color: var(--color-brand);
        color: #000;
        font-weight: 600;
        box-shadow: 0 1px 2px rgba(0,0,0,0.05);
      }
    }

    .benefits { padding-left: var(--space-4); color: var(--color-text-muted); line-height: 2; }
    
    .loading-state {
        text-align: center;
        padding: var(--space-8);
        color: var(--color-text-muted);
        
        .spinner {
            border: 3px solid rgba(0, 0, 0, 0.1);
            border-left-color: var(--color-brand);
            border-radius: 50%;
            width: 32px;
            height: 32px;
            animation: spin 1s linear infinite;
            margin: 0 auto var(--space-4);
        }
    }
    
    @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
    }
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
