import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';

export interface ToggleOption {
  label: string;
  value: any;
}

@Component({
  selector: 'app-toggle-selector',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './app-toggle-selector.component.html',
  styleUrl: './app-toggle-selector.component.scss',
})
export class AppToggleSelectorComponent {
  options = input.required<ToggleOption[]>();
  selectedValue = input.required<any>();

  selectionChange = output<any>();

  selectOption(value: any) {
    this.selectionChange.emit(value);
  }
}
