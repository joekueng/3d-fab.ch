import { Component, computed, forwardRef, input } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
} from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  host: {
    '[attr.name]': 'name() || null',
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppInputComponent),
      multi: true,
    },
  ],
  templateUrl: './app-input.component.html',
  styleUrl: './app-input.component.scss',
})
export class AppInputComponent implements ControlValueAccessor {
  label = input<string>('');
  id = input<string>('input-' + Math.random().toString(36).substr(2, 9));
  name = input<string>('');
  compact = input<boolean>(false);
  type = input<string>('text');
  placeholder = input<string>('');
  error = input<string | null>(null);
  required = input<boolean>(false);
  autocomplete = input<string>('');
  autocapitalize = input<string>('');
  min = input<string | number | null>(null);
  max = input<string | number | null>(null);
  step = input<string | number | null>(null);
  inputmode = input<string>('');
  spellcheck = input<boolean | null>(null);
  readonly = input<boolean>(false);
  disabledInput = input<boolean>(false, { alias: 'disabled' });

  value: string | number | null = '';
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

  onInput(event: Event) {
    const rawValue = (event.target as HTMLInputElement).value;
    const nextValue =
      this.type() === 'number'
        ? rawValue === ''
          ? null
          : Number(rawValue)
        : rawValue;
    this.value = nextValue;
    this.onChange(nextValue);
  }
}
