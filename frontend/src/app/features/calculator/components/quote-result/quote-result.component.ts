import { Component, OnDestroy, input, output, signal, computed, effect } from '@angular/core';
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
export class QuoteResultComponent implements OnDestroy {
  readonly maxInputQuantity = 500;
  readonly directOrderLimit = 100;
  readonly quantityAutoRefreshMs = 2000;

  result = input.required<QuoteResult>();
  consult = output<void>();
  proceed = output<void>();
  itemChange = output<{id?: string, index: number, fileName: string, quantity: number}>();

  // Local mutable state for items to handle quantity changes
  items = signal<QuoteItem[]>([]);
  private lastSentQuantities = new Map<string, number>();
  private quantityTimers = new Map<string, ReturnType<typeof setTimeout>>();

  constructor() {
      effect(() => {
          this.clearAllQuantityTimers();

          // Initialize local items when result inputs change
          // We map to new objects to avoid mutating the input directly if it was a reference
          const nextItems = this.result().items.map(i => ({...i}));
          this.items.set(nextItems);

          this.lastSentQuantities.clear();
          nextItems.forEach(item => {
              const key = item.id ?? item.fileName;
              this.lastSentQuantities.set(key, item.quantity);
          });
      }, { allowSignalWrites: true });
  }

  ngOnDestroy(): void {
      this.clearAllQuantityTimers();
  }

  updateQuantity(index: number, newQty: number | string) {
      const normalizedQty = this.normalizeQuantity(newQty);
      if (normalizedQty === null) return;

      const item = this.items()[index];
      if (!item) return;
      const key = item.id ?? item.fileName;

      this.items.update(current => {
          const updated = [...current];
          updated[index] = { ...updated[index], quantity: normalizedQty };
          return updated;
      });

      this.scheduleQuantityRefresh(index, key);
  }

  flushQuantityUpdate(index: number): void {
      const item = this.items()[index];
      if (!item) return;

      const key = item.id ?? item.fileName;
      this.clearQuantityRefreshTimer(key);

      const normalizedQty = this.normalizeQuantity(item.quantity);
      if (normalizedQty === null) return;

      if (this.lastSentQuantities.get(key) === normalizedQty) {
          return;
      }

      this.itemChange.emit({
          id: item.id,
          index,
          fileName: item.fileName,
          quantity: normalizedQty
      });
      this.lastSentQuantities.set(key, normalizedQty);
  }

  hasQuantityOverLimit = computed(() => this.items().some(item => item.quantity > this.directOrderLimit));

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

  private normalizeQuantity(newQty: number | string): number | null {
      const qty = typeof newQty === 'string' ? parseInt(newQty, 10) : newQty;
      if (!Number.isFinite(qty) || qty < 1) {
          return null;
      }
      return Math.min(qty, this.maxInputQuantity);
  }

  private scheduleQuantityRefresh(index: number, key: string): void {
      this.clearQuantityRefreshTimer(key);
      const timer = setTimeout(() => {
          this.quantityTimers.delete(key);
          this.flushQuantityUpdate(index);
      }, this.quantityAutoRefreshMs);
      this.quantityTimers.set(key, timer);
  }

  private clearQuantityRefreshTimer(key: string): void {
      const timer = this.quantityTimers.get(key);
      if (!timer) return;
      clearTimeout(timer);
      this.quantityTimers.delete(key);
  }

  private clearAllQuantityTimers(): void {
      this.quantityTimers.forEach(timer => clearTimeout(timer));
      this.quantityTimers.clear();
  }

}
