import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-summary-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="summary-card" [class.highlight]="highlight()">
      <span class="label">{{ label() }}</span>
      <span class="value" [class.large]="large()">
        <ng-content></ng-content>
      </span>
    </div>
  `,
  styles: [`
    .summary-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: var(--space-3);
      background: var(--color-bg-card);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      height: 100%;
      justify-content: center;
    }
    .highlight {
      background: var(--color-neutral-100);
      border-color: var(--color-border);
    }
    .label {
      font-size: 0.875rem;
      color: var(--color-text-muted);
      margin-bottom: var(--space-1);
    }
    .value {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--color-text);
    }
    .large {
      font-size: 2rem;
      color: var(--color-brand);
    }
  `]
})
export class SummaryCardComponent {
  label = input.required<string>();
  highlight = input<boolean>(false);
  large = input<boolean>(false);
}
