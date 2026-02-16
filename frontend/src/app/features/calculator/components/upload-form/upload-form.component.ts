import { Component, input, output, signal, effect, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../../shared/components/app-select/app-select.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';
import { ColorSelectorComponent } from '../../../../shared/components/color-selector/color-selector.component';
import { QuoteRequest, QuoteEstimatorService, OptionsResponse, SimpleOption, MaterialOption, VariantOption } from '../../services/quote-estimator.service';
import { getColorHex } from '../../../../core/constants/colors.const';

interface FormItem {
    id?: string;
    file: File;
    quantity: number;
    color: string;
}

@Component({
  selector: 'app-upload-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppInputComponent, AppSelectComponent, AppDropzoneComponent, AppButtonComponent, StlViewerComponent, ColorSelectorComponent],
  templateUrl: './upload-form.component.html',
  styleUrl: './upload-form.component.scss'
})
export class UploadFormComponent implements OnInit {
  mode = input<'easy' | 'advanced'>('easy');
  loading = input<boolean>(false);
  uploadProgress = input<number>(0);
  submitRequest = output<QuoteRequest>();
  itemRemoved = output<{index: number, id?: string}>();

  private estimator = inject(QuoteEstimatorService);
  private fb = inject(FormBuilder);

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
  
  // Computed variants for valid material
  currentMaterialVariants = signal<VariantOption[]>([]);
  
  private updateVariants() {
      const matCode = this.form.get('material')?.value;
      if (matCode && this.fullMaterialOptions.length > 0) {
          const found = this.fullMaterialOptions.find(m => m.code === matCode);
          this.currentMaterialVariants.set(found ? found.variants : []);
      } else {
          this.currentMaterialVariants.set([]);
      }
  }

  acceptedFormats = '.stl,.3mf,.step,.stp,.obj,.amf,.ply,.igs,.iges';

  constructor() {
    this.form = this.fb.group({
      itemsTouched: [false], // Hack to track touched state for custom items list
      material: ['', Validators.required],
      quality: ['', Validators.required],
      items: [[]], // Track items in form for validation if needed
      notes: [''],
      // Advanced fields
      infillDensity: [20, [Validators.min(0), Validators.max(100)]],
      layerHeight: [0.2, [Validators.min(0.05), Validators.max(1.0)]],
      nozzleDiameter: [0.4, Validators.required],
      infillPattern: ['grid'],
      supportEnabled: [true]
    });
    
    // Listen to material changes to update variants
    this.form.get('material')?.valueChanges.subscribe(() => {
        this.updateVariants();
    });
  }

  ngOnInit() {
      this.estimator.getOptions().subscribe({
          next: (options: OptionsResponse) => {
              this.fullMaterialOptions = options.materials;
              this.updateVariants(); // Trigger initial update
              
              this.materials.set(options.materials.map(m => ({ label: m.label, value: m.code })));
              this.qualities.set(options.qualities.map(q => ({ label: q.label, value: q.id })));
              this.infillPatterns.set(options.infillPatterns.map(p => ({ label: p.label, value: p.id })));
              this.layerHeights.set(options.layerHeights.map(l => ({ label: l.label, value: l.value })));
              this.nozzleDiameters.set(options.nozzleDiameters.map(n => ({ label: n.label, value: n.value })));

              this.setDefaults();
          },
          error: (err) => {
              console.error('Failed to load options', err);
              // Fallback for debugging/offline dev
              this.materials.set([{ label: 'PLA (Fallback)', value: 'PLA' }]);
              this.qualities.set([{ label: 'Standard', value: 'standard' }]);
              this.nozzleDiameters.set([{ label: '0.4 mm', value: 0.4 }]);
              this.setDefaults();
          }
      });
  }

  private setDefaults() {
      // Set Defaults if available
      if (this.materials().length > 0 && !this.form.get('material')?.value) {
           // Prefer PLA Basic, otherwise first available
           const pla = this.materials().find(m => m.value === 'pla_basic');
           this.form.get('material')?.setValue(pla ? pla.value : this.materials()[0].value);
      }
      if (this.qualities().length > 0 && !this.form.get('quality')?.value) {
          // Try to find 'standard' or use first
          const std = this.qualities().find(q => q.value === 'standard');
          this.form.get('quality')?.setValue(std ? std.value : this.qualities()[0].value);
      }
      if (this.nozzleDiameters().length > 0 && !this.form.get('nozzleDiameter')?.value) {
           this.form.get('nozzleDiameter')?.setValue(0.4); // Prefer 0.4
      }
      if (this.layerHeights().length > 0 && !this.form.get('layerHeight')?.value) {
           this.form.get('layerHeight')?.setValue(0.2); // Prefer 0.2
      }
      if (this.infillPatterns().length > 0 && !this.form.get('infillPattern')?.value) {
           this.form.get('infillPattern')?.setValue(this.infillPatterns()[0].value);
      }
  }

  onFilesDropped(newFiles: File[]) {
    const MAX_SIZE = 200 * 1024 * 1024; // 200MB
    const validItems: FormItem[] = [];
    let hasError = false;

    for (const file of newFiles) {
        if (file.size > MAX_SIZE) {
            hasError = true;
        } else {
            // Default color is Black
            validItems.push({ file, quantity: 1, color: 'Black' });
        }
    }

    if (hasError) {
        alert("Alcuni file superano il limite di 200MB e non sono stati aggiunti.");
    }

    if (validItems.length > 0) {
        this.items.update(current => [...current, ...validItems]);
        this.form.get('itemsTouched')?.setValue(true);
        // Auto select last added
        this.selectedFile.set(validItems[validItems.length - 1].file);
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

  updateItemQuantityByName(fileName: string, quantity: number) {
      this.items.update(current => {
          return current.map(item => {
              if (item.file.name === fileName) {
                  return { ...item, quantity };
              }
              return item;
          });
      });
  }

  updateItemQuantityAtIndex(index: number, quantity: number) {
      this.items.update(current => {
          const updated = [...current];
          if (updated[index]) {
              updated[index] = { ...updated[index], quantity };
          }
          return updated;
      });
  }

  updateItemIds(itemsWithIds: { fileName: string, id: string }[]) {
      this.items.update(current => {
          return current.map(item => {
              const match = itemsWithIds.find(i => i.fileName === item.file.name && !i.id); // This matching is weak
              // Better: matching should be based on index if we trust order
              return item;
          });
      });
  }

  updateItemIdsByIndex(ids: (string | undefined)[]) {
      this.items.update(current => {
          return current.map((item, i) => {
              if (ids[i]) {
                  return { ...item, id: ids[i] };
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
  }

  // Helper to get color of currently selected file
  getSelectedFileColor(): string {
      const file = this.selectedFile();
      if (!file) return '#facf0a'; // Default
      
      const item = this.items().find(i => i.file === file);
      if (item) {
          const vars = this.currentMaterialVariants();
          if (vars && vars.length > 0) {
              const found = vars.find(v => v.colorName === item.color);
              if (found) return found.hexColor;
          }
          return getColorHex(item.color);
      }
      return '#facf0a';
  }

  updateItemQuantity(index: number, event: Event) {
      const input = event.target as HTMLInputElement;
      let val = parseInt(input.value, 10);
      if (isNaN(val) || val < 1) val = 1;
      
      this.updateItemQuantityAtIndex(index, val);
  }

  updateItemColor(index: number, newColor: string) {
      this.items.update(current => {
          const updated = [...current];
          updated[index] = { ...updated[index], color: newColor };
          return updated;
      });
  }

  removeItem(index: number) {
      const itemToRemove = this.items()[index];
      this.items.update(current => {
          const updated = [...current];
          const removed = updated.splice(index, 1)[0];
          if (this.selectedFile() === removed.file) {
              this.selectedFile.set(null);
          }
          return updated;
      });
      this.itemRemoved.emit({ index, id: itemToRemove.id });
  }

  setFiles(files: File[], colors?: string[]) {
      const validItems: FormItem[] = [];
      files.forEach((file, i) => {
          const color = (colors && colors[i]) ? colors[i] : 'Black';
          validItems.push({ file, quantity: 1, color: color });
      });

      if (validItems.length > 0) {
          this.items.set(validItems);
          this.form.get('itemsTouched')?.setValue(true);
          // Auto select last added
          this.selectedFile.set(validItems[validItems.length - 1].file);
      }
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
          if (settings.layerHeightMm >= 0.28) patch.quality = 'draft';
          else if (settings.layerHeightMm <= 0.12) patch.quality = 'high';
          else patch.quality = 'standard';
          
          patch.layerHeight = settings.layerHeightMm;
      }
      
      if (settings.nozzleDiameterMm) patch.nozzleDiameter = settings.nozzleDiameterMm;
      if (settings.infillPercent) patch.infillDensity = settings.infillPercent;
      if (settings.infillPattern) patch.infillPattern = settings.infillPattern;
      if (settings.supportsEnabled !== undefined) patch.supportEnabled = settings.supportsEnabled;
      if (settings.notes) patch.notes = settings.notes;

      this.form.patchValue(patch);
  }

  onSubmit() {
    console.log('UploadFormComponent: onSubmit triggered');
    console.log('Form Valid:', this.form.valid, 'Items:', this.items().length);
    
    if (this.form.valid && this.items().length > 0) {
      console.log('UploadFormComponent: Emitting submitRequest', this.form.value);
      this.submitRequest.emit({
        ...this.form.value,
        items: this.items(), // Pass the items array explicitly AFTER form value to prevent overwrite
        mode: this.mode()
      });
    } else {
      console.warn('UploadFormComponent: Form Invalid or No Items');
      console.log('Form Errors:', this.form.errors);
      Object.keys(this.form.controls).forEach(key => {
          const control = this.form.get(key);
          if (control?.invalid) {
              console.log('Invalid Control:', key, control.errors, 'Value:', control.value);
          }
      });
      this.form.markAllAsTouched();
      this.form.get('itemsTouched')?.setValue(true);
    }
  }
}
