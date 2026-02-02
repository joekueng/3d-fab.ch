import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AppCardComponent } from '../../../../shared/components/app-card/app-card.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { SummaryCardComponent } from '../../../../shared/components/summary-card/summary-card.component';
import { QuoteResult } from '../../services/quote-estimator.service';

@Component({
  selector: 'app-quote-result',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppCardComponent, AppButtonComponent, SummaryCardComponent],
  template: `
    <app-card>
      <h3 class="title">{{ 'CALC.RESULT' | translate }}</h3>
      
      <div class="result-grid">
        <app-summary-card 
          class="item full-width" 
          [label]="'CALC.COST' | translate" 
          [large]="true" 
          [highlight]="true">
          {{ result().price | currency:result().currency }}
        </app-summary-card>

        <app-summary-card [label]="'CALC.TIME' | translate">
          {{ result().printTimeHours }}h
        </app-summary-card>

        <app-summary-card [label]="'CALC.MATERIAL' | translate">
          {{ result().materialUsageGrams }}g
        </app-summary-card>
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
    .full-width { grid-column: span 2; }
    
    .actions { display: flex; flex-direction: column; gap: var(--space-3); }
  `]
})
export class QuoteResultComponent {
  result = input.required<QuoteResult>();
}
