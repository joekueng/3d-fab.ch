import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button 
      [type]="type()" 
      [class]="'btn btn-' + variant() + ' ' + (fullWidth() ? 'w-full' : '')"
      [disabled]="disabled()"
      (click)="handleClick($event)">
      <ng-content></ng-content>
    </button>
  `,
  styles: [`
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0.5rem 1rem;
      border-radius: var(--radius-md);
      font-weight: 500;
      cursor: pointer;
      transition: background-color 0.2s, color 0.2s, border-color 0.2s;
      border: 1px solid transparent;
      font-family: inherit;
      font-size: 1rem;
      
      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }
    .w-full { width: 100%; }

    .btn-primary {
      background-color: var(--color-brand);
      color: var(--color-neutral-900);
      &:hover:not(:disabled) { background-color: var(--color-brand-hover); }
    }

    .btn-secondary {
      background-color: var(--color-neutral-200);
      color: var(--color-neutral-900);
      &:hover:not(:disabled) { background-color: var(--color-neutral-300); }
    }

    .btn-outline {
      background-color: transparent;
      border-color: var(--color-border);
      color: var(--color-text);
      &:hover:not(:disabled) { 
        border-color: var(--color-brand); 
        color: var(--color-neutral-900);
        background-color: rgba(250, 207, 10, 0.1); /* Low opacity brand color */
      }
    }

    .btn-text {
      background-color: transparent;
      color: var(--color-text-muted);
      padding: 0.5rem;
      &:hover:not(:disabled) { color: var(--color-text); }
    }
  `]
})
export class AppButtonComponent {
  variant = input<'primary' | 'secondary' | 'outline' | 'text'>('primary');
  type = input<'button' | 'submit' | 'reset'>('button');
  disabled = input<boolean>(false);
  fullWidth = input<boolean>(false);

  handleClick(event: Event) {
    if (this.disabled()) {
      event.preventDefault();
      event.stopPropagation();
    }
  }
}
