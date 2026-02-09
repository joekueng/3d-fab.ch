import { Component, input, output, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../../shared/components/app-select/app-select.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';
import { ColorSelectorComponent } from '../../../../shared/components/color-selector/color-selector.component';
import { QuoteRequest } from '../../services/quote-estimator.service';
import { getColorHex } from '../../../../core/constants/colors.const';

interface FormItem {
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
export class UploadFormComponent {
  mode = input<'easy' | 'advanced'>('easy');
  loading = input<boolean>(false);
  uploadProgress = input<number>(0);
  submitRequest = output<QuoteRequest>();

  form: FormGroup;
  
  items = signal<FormItem[]>([]);
  selectedFile = signal<File | null>(null);

  materials = [
    { label: 'PLA (Standard)', value: 'PLA' },
    { label: 'PETG (Resistente)', value: 'PETG' },
    { label: 'TPU (Flessibile)', value: 'TPU' }
  ];

  qualities = [
    { label: 'Bozza (Fast)', value: 'Draft' },
    { label: 'Standard', value: 'Standard' },
    { label: 'Alta definizione', value: 'High' }
  ];

  nozzleDiameters = [
      { label: '0.2 mm (+2 CHF)', value: 0.2 },
      { label: '0.4 mm (Standard)', value: 0.4 },
      { label: '0.6 mm (+2 CHF)', value: 0.6 },
      { label: '0.8 mm (+2 CHF)', value: 0.8 }
  ];
  
  infillPatterns = [
      { label: 'Grid', value: 'grid' },
      { label: 'Gyroid', value: 'gyroid' },
      { label: 'Cubic', value: 'cubic' },
      { label: 'Triangles', value: 'triangles' }
  ];

  layerHeights = [
      { label: '0.08 mm', value: 0.08 },
      { label: '0.12 mm (High Quality - Slow)', value: 0.12 },
      { label: '0.16 mm', value: 0.16 },
      { label: '0.20 mm (Standard)', value: 0.20 },
      { label: '0.24 mm', value: 0.24 },
      { label: '0.28 mm', value: 0.28 }
  ];
  
  acceptedFormats = '.stl,.3mf,.step,.stp,.obj,.amf,.ply,.igs,.iges';

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      itemsTouched: [false], // Hack to track touched state for custom items list
      material: ['PLA', Validators.required],
      quality: ['Standard', Validators.required],
      // Print Speed removed
      notes: [''],
      // Advanced fields
      // Color removed from global form
      infillDensity: [20, [Validators.min(0), Validators.max(100)]],
      layerHeight: [0.2, [Validators.min(0.05), Validators.max(1.0)]],
      nozzleDiameter: [0.4, Validators.required],
      infillPattern: ['grid'],
      supportEnabled: [false]
    });
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
          return getColorHex(item.color);
      }
      return '#facf0a';
  }

  updateItemQuantity(index: number, event: Event) {
      const input = event.target as HTMLInputElement;
      let val = parseInt(input.value, 10);
      if (isNaN(val) || val < 1) val = 1;
      
      this.items.update(current => {
          const updated = [...current];
          updated[index] = { ...updated[index], quantity: val };
          return updated;
      });
  }

  updateItemColor(index: number, newColor: string) {
      this.items.update(current => {
          const updated = [...current];
          updated[index] = { ...updated[index], color: newColor };
          return updated;
      });
  }

  removeItem(index: number) {
      this.items.update(current => {
          const updated = [...current];
          const removed = updated.splice(index, 1)[0];
          if (this.selectedFile() === removed.file) {
              this.selectedFile.set(null);
          }
          return updated;
      });
  }

  onSubmit() {
    if (this.form.valid && this.items().length > 0) {
      this.submitRequest.emit({
        items: this.items(), // Pass the items array including colors
        ...this.form.value,
        mode: this.mode()
      });
    } else {
      this.form.markAllAsTouched();
      this.form.get('itemsTouched')?.setValue(true);
    }
  }
}
