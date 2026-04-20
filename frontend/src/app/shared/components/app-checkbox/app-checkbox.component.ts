import { CommonModule } from '@angular/common';
import { Component, computed, forwardRef, input } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
} from '@angular/forms';

@Component({
  selector: 'app-checkbox',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppCheckboxComponent),
      multi: true,
    },
  ],
  templateUrl: './app-checkbox.component.html',
  styleUrl: './app-checkbox.component.scss',
})
export class AppCheckboxComponent implements ControlValueAccessor {
  id = input<string>('checkbox-' + Math.random().toString(36).slice(2, 11));
  name = input<string>('');
  label = input<string>('');
  variant = input<'default' | 'pill'>('default');
  disabledInput = input<boolean>(false, { alias: 'disabled' });

  checked = false;
  private controlDisabled = false;
  readonly isDisabled = computed(
    () => this.controlDisabled || this.disabledInput(),
  );

  onChange: any = () => {};
  onTouched: any = () => {};

  checkboxClass(): string {
    return this.variant() === 'pill'
      ? 'ui-checkbox ui-checkbox--pill'
      : 'ui-checkbox';
  }

  writeValue(obj: any): void {
    this.checked = !!obj;
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
    const nextValue = (event.target as HTMLInputElement).checked;
    this.checked = nextValue;
    this.onChange(nextValue);
  }
}
