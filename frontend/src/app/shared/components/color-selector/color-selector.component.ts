import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { PRODUCT_COLORS, getColorHex, ColorCategory, ColorOption } from '../../../core/constants/colors.const';

@Component({
  selector: 'app-color-selector',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './color-selector.component.html',
  styleUrl: './color-selector.component.scss'
})
export class ColorSelectorComponent {
  selectedColor = input<string>('Black');
  colorSelected = output<string>();

  isOpen = signal(false);

  categories: ColorCategory[] = PRODUCT_COLORS;

  toggleOpen() {
    this.isOpen.update(v => !v);
  }

  selectColor(color: ColorOption) {
    if (color.outOfStock) return;
    
    this.colorSelected.emit(color.value);
    this.isOpen.set(false);
  }

  // Helper to find hex for the current selected value
  getCurrentHex(): string {
    return getColorHex(this.selectedColor());
  }

  close() {
      this.isOpen.set(false);
  }
}
