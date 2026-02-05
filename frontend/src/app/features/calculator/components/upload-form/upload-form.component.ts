import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../../shared/components/app-select/app-select.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';
import { QuoteRequest } from '../../services/quote-estimator.service';

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
             <button type="button" class="btn-clear" (click)="clearFiles()">
                X
             </button>
          </div>
          <div class="file-list">
             @for (f of files(); track f.name) {
                <div class="file-item" [class.active]="f === selectedFile()" (click)="selectFile(f)">
                   {{ f.name }}
                </div>
             }
          </div>
        } @else {
            <app-dropzone 
            [label]="'CALC.UPLOAD_LABEL' | translate" 
            [subtext]="'CALC.UPLOAD_SUB' | translate"
            [accept]="acceptedFormats"
            [multiple]="true"
            (filesDropped)="onFilesDropped($event)">
            </app-dropzone>
        }
        
        @if (form.get('files')?.invalid && form.get('files')?.touched) {
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

      <app-input
        formControlName="quantity"
        type="number"
        [label]="'CALC.QUANTITY' | translate"
      ></app-input>

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
        <!-- Progress Bar (Only when loading) -->
        @if (loading()) {
            <div class="progress-container">
                <div class="progress-bar">
                    <div class="progress-fill"></div>
                </div>
                <!-- <p class="progress-text">Uploading & Analyzing...</p> -->
            </div>
        }

        <app-button 
          type="submit" 
          [disabled]="form.invalid || loading()" 
          [fullWidth]="true">
          {{ loading() ? 'Processing...' : ('CALC.CALCULATE' | translate) }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .section { margin-bottom: var(--space-6); }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-4); }
    .actions { margin-top: var(--space-6); }
    .error-msg { color: var(--color-danger-500); font-size: 0.875rem; margin-top: var(--space-2); text-align: center; }
    
    .viewer-wrapper { position: relative; margin-bottom: var(--space-4); }
    .btn-clear {
        position: absolute;
        top: 10px;
        right: 10px;
        background: rgba(0,0,0,0.5);
        color: white;
        border: none;
        width: 32px;
        height: 32px;
        border-radius: 50%;
        cursor: pointer;
        z-index: 10;
        &:hover { background: rgba(0,0,0,0.7); }
    }
    
    .file-list {
        display: flex;
        gap: var(--space-2);
        overflow-x: auto;
        padding-bottom: var(--space-2);
    }
    .file-item {
        padding: 0.5rem 1rem;
        background: var(--color-neutral-100);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        font-size: 0.85rem;
        cursor: pointer;
        white-space: nowrap;
        &:hover { background: var(--color-neutral-200); }
        &.active { 
            border-color: var(--color-brand); 
            background: rgba(250, 207, 10, 0.1); 
            font-weight: 600;
        }
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
        /* padding: var(--space-2); */
        /* background: var(--color-neutral-100); */
        /* border-radius: var(--radius-md); */
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
        animation: progress 2s ease-in-out infinite;
    }
    .progress-text { font-size: 0.875rem; color: var(--color-text-muted); }
    
    @keyframes progress {
        0% { width: 0%; transform: translateX(-100%); }
        50% { width: 100%; transform: translateX(0); }
        100% { width: 100%; transform: translateX(100%); }
    }
  `]
})
export class UploadFormComponent {
  mode = input<'easy' | 'advanced'>('easy');
  loading = input<boolean>(false);
  submitRequest = output<QuoteRequest>();

  form: FormGroup;
  
  files = signal<File[]>([]);
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
      files: [[], Validators.required],
      material: ['PLA', Validators.required],
      quality: ['Standard', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
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
    const validFiles: File[] = [];
    let hasError = false;

    for (const file of newFiles) {
        if (file.size > MAX_SIZE) {
            hasError = true;
        } else {
            validFiles.push(file);
        }
    }

    if (hasError) {
        alert("Alcuni file superano il limite di 200MB e non sono stati aggiunti.");
    }

    if (validFiles.length > 0) {
        this.files.update(current => [...current, ...validFiles]);
        this.form.patchValue({ files: this.files() });
        this.form.get('files')?.markAsTouched();
        this.selectedFile.set(validFiles[validFiles.length - 1]);
    }
  }

  selectFile(file: File) {
      this.selectedFile.set(file);
  }

  clearFiles() {
      this.files.set([]);
      this.selectedFile.set(null);
      this.form.patchValue({ files: [] });
  }

  onSubmit() {
    if (this.form.valid) {
      this.submitRequest.emit({
        ...this.form.value,
        mode: this.mode()
      });
    } else {
      this.form.markAllAsTouched();
    }
  }
}
