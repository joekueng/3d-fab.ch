import { Component, input, output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { PRODUCT_COLORS, getColorHex, ColorCategory, ColorOption } from '../../../core/constants/colors.const';
import { VariantOption } from '../../../features/calculator/services/quote-estimator.service';

@Component({
  selector: 'app-color-selector',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './color-selector.component.html',
  styleUrl: './color-selector.component.scss'
})
export class ColorSelectorComponent {
  selectedColor = input<string>('Black');
  variants = input<VariantOption[]>([]);
  colorSelected = output<string>();

  isOpen = signal(false);

  categories = computed(() => {
      const vars = this.variants();
      if (vars && vars.length > 0) {
          // Flatten variants into a single category for now
          // We could try to group by extracting words, but "Colors" is fine.
          return [{
              name: 'COLOR.AVAILABLE_COLORS',
              colors: vars.map(v => ({
                  label: v.colorName, // Display "Red"
                  value: v.colorName, // Send "Red" to backend
                  hex: v.hexColor,
                  outOfStock: v.isOutOfStock
              }))
          }] as ColorCategory[];
      }
      return PRODUCT_COLORS;
  });

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
     // Check in dynamic variants first
     const vars = this.variants();
     if (vars && vars.length > 0) {
         const found = vars.find(v => v.colorName === this.selectedColor());
         if (found) return found.hexColor;
     }

    return getColorHex(this.selectedColor());
  }

  close() {
      this.isOpen.set(false);
  }
}
