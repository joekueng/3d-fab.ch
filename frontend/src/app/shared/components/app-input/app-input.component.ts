import { Component, input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppInputComponent),
      multi: true
    }
  ],
  template: `
    <div class="form-group">
      @if (label()) { <label [for]="id()">{{ label() }}</label> }
      <input
        [id]="id()"
        [type]="type()"
        [placeholder]="placeholder()"
        [value]="value"
        (input)="onInput($event)"
        (blur)="onTouched()"
        [disabled]="disabled"
        class="form-control"
      />
      @if (error()) { <span class="error-text">{{ error() }}</span> }
    </div>
  `,
  styles: [`
    .form-group { display: flex; flex-direction: column; margin-bottom: var(--space-4); }
    label { font-size: 0.875rem; font-weight: 500; margin-bottom: var(--space-2); color: var(--color-text); }
    .form-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      font-size: 1rem;
      width: 100%;
      background: var(--color-bg-card);
      color: var(--color-text);
      &:focus { outline: none; border-color: var(--color-brand); box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }
      &:disabled { background: var(--color-neutral-100); cursor: not-allowed; }
    }
    .error-text { color: var(--color-danger-500); font-size: 0.75rem; margin-top: var(--space-1); }
  `]
})
export class AppInputComponent implements ControlValueAccessor {
  label = input<string>('');
  id = input<string>('input-' + Math.random().toString(36).substr(2, 9));
  type = input<string>('text');
  placeholder = input<string>('');
  error = input<string | null>(null);

  value: string = '';
  disabled = false;

  onChange: any = () => {};
  onTouched: any = () => {};

  writeValue(obj: any): void { this.value = obj || ''; }
  registerOnChange(fn: any): void { this.onChange = fn; }
  registerOnTouched(fn: any): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }
  
  onInput(event: Event) {
    const val = (event.target as HTMLInputElement).value;
    this.value = val;
    this.onChange(val);
  }
}
