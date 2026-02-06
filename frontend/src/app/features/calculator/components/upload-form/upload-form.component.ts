import { Component, input, output, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../../shared/components/app-select/app-select.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';
import { QuoteRequest } from '../../services/quote-estimator.service';

interface FormItem {
    file: File;
    quantity: number;
}

@Component({
  selector: 'app-upload-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppInputComponent, AppSelectComponent, AppDropzoneComponent, AppButtonComponent, StlViewerComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      
      <div class="section">
        @if (selectedFile()) {
          <div class="viewer-wrapper">
             <app-stl-viewer [file]="selectedFile()"></app-stl-viewer>
             <!-- Close button removed as requested -->
          </div>
        }
        
        <!-- Initial Dropzone (Visible only when no files) -->
        @if (items().length === 0) {
            <app-dropzone 
                [label]="'CALC.UPLOAD_LABEL' | translate" 
                [subtext]="'CALC.UPLOAD_SUB' | translate"
                [accept]="acceptedFormats"
                [multiple]="true"
                (filesDropped)="onFilesDropped($event)">
            </app-dropzone>
        }

        <!-- New File List with Details -->
        @if (items().length > 0) {
            <div class="items-grid">
                @for (item of items(); track item.file.name; let i = $index) {
                    <div class="file-card" [class.active]="item.file === selectedFile()" (click)="selectFile(item.file)">
                        <div class="card-header">
                            <span class="file-name" [title]="item.file.name">{{ item.file.name }}</span>
                        </div>
                        
                        <div class="card-body">
                             <div class="qty-group">
                                <label>Qtà</label>
                                <input 
                                    type="number" 
                                    min="1" 
                                    [value]="item.quantity"
                                    (change)="updateItemQuantity(i, $event)"
                                    class="qty-input"
                                    (click)="$event.stopPropagation()">
                            </div>
                            
                            <button type="button" class="btn-remove" (click)="removeItem(i); $event.stopPropagation()" title="Remove file">
                                X
                            </button>
                        </div>
                    </div>
                }
            </div>
            
            <!-- "Add Files" Button (Visible only when files exist) -->
            <div class="add-more-container">
                <input #additionalInput type="file" [accept]="acceptedFormats" multiple hidden (change)="onAdditionalFilesSelected($event)">
                
                <button type="button" class="btn-add-more" (click)="additionalInput.click()">
                    + {{ 'CALC.ADD_FILES' | translate }}
                </button>
            </div>
        }
        
        @if (items().length === 0 && form.get('itemsTouched')?.value) {
          <div class="error-msg">{{ 'CALC.ERR_FILE_REQUIRED' | translate }}</div>
        }
      </div>

      <div class="grid">
        <app-select
          formControlName="material"
          [label]="'CALC.MATERIAL' | translate"
          [options]="materials"
        ></app-select>

        <app-select
          formControlName="quality"
          [label]="'CALC.QUALITY' | translate"
          [options]="qualities"
        ></app-select>
      </div>
      
      <!-- Global quantity removed, now per item -->

      @if (mode() === 'advanced') {
        <div class="grid">
             <app-select
              formControlName="color"
              [label]="'CALC.COLOR' | translate"
              [options]="colors"
            ></app-select>
            
            <app-select
              formControlName="infillPattern"
              [label]="'CALC.PATTERN' | translate"
              [options]="infillPatterns"
            ></app-select>
        </div>

        <div class="grid">
            <app-input
              formControlName="infillDensity"
              type="number"
              [label]="'CALC.INFILL' | translate"
            ></app-input>
            
            <div class="checkbox-row">
                <input type="checkbox" formControlName="supportEnabled" id="support">
                <label for="support">{{ 'CALC.SUPPORT' | translate }}</label>
            </div>
        </div>

        <app-input
          formControlName="notes"
          [label]="'CALC.NOTES' | translate"
          placeholder="Istruzioni specifiche..."
        ></app-input>
      }

      <div class="actions">
        <!-- Progress Bar (Only when uploading i.e. progress < 100) -->
        @if (loading() && uploadProgress() < 100) {
            <div class="progress-container">
                <div class="progress-bar">
                    <div class="progress-fill" [style.width.%]="uploadProgress()"></div>
                </div>
            </div>
        }

        <app-button 
          type="submit" 
          [disabled]="form.invalid || items().length === 0 || loading()" 
          [fullWidth]="true">
          {{ loading() ? (uploadProgress() < 100 ? 'Uploading...' : 'Processing...') : ('CALC.CALCULATE' | translate) }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .section { margin-bottom: var(--space-6); }
    .grid { 
        display: grid; 
        grid-template-columns: 1fr; 
        gap: var(--space-4); 
        
        @media(min-width: 640px) {
            grid-template-columns: 1fr 1fr;
        }
    }
    .actions { margin-top: var(--space-6); }
    .error-msg { color: var(--color-danger-500); font-size: 0.875rem; margin-top: var(--space-2); text-align: center; }
    
    .viewer-wrapper { position: relative; margin-bottom: var(--space-4); }
    
    /* Grid Layout for Files */
    .items-grid {
        display: grid;
        grid-template-columns: 1fr;
        gap: var(--space-3);
        margin-top: var(--space-4);
        margin-bottom: var(--space-4);
        
        @media(min-width: 640px) {
            grid-template-columns: 1fr 1fr;
        }
    }
    
    .file-card {
        padding: var(--space-3);
        background: var(--color-neutral-100);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        transition: all 0.2s;
        cursor: pointer;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        
        &:hover { border-color: var(--color-neutral-300); }
        &.active {
             border-color: var(--color-brand);
             background: rgba(250, 207, 10, 0.05);
             box-shadow: 0 0 0 1px var(--color-brand);
        }
    }
    
    .card-header {
        overflow: hidden;
    }
    
    .file-name { 
        font-weight: 500; 
        font-size: 0.85rem; 
        color: var(--color-text);
        display: block;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    
    .card-body {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }
    
    .qty-group {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        label { font-size: 0.75rem; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
    }
    
    .qty-input {
        width: 40px;
        padding: 2px 4px;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        text-align: center;
        font-size: 0.9rem;
        background: white;
        &:focus { outline: none; border-color: var(--color-brand); }
    }
    
    .btn-remove {
        width: 24px;
        height: 24px;
        border-radius: 4px;
        border: 1px solid transparent; // var(--color-border);
        background: transparent; // white;
        color: var(--color-text-muted);
        font-weight: bold;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s;
        font-size: 0.9rem;
        
        &:hover { 
            background: var(--color-danger-100); 
            color: var(--color-danger-500);
            border-color: var(--color-danger-200);
        }
    }

    /* Prominent Add Button */
    .add-more-container {
        margin-top: var(--space-2);
    }
    
    .btn-add-more {
        width: 100%;
        padding: var(--space-3);
        background: var(--color-neutral-800);
        color: white;
        border: none;
        border-radius: var(--radius-md);
        font-weight: 600;
        font-size: 0.9rem;
        cursor: pointer;
        transition: all 0.2s;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        
        &:hover {
            background: var(--color-neutral-900);
            transform: translateY(-1px);
        }
        &:active { transform: translateY(0); }
    }

    .checkbox-row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        height: 100%;
        padding-top: var(--space-4);
        
        input[type="checkbox"] {
            width: 20px;
            height: 20px;
            accent-color: var(--color-brand);
        }
        label {
            font-weight: 500;
            cursor: pointer;
        }
    }

    /* Progress Bar */
    .progress-container {
        margin-bottom: var(--space-3);
        text-align: center;
        width: 100%;
    }
    .progress-bar {
        height: 4px;
        background: var(--color-border);
        border-radius: 2px;
        overflow: hidden;
        margin-bottom: 0;
        position: relative;
        width: 100%;
    }
    .progress-fill {
        height: 100%;
        background: var(--color-brand);
        width: 0%;
        transition: width 0.2s ease-out;
    }
  `]
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
  
  colors = [
      { label: 'Black', value: 'Black' },
      { label: 'White', value: 'White' },
      { label: 'Gray', value: 'Gray' },
      { label: 'Red', value: 'Red' },
      { label: 'Blue', value: 'Blue' },
      { label: 'Green', value: 'Green' },
      { label: 'Yellow', value: 'Yellow' }
  ];
  infillPatterns = [
      { label: 'Grid', value: 'grid' },
      { label: 'Gyroid', value: 'gyroid' },
      { label: 'Cubic', value: 'cubic' },
      { label: 'Triangles', value: 'triangles' }
  ];
  
  acceptedFormats = '.stl,.3mf,.step,.stp,.obj,.amf,.ply,.igs,.iges';

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      itemsTouched: [false], // Hack to track touched state for custom items list
      material: ['PLA', Validators.required],
      quality: ['Standard', Validators.required],
      notes: [''],
      // Advanced fields
      color: ['Black'],
      infillDensity: [20, [Validators.min(0), Validators.max(100)]],
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
            validItems.push({ file, quantity: 1 });
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
        items: this.items(), // Pass the items array
        ...this.form.value,
        mode: this.mode()
      });
    } else {
      this.form.markAllAsTouched();
      this.form.get('itemsTouched')?.setValue(true);
    }
  }
}
