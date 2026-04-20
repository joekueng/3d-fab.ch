import { CommonModule } from '@angular/common';
import { Component, computed, forwardRef, input } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
} from '@angular/forms';

@Component({
  selector: 'app-textarea',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  host: {
    '[attr.name]': 'name() || null',
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppTextareaComponent),
      multi: true,
    },
  ],
  templateUrl: './app-textarea.component.html',
  styleUrl: './app-textarea.component.scss',
})
export class AppTextareaComponent implements ControlValueAccessor {
  label = input<string>('');
  id = input<string>('textarea-' + Math.random().toString(36).slice(2, 11));
  name = input<string>('');
  compact = input<boolean>(false);
  placeholder = input<string>('');
  error = input<string | null>(null);
  required = input<boolean>(false);
  rows = input<number>(3);
  readonly = input<boolean>(false);
  disabledInput = input<boolean>(false, { alias: 'disabled' });

  value = '';
  private controlDisabled = false;
  readonly isDisabled = computed(
    () => this.controlDisabled || this.disabledInput(),
  );

  onChange: any = () => {};
  onTouched: any = () => {};

  writeValue(obj: any): void {
    this.value = obj ?? '';
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.controlDisabled = isDisabled;
  }

  onInput(event: Event): void {
    const nextValue = (event.target as HTMLTextAreaElement).value;
    this.value = nextValue;
    this.onChange(nextValue);
  }
}
