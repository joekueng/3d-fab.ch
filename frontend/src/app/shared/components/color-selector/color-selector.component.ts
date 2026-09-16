import {
  Component,
  input,
  output,
  signal,
  computed,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  PRODUCT_COLORS,
  getColorHex,
  ColorOption,
  resolveLocalizedColorLabel,
} from '../../../core/constants/colors.const';
import { VariantOption } from '../../../features/calculator/services/quote-estimator.service';
import { LanguageService } from '../../../core/services/language.service';

export interface ColorSelectorChoice extends Omit<ColorOption, 'variantId'> {
  variantId?: string | number;
}
export interface ColorSelectorGroup {
  name: string;
  colors: ColorSelectorChoice[];
}

@Component({
  selector: 'app-color-selector',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './color-selector.component.html',
  styleUrl: './color-selector.component.scss',
})
export class ColorSelectorComponent {
  private readonly languageService = inject(LanguageService);
  disabled = input(false);
  selectedColor = input<string>('Black');
  selectedVariantId = input<string | number | null>(null);
  variants = input<VariantOption[]>([]);
  colorSelected = output<{ colorName: string; filamentVariantId?: number }>();

  groups = input<ColorSelectorGroup[] | null>(null);
  showLabel = input(false);
  subtitle = input('');
  variantSelected = output<string | number>();
  isOpen = signal(false);

  categories = computed<ColorSelectorGroup[]>(() => {
    if (this.groups() !== null) return this.groups()!;
    const vars = this.variants();
    if (vars && vars.length > 0) {
      const byFinish = new Map<string, ColorOption[]>();
      vars.forEach((v) => {
        const finish = this.finishCategoryLabel(v.finishType);
        const bucket = byFinish.get(finish) || [];
        bucket.push({
          label:
            resolveLocalizedColorLabel(this.languageService.selectedLang(), {
              fallback: v.colorName,
              it: v.colorLabelIt,
              en: v.colorLabelEn,
              de: v.colorLabelDe,
              fr: v.colorLabelFr,
            }) ?? v.colorName,
          value: v.colorName,
          hex: v.hexColor,
          variantId: v.id,
          outOfStock: v.isOutOfStock,
        });
        byFinish.set(finish, bucket);
      });

      return Array.from(byFinish.entries()).map(([finish, colors]) => ({
        name: finish,
        colors,
      }));
    }
    return PRODUCT_COLORS;
  });

  toggleOpen() {
    if (this.disabled()) return;
    this.isOpen.update((v) => !v);
  }

  selectColor(color: ColorSelectorChoice) {
    if (this.disabled() || color.outOfStock) return;

    if (color.variantId !== undefined) this.variantSelected.emit(color.variantId);
    this.colorSelected.emit({
      colorName: color.value,
      filamentVariantId: typeof color.variantId === 'number' ? color.variantId : undefined,
    });
    this.isOpen.set(false);
  }

  private selectedChoice(): ColorSelectorChoice | undefined {
    const choices = this.categories().flatMap((category) => category.colors);
    return this.selectedVariantId() !== null
      ? choices.find((choice) => choice.variantId === this.selectedVariantId())
      : choices.find((choice) => choice.value === this.selectedColor());
  }

  getCurrentHex(): string {
    return this.selectedChoice()?.hex ?? getColorHex(this.selectedColor());
  }

  getCurrentLabel(): string {
    return this.selectedChoice()?.label ?? this.selectedColor();
  }

  private finishCategoryLabel(finishType: string): string {
    const normalized = String(finishType || '')
      .trim()
      .toLowerCase();
    if (normalized === 'glossy') return 'COLOR.CATEGORY_GLOSSY';
    if (normalized === 'matte') return 'COLOR.CATEGORY_MATTE';
    return 'COLOR.AVAILABLE_COLORS';
  }

  close() {
    this.isOpen.set(false);
  }
}
