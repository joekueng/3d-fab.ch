import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AppCardComponent } from '../../../../shared/components/app-card/app-card.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { QuoteResult } from '../../services/quote-estimator.service';

@Component({
  selector: 'app-quote-result',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppCardComponent, AppButtonComponent],
  template: `
    <app-card>
      <h3 class="title">{{ 'CALC.RESULT' | translate }}</h3>
      
      <div class="result-grid">
        <div class="item">
          <span class="label">{{ 'CALC.COST' | translate }}</span>
          <span class="value price">{{ result().price | currency:result().currency }}</span>
        </div>
        <div class="item">
          <span class="label">{{ 'CALC.TIME' | translate }}</span>
          <span class="value">{{ result().printTimeHours }}h</span>
        </div>
        <div class="item">
          <span class="label">Material</span>
          <span class="value">{{ result().materialUsageGrams }}g</span>
        </div>
      </div>

      <div class="actions">
        <app-button variant="primary" [fullWidth]="true">{{ 'CALC.ORDER' | translate }}</app-button>
        <app-button variant="outline" [fullWidth]="true">{{ 'CALC.CONSULT' | translate }}</app-button>
      </div>
    </app-card>
  `,
  styles: [`
    .title { margin-bottom: var(--space-6); text-align: center; }
    .result-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-4);
      margin-bottom: var(--space-6);
    }
    .item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: var(--space-3);
      background: var(--color-neutral-50);
      border-radius: var(--radius-md);
    }
    .item:first-child { grid-column: span 2; background: var(--color-neutral-100); }
    .label { font-size: 0.875rem; color: var(--color-text-muted); }
    .value { font-size: 1.25rem; font-weight: 700; }
    .price { font-size: 2rem; color: var(--color-brand); }
    
    .actions { display: flex; flex-direction: column; gap: var(--space-3); }
  `]
})
export class QuoteResultComponent {
  result = input.required<QuoteResult>();
}
