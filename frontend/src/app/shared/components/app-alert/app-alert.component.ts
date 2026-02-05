import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-alert',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="alert" [ngClass]="type()">
      <div class="icon">
        @if(type() === 'info') { ℹ️ }
        @if(type() === 'warning') { ⚠️ }
        @if(type() === 'error') { ❌ }
        @if(type() === 'success') { ✅ }
      </div>
      <div class="content"><ng-content></ng-content></div>
    </div>
  `,
  styles: [`
    .alert {
      padding: var(--space-4);
      border-radius: var(--radius-md);
      display: flex;
      gap: var(--space-3);
      font-size: 0.875rem;
      margin-bottom: var(--space-4);
    }
    .info { background: var(--color-neutral-100); color: var(--color-neutral-800); }
    .warning { background: #fefce8; color: #854d0e; border: 1px solid #fde047; }
    .error { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }
    .success { background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; }
  `]
})
export class AppAlertComponent {
  type = input<'info' | 'warning' | 'error' | 'success'>('info');
}
