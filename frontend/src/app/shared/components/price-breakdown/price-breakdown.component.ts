import { CommonModule } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

export interface PriceBreakdownRow {
  label?: string;
  labelKey?: string;
  amount: number;
  visible?: boolean;
}

@Component({
  selector: 'app-price-breakdown',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './price-breakdown.component.html',
  styleUrl: './price-breakdown.component.scss',
})
export class PriceBreakdownComponent {
  rows = input<PriceBreakdownRow[]>([]);
  total = input.required<number>();
  currency = input<string>('CHF');
  totalLabel = input<string>('');
  totalLabelKey = input<string>('');

  visibleRows = computed(() =>
    this.rows().filter((row) => row.visible === undefined || row.visible),
  );
}
