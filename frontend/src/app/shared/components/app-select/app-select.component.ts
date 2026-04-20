import { Component, computed, forwardRef, input } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  FormsModule,
} from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-select',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  host: {
    '[attr.name]': 'name() || null',
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppSelectComponent),
      multi: true,
    },
  ],
  templateUrl: './app-select.component.html',
  styleUrl: './app-select.component.scss',
})
export class AppSelectComponent implements ControlValueAccessor {
  label = input<string>('');
  id = input<string>('select-' + Math.random().toString(36).substr(2, 9));
  name = input<string>('');
  compact = input<boolean>(false);
  options = input<{ label: string; value: any }[]>([]);
  error = input<string | null>(null);
  required = input<boolean>(false);
  disabledInput = input<boolean>(false, { alias: 'disabled' });

  value: any = '';
  private controlDisabled = false;
  readonly isDisabled = computed(
    () => this.controlDisabled || this.disabledInput(),
  );

  onChange: any = () => {};
  onTouched: any = () => {};

  writeValue(obj: any): void {
    this.value = obj;
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

  onModelChange(val: any) {
    this.value = val;
    this.onChange(val);
  }
}
