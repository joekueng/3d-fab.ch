import { Component, input, output, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppCardComponent } from '../../../../shared/components/app-card/app-card.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { SummaryCardComponent } from '../../../../shared/components/summary-card/summary-card.component';
import { QuoteResult, QuoteItem } from '../../services/quote-estimator.service';

@Component({
  selector: 'app-quote-result',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, AppCardComponent, AppButtonComponent, SummaryCardComponent],
  template: `
    <app-card>
      <h3 class="title">{{ 'CALC.RESULT' | translate }}</h3>
      
      <!-- Detailed Items List -->
      <div class="items-list">
        @for (item of items(); track item.fileName; let i = $index) {
          <div class="item-row">
            <div class="item-info">
              <span class="file-name">{{ item.fileName }}</span>
              <span class="file-details">
                 {{ (item.unitTime / 3600) | number:'1.1-1' }}h | {{ item.unitWeight | number:'1.0-0' }}g
              </span>
            </div>
            
            <div class="item-controls">
                <div class="qty-control">
                    <label>Qtà:</label>
                    <input 
                        type="number" 
                        min="1" 
                        [ngModel]="item.quantity" 
                        (ngModelChange)="updateQuantity(i, $event)"
                        class="qty-input">
                </div>
                <div class="item-price">
                    {{ (item.unitPrice * item.quantity) | currency:result().currency }}
                </div>
            </div>
          </div>
        }
      </div>

      <div class="divider"></div>
      
      <!-- Summary Grid -->
      <div class="result-grid">
        <app-summary-card 
          class="item full-width" 
          [label]="'CALC.COST' | translate" 
          [large]="true" 
          [highlight]="true">
          {{ totals().price | currency:result().currency }}
        </app-summary-card>

        <app-summary-card [label]="'CALC.TIME' | translate">
          {{ totals().hours }}h {{ totals().minutes }}m
        </app-summary-card>

        <app-summary-card [label]="'CALC.MATERIAL' | translate">
          {{ totals().weight }}g
        </app-summary-card>
      </div>
      
      <div class="setup-note">
        <small>* Include {{ result().setupCost | currency:result().currency }} Setup Cost</small>
      </div>

      <div class="actions">
        <app-button variant="primary" [fullWidth]="true">{{ 'CALC.ORDER' | translate }}</app-button>
        <app-button variant="outline" [fullWidth]="true" (click)="consult.emit()">{{ 'CALC.CONSULT' | translate }}</app-button>
      </div>
    </app-card>
  `,
  styles: [`
    .title { margin-bottom: var(--space-6); text-align: center; }
    
    .divider { 
        height: 1px; 
        background: var(--color-border); 
        margin: var(--space-4) 0;
    }

    .items-list {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        margin-bottom: var(--space-4);
    }
    
    .item-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--space-3);
        background: var(--color-neutral-50);
        border-radius: var(--radius-md);
        border: 1px solid var(--color-border);
    }
    
    .item-info {
        display: flex;
        flex-direction: column;
    }
    
    .file-name { font-weight: 500; font-size: 0.9rem; color: var(--color-text); }
    .file-details { font-size: 0.8rem; color: var(--color-text-muted); }

    .item-controls {
        display: flex;
        align-items: center;
        gap: var(--space-4);
    }
    
    .qty-control {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        
        label { font-size: 0.8rem; color: var(--color-text-muted); }
    }
    
    .qty-input {
        width: 60px;
        padding: 4px 8px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        text-align: center;
        &:focus { outline: none; border-color: var(--color-brand); }
    }
    
    .item-price {
        font-weight: 600;
        min-width: 60px;
        text-align: right;
    }

    .result-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-4);
      margin-bottom: var(--space-2);
    }
    .full-width { grid-column: span 2; }
    
    .setup-note {
        text-align: center;
        margin-bottom: var(--space-6);
        color: var(--color-text-muted);
        font-size: 0.8rem;
    }
    
    .actions { display: flex; flex-direction: column; gap: var(--space-3); }
  `]
})
export class QuoteResultComponent {
  result = input.required<QuoteResult>();
  consult = output<void>();

  // Local mutable state for items to handle quantity changes
  items = signal<QuoteItem[]>([]);

  constructor() {
      effect(() => {
          // Initialize local items when result inputs change
          // We map to new objects to avoid mutating the input directly if it was a reference
          this.items.set(this.result().items.map(i => ({...i})));
      }, { allowSignalWrites: true });
  }

  updateQuantity(index: number, newQty: number | string) {
      const qty = typeof newQty === 'string' ? parseInt(newQty, 10) : newQty;
      if (qty < 1 || isNaN(qty)) return;

      this.items.update(current => {
          const updated = [...current];
          updated[index] = { ...updated[index], quantity: qty };
          return updated;
      });
  }

  totals = computed(() => {
      const currentItems = this.items();
      const setup = this.result().setupCost;
      
      let price = setup;
      let time = 0;
      let weight = 0;
      
      currentItems.forEach(i => {
          price += i.unitPrice * i.quantity;
          time += i.unitTime * i.quantity;
          weight += i.unitWeight * i.quantity;
      });
      
      const hours = Math.floor(time / 3600);
      const minutes = Math.ceil((time % 3600) / 60);
      
      return {
          price: Math.round(price * 100) / 100,
          hours,
          minutes,
          weight: Math.ceil(weight)
      };
  });
}
