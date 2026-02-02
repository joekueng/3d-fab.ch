import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppSelectComponent } from '../../../../shared/components/app-select/app-select.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { QuoteRequest } from '../../services/quote-estimator.service';

@Component({
  selector: 'app-upload-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppInputComponent, AppSelectComponent, AppDropzoneComponent, AppButtonComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      
      <div class="section">
        <app-dropzone 
          [label]="'CALC.UPLOAD_LABEL' | translate" 
          [subtext]="'CALC.UPLOAD_SUB' | translate"
          (fileDropped)="onFileDropped($event)">
        </app-dropzone>
        @if (form.get('file')?.invalid && form.get('file')?.touched) {
          <div class="error-msg">File required</div>
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
        <app-input
          formControlName="notes"
          [label]="'CALC.NOTES' | translate"
          placeholder="Specific instructions..."
        ></app-input>
      }

      <div class="actions">
        <app-button 
          type="submit" 
          [disabled]="form.invalid || loading()" 
          [fullWidth]="true">
          {{ loading() ? '...' : ('CALC.CALCULATE' | translate) }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .section { margin-bottom: var(--space-6); }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-4); }
    .actions { margin-top: var(--space-6); }
    .error-msg { color: var(--color-danger-500); font-size: 0.875rem; margin-top: var(--space-2); text-align: center; }
  `]
})
export class UploadFormComponent {
  clientType = input<'business' | 'private'>('private');
  mode = input<'easy' | 'advanced'>('easy');
  loading = input<boolean>(false);
  submitRequest = output<QuoteRequest>();

  form: FormGroup;
  
  materials = [
    { label: 'PLA (Standard)', value: 'PLA' },
    { label: 'PETG (Durable)', value: 'PETG' },
    { label: 'TPU (Flexible)', value: 'TPU' }
  ];

  qualities = [
    { label: 'Draft (Fast)', value: 'Draft' },
    { label: 'Standard', value: 'Standard' },
    { label: 'High Detail', value: 'High' }
  ];

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      file: [null, Validators.required],
      material: ['PLA', Validators.required],
      quality: ['Standard', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
      notes: ['']
    });
  }

  onFileDropped(file: File) {
    this.form.patchValue({ file });
    this.form.get('file')?.markAsTouched();
  }

  onSubmit() {
    if (this.form.valid) {
      this.submitRequest.emit({
        ...this.form.value,
        clientType: this.clientType(),
        mode: this.mode()
      });
    } else {
      this.form.markAllAsTouched();
    }
  }
}
