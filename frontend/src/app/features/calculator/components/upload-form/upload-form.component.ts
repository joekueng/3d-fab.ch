import {
  Component,
  input,
  output,
  signal,
  OnInit,
  inject,
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';
import { ColorSelectorComponent } from '../../../../shared/components/color-selector/color-selector.component';
import {
  QuoteRequest,
  QuoteEstimatorService,
  OptionsResponse,
  SimpleOption,
  MaterialOption,
  VariantOption,
} from '../../services/quote-estimator.service';
import { getColorHex } from '../../../../core/constants/colors.const';

interface FormItem {
  file: File;
  previewFile?: File;
  quantity: number;
  material?: string;
  quality?: string;
  color: string;
  filamentVariantId?: number;
  supportEnabled?: boolean;
  infillDensity?: number;
  infillPattern?: string;
  layerHeight?: number;
  nozzleDiameter?: number;
  printSettings: ItemPrintSettings;
}

interface ItemPrintSettings {
  material: string;
  quality: string;
  nozzleDiameter: number;
  layerHeight: number;
  infillDensity: number;
  infillPattern: string;
  supportEnabled: boolean;
}

interface ItemSettingsDiffInfo {
  differences: string[];
}

type ItemPrintSettingsUpdate = Partial<ItemPrintSettings>;

@Component({
  selector: 'app-upload-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    AppInputComponent,
    AppDropzoneComponent,
    AppButtonComponent,
    StlViewerComponent,
    ColorSelectorComponent,
  ],
  templateUrl: './upload-form.component.html',
  styleUrl: './upload-form.component.scss',
})
export class UploadFormComponent implements OnInit {
  mode = input<'easy' | 'advanced'>('easy');
  lockedSettings = input<boolean>(false);
  loading = input<boolean>(false);
  uploadProgress = input<number>(0);
  submitRequest = output<QuoteRequest>();
  itemQuantityChange = output<{
    index: number;
    fileName: string;
    quantity: number;
  }>();
  itemSettingsDiffChange = output<Record<string, ItemSettingsDiffInfo>>();
  printSettingsChange = output<{
    mode: 'easy' | 'advanced';
    material: string;
    quality: string;
    nozzleDiameter: number;
    layerHeight: number;
    infillDensity: number;
    infillPattern: string;
    supportEnabled: boolean;
  }>();

  private estimator = inject(QuoteEstimatorService);
  private fb = inject(FormBuilder);
  private translate = inject(TranslateService);

  form: FormGroup;

  items = signal<FormItem[]>([]);
  selectedFile = signal<File | null>(null);

  // Dynamic Options
  materials = signal<SimpleOption[]>([]);
  qualities = signal<SimpleOption[]>([]);
  nozzleDiameters = signal<SimpleOption[]>([]);
  infillPatterns = signal<SimpleOption[]>([]);
  layerHeights = signal<SimpleOption[]>([]);

  // Store full material options to lookup variants/colors if needed later
  private fullMaterialOptions: MaterialOption[] = [];
  private allLayerHeights: SimpleOption[] = [];
  private layerHeightsByNozzle: Record<string, SimpleOption[]> = {};
  private isPatchingSettings = false;
  sameSettingsForAll = signal(true);

  // Computed variants for valid material
  currentMaterialVariants = signal<VariantOption[]>([]);

  private updateVariants() {
    const matCode = this.form.get('material')?.value;
    if (matCode && this.fullMaterialOptions.length > 0) {
      const found = this.fullMaterialOptions.find((m) => m.code === matCode);
      this.currentMaterialVariants.set(found ? found.variants : []);
      this.syncSelectedItemVariantSelection();
    } else {
      this.currentMaterialVariants.set([]);
    }
  }

  acceptedFormats = '.stl,.3mf,.step,.stp';

  isStlFile(file: File | null): boolean {
    if (!file) return false;
    const name = file.name.toLowerCase();
    return name.endsWith('.stl');
  }

  canPreviewSelectedFile(): boolean {
    return this.isStlFile(this.getSelectedPreviewFile());
  }

  getSelectedPreviewFile(): File | null {
    const selected = this.selectedFile();
    if (!selected) return null;
    const item = this.items().find((i) => i.file === selected);
    if (!item) return null;
    return item.previewFile ?? item.file;
  }

  getSelectedItemIndex(): number {
    const selected = this.selectedFile();
    if (!selected) return -1;
    return this.items().findIndex((item) => item.file === selected);
  }

  getSelectedItem(): FormItem | null {
    const index = this.getSelectedItemIndex();
    if (index < 0) return null;
    return this.items()[index] ?? null;
  }

  getVariantsForMaterial(materialCode: string | null | undefined): VariantOption[] {
    if (!materialCode) return [];
    const found = this.fullMaterialOptions.find((m) => m.code === materialCode);
    return found?.variants ?? [];
  }

  constructor() {
    this.form = this.fb.group({
      itemsTouched: [false], // Hack to track touched state for custom items list
      syncAllItems: [true],
      material: ['', Validators.required],
      quality: ['', Validators.required],
      items: [[]], // Track items in form for validation if needed
      notes: [''],
      // Advanced fields
      infillDensity: [15, [Validators.min(0), Validators.max(100)]],
      layerHeight: [0.2, [Validators.min(0.05), Validators.max(1.0)]],
      nozzleDiameter: [0.4, Validators.required],
      infillPattern: ['grid'],
      supportEnabled: [false],
    });

    // Listen to material changes to update variants and propagate when "all files equal" is active.
    this.form.get('material')?.valueChanges.subscribe((materialCode) => {
      this.updateVariants();
      if (this.sameSettingsForAll() && !this.isPatchingSettings) {
        this.applyGlobalMaterialToAll(String(materialCode || 'PLA'));
      }
    });

    this.form.get('quality')?.valueChanges.subscribe((quality) => {
      if (this.mode() !== 'easy' || this.isPatchingSettings) return;
      this.applyAdvancedPresetFromQuality(quality);
      if (this.sameSettingsForAll()) {
        this.applyGlobalFieldToAll('quality', String(quality || 'standard'));
      }
    });

    this.form.get('nozzleDiameter')?.valueChanges.subscribe((value) => {
      if (!this.sameSettingsForAll() || this.isPatchingSettings) return;
      this.applyGlobalFieldToAll(
        'nozzleDiameter',
        Number.isFinite(Number(value)) ? Number(value) : 0.4,
      );
    });
    this.form.get('layerHeight')?.valueChanges.subscribe((value) => {
      if (!this.sameSettingsForAll() || this.isPatchingSettings) return;
      this.applyGlobalFieldToAll(
        'layerHeight',
        Number.isFinite(Number(value)) ? Number(value) : 0.2,
      );
    });
    this.form.get('infillDensity')?.valueChanges.subscribe((value) => {
      if (!this.sameSettingsForAll() || this.isPatchingSettings) return;
      this.applyGlobalFieldToAll(
        'infillDensity',
        Number.isFinite(Number(value)) ? Number(value) : 15,
      );
    });
    this.form.get('infillPattern')?.valueChanges.subscribe((value) => {
      if (!this.sameSettingsForAll() || this.isPatchingSettings) return;
      this.applyGlobalFieldToAll('infillPattern', String(value || 'grid'));
    });
    this.form.get('supportEnabled')?.valueChanges.subscribe((value) => {
      if (!this.sameSettingsForAll() || this.isPatchingSettings) return;
      this.applyGlobalFieldToAll('supportEnabled', !!value);
    });
    this.form.get('nozzleDiameter')?.valueChanges.subscribe((nozzle) => {
      if (this.isPatchingSettings) return;
      this.updateLayerHeightOptionsForNozzle(nozzle, true);
    });
    this.form.valueChanges.subscribe(() => {
      if (this.isPatchingSettings) return;
      this.syncSelectedItemSettingsFromForm();
      this.emitPrintSettingsChange();
      this.emitItemSettingsDiffChange();
    });

    effect(() => {
      this.applySettingsLock(this.lockedSettings());
    });
  }

  private applyAdvancedPresetFromQuality(quality: string | null | undefined) {
    const normalized = (quality || 'standard').toLowerCase();

    const presets: Record<
      string,
      {
        nozzleDiameter: number;
        layerHeight: number;
        infillDensity: number;
        infillPattern: string;
      }
    > = {
      standard: {
        nozzleDiameter: 0.4,
        layerHeight: 0.2,
        infillDensity: 15,
        infillPattern: 'grid',
      },
      extra_fine: {
        nozzleDiameter: 0.4,
        layerHeight: 0.12,
        infillDensity: 20,
        infillPattern: 'grid',
      },
      high: {
        nozzleDiameter: 0.4,
        layerHeight: 0.12,
        infillDensity: 20,
        infillPattern: 'grid',
      }, // Legacy alias
      draft: {
        nozzleDiameter: 0.4,
        layerHeight: 0.24,
        infillDensity: 12,
        infillPattern: 'grid',
      },
    };

    const preset = presets[normalized] || presets['standard'];
    this.form.patchValue(preset, { emitEvent: false });
    this.updateLayerHeightOptionsForNozzle(preset.nozzleDiameter, true);
  }

  ngOnInit() {
    this.estimator.getOptions().subscribe({
      next: (options: OptionsResponse) => {
        this.fullMaterialOptions = options.materials;
        this.updateVariants(); // Trigger initial update

        this.materials.set(
          options.materials.map((m) => ({ label: m.label, value: m.code })),
        );
        this.qualities.set(
          options.qualities.map((q) => ({ label: q.label, value: q.id })),
        );
        this.infillPatterns.set(
          options.infillPatterns.map((p) => ({ label: p.label, value: p.id })),
        );
        this.allLayerHeights = options.layerHeights.map((l) => ({
          label: l.label,
          value: l.value,
        }));
        this.layerHeightsByNozzle = {};
        (options.layerHeightsByNozzle || []).forEach((entry) => {
          this.layerHeightsByNozzle[this.toNozzleKey(entry.nozzleDiameter)] =
            entry.layerHeights.map((layer) => ({
              label: layer.label,
              value: layer.value,
            }));
        });
        this.layerHeights.set(this.allLayerHeights);
        this.nozzleDiameters.set(
          options.nozzleDiameters.map((n) => ({
            label: n.label,
            value: n.value,
          })),
        );

        this.setDefaults();
      },
      error: (err) => {
        console.error('Failed to load options', err);
        // Fallback for debugging/offline dev
        this.materials.set([
          {
            label: this.translate.instant('CALC.FALLBACK_MATERIAL'),
            value: 'PLA',
          },
        ]);
        this.qualities.set([
          {
            label: this.translate.instant('CALC.FALLBACK_QUALITY_STANDARD'),
            value: 'standard',
          },
        ]);
        this.allLayerHeights = [{ label: '0.20 mm', value: 0.2 }];
        this.layerHeightsByNozzle = {
          [this.toNozzleKey(0.4)]: this.allLayerHeights,
        };
        this.layerHeights.set(this.allLayerHeights);
        this.nozzleDiameters.set([{ label: '0.4 mm', value: 0.4 }]);
        this.setDefaults();
      },
    });
  }

  private setDefaults() {
    // Set Defaults if available
    if (this.materials().length > 0 && !this.form.get('material')?.value) {
      const exactPla = this.materials().find(
        (m) => typeof m.value === 'string' && m.value.toUpperCase() === 'PLA',
      );
      const anyPla = this.materials().find(
        (m) =>
          typeof m.value === 'string' &&
          m.value.toUpperCase().startsWith('PLA'),
      );
      const preferredMaterial = exactPla ?? anyPla ?? this.materials()[0];
      this.form.get('material')?.setValue(preferredMaterial.value);
    }
    if (this.qualities().length > 0 && !this.form.get('quality')?.value) {
      // Try to find 'standard' or use first
      const std = this.qualities().find((q) => q.value === 'standard');
      this.form
        .get('quality')
        ?.setValue(std ? std.value : this.qualities()[0].value);
    }
    if (
      this.nozzleDiameters().length > 0 &&
      !this.form.get('nozzleDiameter')?.value
    ) {
      this.form.get('nozzleDiameter')?.setValue(0.4); // Prefer 0.4
    }

    this.updateLayerHeightOptionsForNozzle(
      this.form.get('nozzleDiameter')?.value,
      true,
    );

    if (
      this.infillPatterns().length > 0 &&
      !this.form.get('infillPattern')?.value
    ) {
      this.form.get('infillPattern')?.setValue(this.infillPatterns()[0].value);
    }

    this.emitPrintSettingsChange();
  }

  onFilesDropped(newFiles: File[]) {
    const MAX_SIZE = 200 * 1024 * 1024; // 200MB
    const validItems: FormItem[] = [];
    let hasError = false;
    const defaults = this.getCurrentGlobalItemDefaults();

    for (const file of newFiles) {
      if (file.size > MAX_SIZE) {
        hasError = true;
      } else {
        const defaultSelection = this.getDefaultVariantSelection(defaults.material);
        validItems.push({
          file,
          previewFile: this.isStlFile(file) ? file : undefined,
          quantity: 1,
          material: defaults.material,
          quality: defaults.quality,
          color: defaultSelection.colorName,
          filamentVariantId: defaultSelection.filamentVariantId,
          supportEnabled: defaults.supportEnabled,
          infillDensity: defaults.infillDensity,
          infillPattern: defaults.infillPattern,
          layerHeight: defaults.layerHeight,
          nozzleDiameter: defaults.nozzleDiameter,
          printSettings: this.getCurrentItemPrintSettings(),
        });
      }
    }

    if (hasError) {
      alert(this.translate.instant('CALC.ERR_FILE_TOO_LARGE'));
    }

    if (validItems.length > 0) {
      this.items.update((current) => [...current, ...validItems]);
      this.form.get('itemsTouched')?.setValue(true);
      // Auto select last added
      this.selectFile(validItems[validItems.length - 1].file);
      this.emitItemSettingsDiffChange();
    }
  }

  onAdditionalFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.onFilesDropped(Array.from(input.files));
      // Reset input so same files can be selected again if needed
      input.value = '';
    }
  }

  updateItemQuantityByIndex(index: number, quantity: number) {
    if (!Number.isInteger(index) || index < 0) return;
    const normalizedQty = this.normalizeQuantity(quantity);

    this.items.update((current) => {
      if (index >= current.length) return current;
      const applyToAll = this.sameSettingsForAll();
      return current.map((item, idx) => {
        if (!applyToAll && idx !== index) return item;
        return { ...item, quantity: normalizedQty };
      });
    });
  }

  updateItemQuantityByName(fileName: string, quantity: number) {
    const targetName = this.normalizeFileName(fileName);
    const normalizedQty = this.normalizeQuantity(quantity);
    const applyToAll = this.sameSettingsForAll();

    this.items.update((current) => {
      let matched = false;
      return current.map((item) => {
        if (applyToAll) {
          return { ...item, quantity: normalizedQty };
        }
        if (!matched && this.normalizeFileName(item.file.name) === targetName) {
          matched = true;
          return { ...item, quantity: normalizedQty };
        }
        return item;
      });
    });
  }

  selectFile(file: File) {
    if (this.selectedFile() === file) {
      // toggle off? no, keep active
    } else {
      this.selectedFile.set(file);
    }
    this.loadSelectedItemSettingsIntoForm();
  }

  // Helper to get color of currently selected file
  getSelectedFileColor(): string {
    const file = this.selectedFile();
    if (!file) return '#facf0a'; // Default

    const item = this.items().find((i) => i.file === file);
    if (item) {
      const vars = this.getVariantsForMaterial(item.material);
      if (vars && vars.length > 0) {
        const found = item.filamentVariantId
          ? vars.find((v) => v.id === item.filamentVariantId)
          : vars.find((v) => v.colorName === item.color);
        if (found) return found.hexColor;
      }
      return getColorHex(item.color);
    }
    return '#facf0a';
  }

  updateItemQuantity(index: number, event: Event) {
    const input = event.target as HTMLInputElement;
    const parsed = parseInt(input.value, 10);
    const quantity = Number.isFinite(parsed) ? parsed : 1;
    const currentItem = this.items()[index];
    if (!currentItem) return;
    const normalizedQty = this.normalizeQuantity(quantity);
    this.updateItemQuantityByIndex(index, quantity);
    this.itemQuantityChange.emit({
      index,
      fileName: currentItem.file.name,
      quantity: normalizedQty,
    });
  }

  updateItemColor(
    index: number,
    newSelection: string | { colorName: string; filamentVariantId?: number },
  ) {
    const colorName =
      typeof newSelection === 'string' ? newSelection : newSelection.colorName;
    const filamentVariantId =
      typeof newSelection === 'string'
        ? undefined
        : newSelection.filamentVariantId;
    this.items.update((current) => {
      const updated = [...current];
      const applyToAll = this.sameSettingsForAll();
      return updated.map((item, idx) => {
        if (!applyToAll && idx !== index) return item;
        return {
          ...item,
          color: colorName,
          filamentVariantId,
        };
      });
    });
  }

  updateItemMaterial(index: number, materialCode: string) {
    if (!Number.isInteger(index) || index < 0) return;
    const variants = this.getVariantsForMaterial(materialCode);
    const fallback = variants.find((v) => !v.isOutOfStock) || variants[0];

    this.items.update((current) => {
      if (index >= current.length) return current;
      const applyToAll = this.sameSettingsForAll();
      return current.map((item, idx) => {
        if (!applyToAll && idx !== index) return item;
        const next = { ...item, material: materialCode };
        if (fallback) {
          next.color = fallback.colorName;
          next.filamentVariantId = fallback.id;
        } else {
          next.filamentVariantId = undefined;
        }
        return next;
      });
    });
  }

  updateSelectedItemNumberField(
    field:
      | 'nozzleDiameter'
      | 'layerHeight'
      | 'infillDensity'
      | 'quantity',
    value: number,
  ) {
    const index = this.getSelectedItemIndex();
    if (index < 0) return;
    const normalized =
      field === 'quantity'
        ? this.normalizeQuantity(value)
        : Number.isFinite(value)
          ? value
          : undefined;

    this.items.update((current) => {
      if (index >= current.length) return current;
      const applyToAll = this.sameSettingsForAll();
      return current.map((item, idx) => {
        if (!applyToAll && idx !== index) return item;
        return {
          ...item,
          [field]: normalized,
        };
      });
    });
  }

  updateSelectedItemStringField(
    field: 'quality' | 'infillPattern',
    value: string,
  ) {
    const index = this.getSelectedItemIndex();
    if (index < 0) return;
    this.items.update((current) => {
      if (index >= current.length) return current;
      const applyToAll = this.sameSettingsForAll();
      return current.map((item, idx) => {
        if (!applyToAll && idx !== index) return item;
        return {
          ...item,
          [field]: value,
        };
      });
    });
  }

  updateSelectedItemSupport(value: boolean) {
    const index = this.getSelectedItemIndex();
    if (index < 0) return;
    this.items.update((current) => {
      if (index >= current.length) return current;
      const applyToAll = this.sameSettingsForAll();
      return current.map((item, idx) => {
        if (!applyToAll && idx !== index) return item;
        return {
          ...item,
          supportEnabled: value,
        };
      });
    });
  }

  onSameSettingsToggle(enabled: boolean) {
    this.sameSettingsForAll.set(enabled);
    if (!enabled) {
      // Keep per-file values aligned with what the user sees in global controls
      // right before switching to single-file mode.
      this.syncAllItemsWithGlobalForm();
      return;
    }

    const selected = this.getSelectedItem() ?? this.items()[0];
    if (!selected) return;

    const normalizedQuality = this.normalizeQualityValue(
      selected.quality ?? this.form.get('quality')?.value,
    );

    this.isPatchingSettings = true;
    this.form.patchValue(
      {
        material: selected.material || this.form.get('material')?.value || 'PLA',
        quality: normalizedQuality,
        nozzleDiameter:
          selected.nozzleDiameter ?? this.form.get('nozzleDiameter')?.value ?? 0.4,
        layerHeight:
          selected.layerHeight ?? this.form.get('layerHeight')?.value ?? 0.2,
        infillDensity:
          selected.infillDensity ?? this.form.get('infillDensity')?.value ?? 15,
        infillPattern:
          selected.infillPattern || this.form.get('infillPattern')?.value || 'grid',
        supportEnabled:
          selected.supportEnabled ??
          this.form.get('supportEnabled')?.value ??
          false,
      },
      { emitEvent: false },
    );
    this.isPatchingSettings = false;

    const sharedPatch: Partial<FormItem> = {
      quantity: selected.quantity,
      material: selected.material,
      quality: normalizedQuality,
      color: selected.color,
      filamentVariantId: selected.filamentVariantId,
      supportEnabled: selected.supportEnabled,
      infillDensity: selected.infillDensity,
      infillPattern: selected.infillPattern,
      layerHeight: selected.layerHeight,
      nozzleDiameter: selected.nozzleDiameter,
    };

    this.items.update((current) =>
      current.map((item) => ({
        ...item,
        ...sharedPatch,
      })),
    );
  }

  private applyGlobalMaterialToAll(materialCode: string): void {
    const normalizedMaterial = materialCode || 'PLA';
    const variants = this.getVariantsForMaterial(normalizedMaterial);
    const fallback = variants.find((v) => !v.isOutOfStock) || variants[0];
    this.items.update((current) =>
      current.map((item) => ({
        ...item,
        material: normalizedMaterial,
        color: fallback ? fallback.colorName : item.color,
        filamentVariantId: fallback ? fallback.id : item.filamentVariantId,
      })),
    );
  }

  private applyGlobalFieldToAll(
    field:
      | 'quality'
      | 'nozzleDiameter'
      | 'layerHeight'
      | 'infillDensity'
      | 'infillPattern'
      | 'supportEnabled',
    value: string | number | boolean,
  ): void {
    this.items.update((current) =>
      current.map((item) => ({
        ...item,
        [field]: value,
      })),
    );
  }

  patchItemSettingsByIndex(index: number, patch: Partial<FormItem>) {
    if (!Number.isInteger(index) || index < 0) return;
    const normalizedPatch: Partial<FormItem> = { ...patch };
    if (normalizedPatch.quality !== undefined && normalizedPatch.quality !== null) {
      normalizedPatch.quality = this.normalizeQualityValue(normalizedPatch.quality);
    }
    this.items.update((current) => {
      if (index >= current.length) return current;
      const updated = [...current];
      updated[index] = { ...updated[index], ...normalizedPatch };
      return updated;
    });
    this.emitItemSettingsDiffChange();
  }

  setItemPrintSettingsByIndex(index: number, update: ItemPrintSettingsUpdate) {
    if (!Number.isInteger(index) || index < 0) return;

    let selectedItemUpdated = false;
    this.items.update((current) => {
      if (index >= current.length) return current;
      const updated = [...current];
      const target = updated[index];
      if (!target) return current;

      const merged: ItemPrintSettings = {
        ...target.printSettings,
        ...update,
      };

      updated[index] = {
        ...target,
        printSettings: merged,
      };
      selectedItemUpdated = target.file === this.selectedFile();
      return updated;
    });

    if (selectedItemUpdated) {
      this.loadSelectedItemSettingsIntoForm();
      this.emitPrintSettingsChange();
    }
    this.emitItemSettingsDiffChange();
  }

  removeItem(index: number) {
    let nextSelected: File | null = null;
    this.items.update((current) => {
      const updated = [...current];
      const removed = updated.splice(index, 1)[0];
      if (this.selectedFile() === removed.file) {
        nextSelected = updated.length > 0 ? updated[Math.max(0, index - 1)].file : null;
      }
      return updated;
    });
    if (nextSelected) {
      this.selectFile(nextSelected);
    } else if (this.items().length === 0) {
      this.selectedFile.set(null);
    }
    this.emitItemSettingsDiffChange();
  }

  setFiles(files: File[]) {
    const validItems: FormItem[] = [];
    const defaults = this.getCurrentGlobalItemDefaults();
    const defaultSelection = this.getDefaultVariantSelection(defaults.material);
    for (const file of files) {
      validItems.push({
        file,
        previewFile: this.isStlFile(file) ? file : undefined,
        quantity: 1,
        material: defaults.material,
        quality: defaults.quality,
        color: defaultSelection.colorName,
        filamentVariantId: defaultSelection.filamentVariantId,
        supportEnabled: defaults.supportEnabled,
        infillDensity: defaults.infillDensity,
        infillPattern: defaults.infillPattern,
        layerHeight: defaults.layerHeight,
        nozzleDiameter: defaults.nozzleDiameter,
      });
    }

    if (validItems.length > 0) {
      this.items.set(validItems);
      this.form.get('itemsTouched')?.setValue(true);
      // Auto select last added
      this.selectFile(validItems[validItems.length - 1].file);
      this.emitItemSettingsDiffChange();
    }
  }

  setPreviewFileByIndex(index: number, previewFile: File) {
    if (!Number.isInteger(index) || index < 0) return;
    this.items.update((current) => {
      if (index >= current.length) return current;
      const updated = [...current];
      updated[index] = { ...updated[index], previewFile };
      return updated;
    });
  }

  private getCurrentGlobalItemDefaults(): Omit<FormItem, 'file' | 'previewFile' | 'quantity' | 'color' | 'filamentVariantId'> & {
    material: string;
    quality: string;
  } {
    return {
      material: this.form.get('material')?.value || 'PLA',
      quality: this.normalizeQualityValue(this.form.get('quality')?.value),
      supportEnabled: !!this.form.get('supportEnabled')?.value,
      infillDensity: Number(this.form.get('infillDensity')?.value ?? 15),
      infillPattern: this.form.get('infillPattern')?.value || 'grid',
      layerHeight: Number(this.form.get('layerHeight')?.value ?? 0.2),
      nozzleDiameter: Number(this.form.get('nozzleDiameter')?.value ?? 0.4),
    };
  }

  private getDefaultVariantSelection(materialCode?: string): {
    colorName: string;
    filamentVariantId?: number;
  } {
    const vars = materialCode
      ? this.getVariantsForMaterial(materialCode)
      : this.currentMaterialVariants();
    if (vars && vars.length > 0) {
      const preferred = vars.find((v) => !v.isOutOfStock) || vars[0];
      return {
        colorName: preferred.colorName,
        filamentVariantId: preferred.id,
      };
    }
    return { colorName: 'Black' };
  }

  getVariantsForItem(item: FormItem): VariantOption[] {
    return this.getVariantsForMaterialCode(item.printSettings.material);
  }

  private getVariantsForMaterialCode(materialCodeRaw: string): VariantOption[] {
    const materialCode = String(materialCodeRaw || '').toUpperCase();
    if (!materialCode) {
      return [];
    }
    const material = this.fullMaterialOptions.find(
      (option) => String(option.code || '').toUpperCase() === materialCode,
    );
    return material?.variants || [];
  }

  private syncSelectedItemVariantSelection(): void {
    const vars = this.currentMaterialVariants();
    if (!vars || vars.length === 0) {
      return;
    }

    const selected = this.selectedFile();
    if (!selected) {
      return;
    }

    const fallback = vars.find((v) => !v.isOutOfStock) || vars[0];
    this.items.update((current) =>
      current.map((item) => {
        if (item.file !== selected) {
          return item;
        }
        const byId =
          item.filamentVariantId != null
            ? vars.find((v) => v.id === item.filamentVariantId)
            : null;
        const byColor = vars.find((v) => v.colorName === item.color);
        const selectedVariant = byId || byColor || fallback;
        return {
          ...item,
          color: selectedVariant.colorName,
          filamentVariantId: selectedVariant.id,
        };
      }),
    );
  }

  patchSettings(settings: any) {
    if (!settings) return;
    // settings object matches keys in our form?
    // Session has: materialCode, etc. derived from QuoteSession entity properties
    // We need to map them if names differ.

    const patch: any = {};
    if (settings.materialCode) patch.material = settings.materialCode;

    // Heuristic for Quality if not explicitly stored as "draft/standard/high"
    // But we stored it in session creation?
    // QuoteSession entity does NOT store "quality" string directly, only layerHeight/infill.
    // So we might need to deduce it or just set Custom/Advanced.
    // But for Easy mode, we want to show "Standard" etc.

    // Actually, let's look at what we have in QuoteSession.
    // layerHeightMm, infillPercent, etc.
    // If we are in Easy mode, we might just set the "quality" dropdown to match approx?
    // Or if we stored "quality" in notes or separate field? We didn't.

    // Let's try to reverse map or defaults.
    if (settings.layerHeightMm) {
      if (settings.layerHeightMm >= 0.24) patch.quality = 'draft';
      else if (settings.layerHeightMm <= 0.12) patch.quality = 'extra_fine';
      else patch.quality = 'standard';

      patch.layerHeight = settings.layerHeightMm;
    }

    if (settings.nozzleDiameterMm)
      patch.nozzleDiameter = settings.nozzleDiameterMm;
    if (settings.infillPercent) patch.infillDensity = settings.infillPercent;
    if (settings.infillPattern) patch.infillPattern = settings.infillPattern;
    if (settings.supportsEnabled !== undefined)
      patch.supportEnabled = settings.supportsEnabled;
    if (settings.notes) patch.notes = settings.notes;

    this.isPatchingSettings = true;
    this.form.patchValue(patch, { emitEvent: false });
    this.isPatchingSettings = false;
    this.updateLayerHeightOptionsForNozzle(
      this.form.get('nozzleDiameter')?.value,
      true,
    );
    this.emitPrintSettingsChange();
  }

  onSubmit() {
    console.log('UploadFormComponent: onSubmit triggered');
    console.log('Form Valid:', this.form.valid, 'Items:', this.items().length);

    if (this.form.valid && this.items().length > 0) {
      const items = this.items();
      const firstItemMaterial = items[0]?.material;
      console.log(
        'UploadFormComponent: Emitting submitRequest',
        this.form.value,
      );
      this.submitRequest.emit({
        ...this.form.getRawValue(),
        items: this.items(), // Pass the items array explicitly AFTER form value to prevent overwrite
        mode: this.mode(),
      });
    } else {
      console.warn('UploadFormComponent: Form Invalid or No Items');
      console.log('Form Errors:', this.form.errors);
      Object.keys(this.form.controls).forEach((key) => {
        const control = this.form.get(key);
        if (control?.invalid) {
          console.log(
            'Invalid Control:',
            key,
            control.errors,
            'Value:',
            control.value,
          );
        }
      });
      this.form.markAllAsTouched();
      this.form.get('itemsTouched')?.setValue(true);
    }
  }

  private normalizeQuantity(quantity: number): number {
    if (!Number.isFinite(quantity) || quantity < 1) {
      return 1;
    }
    return Math.floor(quantity);
  }

  private normalizeFileName(fileName: string): string {
    return (fileName || '').split(/[\\/]/).pop()?.trim().toLowerCase() ?? '';
  }

  private applySettingsLock(locked: boolean): void {
    const controlsToLock = [
      'syncAllItems',
      'material',
      'quality',
      'nozzleDiameter',
      'infillPattern',
      'layerHeight',
      'infillDensity',
      'supportEnabled',
    ];

    controlsToLock.forEach((name) => {
      const control = this.form.get(name);
      if (!control) return;
      if (locked) {
        control.disable({ emitEvent: false });
      } else {
        control.enable({ emitEvent: false });
      }
    });
  }
}
