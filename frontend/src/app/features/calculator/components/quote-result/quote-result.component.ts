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
  templateUrl: './quote-result.component.html',
  styleUrl: './quote-result.component.scss'
})
export class QuoteResultComponent {
  result = input.required<QuoteResult>();
  consult = output<void>();
  proceed = output<void>();
  itemChange = output<{fileName: string, quantity: number}>();

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
      
      this.itemChange.emit({
          fileName: this.items()[index].fileName,
          quantity: qty
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
