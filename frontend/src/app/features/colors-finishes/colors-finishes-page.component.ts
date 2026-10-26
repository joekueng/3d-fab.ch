import { CommonModule } from '@angular/common';
import {
  Component,
  computed,
  inject,
  RESPONSE_INIT,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';
import { LanguageService } from '../../core/services/language.service';
import {
  filamentFinishLabelKey,
  resolveLocalizedColorLabel,
} from '../../core/constants/colors.const';
import {
  PublicMediaService,
  PublicMediaDisplayImage,
  PublicMediaUsageCollectionMap,
  buildPublicMediaUsageScopeKey,
} from '../../core/services/public-media.service';
import { CalculatorOptionsService } from '../calculator/services/calculator-options.service';
import type {
  MaterialOption,
  VariantOption,
} from '../calculator/services/quote-estimator.service';
import { AppSelectComponent } from '../../shared/components/app-select/app-select.component';
import { AppCheckboxComponent } from '../../shared/components/app-checkbox/app-checkbox.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { ImagePreviewComponent } from '../../shared/components/image-preview/image-preview.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';

interface CatalogState {
  materials: MaterialOption[];
  loading: boolean;
  error: boolean;
}
interface CatalogVariant extends VariantOption {
  materialCode: string;
  materialLabel: string;
}

@Component({
  selector: 'app-colors-finishes-page',
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    TranslatePipe,
    AppSelectComponent,
    AppCheckboxComponent,
    AppButtonComponent,
    ImagePreviewComponent,
    AppAlertComponent,
  ],
  templateUrl: './colors-finishes-page.component.html',
  styleUrl: './colors-finishes-page.component.scss',
})
export class ColorsFinishesPageComponent {
  readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);
  private readonly options = inject(CalculatorOptionsService);
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });
  private readonly media = inject(PublicMediaService);
  private readonly reload = new Subject<void>();
  readonly material = signal('');
  readonly finish = signal('');
  readonly selectedIds = signal<number[]>([]);
  readonly failedImages = signal<ReadonlySet<string>>(new Set());
  readonly state = toSignal(
    this.reload.pipe(
      startWith(undefined),
      switchMap(() =>
        this.options.getOptions().pipe(
          map((result) => ({
            materials: result.materials,
            loading: false,
            error: false,
          })),
          catchError(() => {
            if (this.responseInit) this.responseInit.status = 503;
            return of({ materials: [], loading: false, error: true });
          }),
          startWith({ materials: [], loading: true, error: false }),
        ),
      ),
    ),
    {
      initialValue: {
        materials: [],
        loading: true,
        error: false,
      } as CatalogState,
    },
  );
  readonly variants = computed<CatalogVariant[]>(() =>
    this.state().materials.flatMap((material) =>
      material.variants.map((variant) => ({
        ...variant,
        materialCode: material.code,
        materialLabel: material.label,
      })),
    ),
  );
  private readonly mediaByUsage = toSignal(
    toObservable(this.variants).pipe(
      switchMap((variants) =>
        this.media.getUsageCollections(
          variants.map((variant) => ({
            usageType: 'FILAMENT_VARIANT',
            usageKey: String(variant.id),
          })),
        ),
      ),
    ),
    { initialValue: {} as PublicMediaUsageCollectionMap },
  );
  readonly materialOptions = computed(() => {
    this.languageService.currentLang();
    return [
      {
        value: '',
        label: this.translate.instant('COLORS_FINISHES.ALL_MATERIALS'),
      },
      ...this.state().materials.map((material) => ({
        value: material.code,
        label: material.label,
      })),
    ];
  });
  readonly finishOptions = computed(() => {
    this.languageService.currentLang();
    const variants = this.variants().filter(
      (variant) => !this.material() || variant.materialCode === this.material(),
    );
    return [
      {
        value: '',
        label: this.translate.instant('COLORS_FINISHES.ALL_FINISHES'),
      },
      ...[...new Set(variants.map((variant) => variant.finishType))]
        .sort()
        .map((finish) => ({ value: finish, label: this.finishLabel(finish) })),
    ];
  });
  readonly filteredVariants = computed(() =>
    this.variants().filter(
      (variant) =>
        (!this.material() || variant.materialCode === this.material()) &&
        (!this.finish() || variant.finishType === this.finish()),
    ),
  );
  readonly comparison = computed(() =>
    this.variants().filter((variant) =>
      this.selectedIds().includes(variant.id),
    ),
  );

  retry(): void {
    this.reload.next();
  }
  changeMaterial(value: string): void {
    this.material.set(value);
    this.finish.set('');
  }
  colorLabel(variant: VariantOption): string {
    return (
      resolveLocalizedColorLabel(this.languageService.currentLang(), {
        fallback: variant.colorName,
        it: variant.colorLabelIt,
        en: variant.colorLabelEn,
        de: variant.colorLabelDe,
        fr: variant.colorLabelFr,
      }) ?? variant.colorName
    );
  }
  finishLabel(value: string): string {
    return this.translate.instant(filamentFinishLabelKey(value));
  }

  swatch(variant: VariantOption): string {
    return /^#[0-9a-f]{6}$/i.test(variant.hexColor)
      ? variant.hexColor
      : 'transparent';
  }
  images(variant: VariantOption): PublicMediaDisplayImage[] {
    const images =
      this.mediaByUsage()[
        buildPublicMediaUsageScopeKey('FILAMENT_VARIANT', String(variant.id))
      ] ?? [];
    return [...images]
      .sort(
        (a, b) =>
          Number(b.isPrimary) - Number(a.isPrimary) ||
          a.sortOrder - b.sortOrder,
      )
      .map((image) => this.media.toDisplayImage(image, 'hero'))
      .filter(
        (image): image is PublicMediaDisplayImage =>
          image !== null && !this.failedImages().has(image.source.fallbackUrl),
      );
  }
  imageFailed(url: string): void {
    this.failedImages.update((current) => new Set([...current, url]));
  }
  toggleCompare(id: number, selected: boolean): void {
    this.selectedIds.update((ids) =>
      selected
        ? ids.includes(id) || ids.length >= 2
          ? ids
          : [...ids, id]
        : ids.filter((value) => value !== id),
    );
  }
}
