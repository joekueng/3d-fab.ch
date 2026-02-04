import { Component, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';

interface FilePreview {
  file: File;
  url?: string;
  type: 'image' | 'pdf' | '3d' | 'other';
}

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppInputComponent, AppButtonComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      <!-- Request Type -->
      <div class="form-group">
        <label>{{ 'CONTACT.REQ_TYPE_LABEL' | translate }} *</label>
        <select formControlName="requestType" class="form-control">
          <option *ngFor="let type of requestTypes" [value]="type.value">
            {{ type.label | translate }}
          </option>
        </select>
      </div>

      <div class="row">
        <!-- Phone -->
        <app-input formControlName="email" type="email" label="Email *" [placeholder]="'CONTACT.PLACEHOLDER_EMAIL' | translate" class="col"></app-input>
        <!-- Phone -->
        <app-input formControlName="phone" type="tel" [label]="('CONTACT.PHONE' | translate)" [placeholder]="'CONTACT.PLACEHOLDER_PHONE' | translate" class="col"></app-input>
      </div>

      <!-- User Type Selector (Segmented Control) -->
      <div class="user-type-selector">
        <div class="type-option" [class.selected]="!isCompany" (click)="setCompanyMode(false)">
          {{ 'CONTACT.TYPE_PRIVATE' | translate }}
        </div>
        <div class="type-option" [class.selected]="isCompany" (click)="setCompanyMode(true)">
          {{ 'CONTACT.TYPE_COMPANY' | translate }}
        </div>
      </div>

      <!-- Personal Name (Only if NOT Company) -->
      <app-input *ngIf="!isCompany" formControlName="name" label="Nome *" [placeholder]="'CONTACT.PLACEHOLDER_NAME' | translate"></app-input>

      <!-- Company Fields (Only if Company) -->
      <div *ngIf="isCompany" class="company-fields">
        <app-input formControlName="companyName" [label]="('CONTACT.COMPANY_NAME' | translate) + ' *'" [placeholder]="'CONTACT.PLACEHOLDER_COMPANY' | translate"></app-input>
        <app-input formControlName="referencePerson" [label]="('CONTACT.REF_PERSON' | translate) + ' *'" [placeholder]="'CONTACT.PLACEHOLDER_REF_PERSON' | translate"></app-input>
      </div>
      
      <div class="form-group">
        <label>{{ 'CONTACT.LABEL_MESSAGE' | translate }}</label>
        <textarea formControlName="message" class="form-control" rows="4"></textarea>
      </div>

      <!-- File Upload Section -->
      <div class="form-group">
        <label>{{ 'CONTACT.UPLOAD_LABEL' | translate }}</label>
        <p class="hint">{{ 'CONTACT.UPLOAD_HINT' | translate }}</p>
        
        <div class="drop-zone" (click)="fileInput.click()" 
             (dragover)="onDragOver($event)" (drop)="onDrop($event)">
          <input #fileInput type="file" multiple (change)="onFileSelected($event)" hidden 
                 accept=".jpg,.jpeg,.png,.pdf,.stl,.step,.stp,.3mf,.obj">
          <p>{{ 'CONTACT.DROP_FILES' | translate }}</p>
        </div>

        <div class="file-grid" *ngIf="files().length > 0">
          <div class="file-item" *ngFor="let file of files(); let i = index">
            <button type="button" class="remove-btn" (click)="removeFile(i)">×</button>
            <img *ngIf="file.type === 'image'" [src]="file.url" class="preview-img">
            <div *ngIf="file.type !== 'image'" class="file-icon">
              <span *ngIf="file.type === 'pdf'">PDF</span>
              <span *ngIf="file.type === '3d'">3D</span>
            </div>
            <div class="file-name" [title]="file.file.name">{{ file.file.name }}</div>
          </div>
        </div>
      </div>

      <div class="actions">
        <app-button type="submit" [disabled]="form.invalid || sent()">
          {{ sent() ? ('CONTACT.MSG_SENT' | translate) : ('CONTACT.SEND' | translate) }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .form-group { display: flex; flex-direction: column; margin-bottom: var(--space-4); }
    label { font-size: 0.875rem; font-weight: 500; margin-bottom: var(--space-2); color: var(--color-text); }
    .hint { font-size: 0.75rem; color: var(--color-text-muted); margin-bottom: var(--space-2); }
    
    .form-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      width: 100%;
      background: var(--color-bg-card);
      color: var(--color-text);
      font-family: inherit;
      &:focus { outline: none; border-color: var(--color-brand); }
    }

    select.form-control {
      appearance: none;
      background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
      background-repeat: no-repeat;
      background-position: right 1rem center;
      background-size: 1em;
    }

    .row {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      margin-bottom: var(--space-4);
      @media(min-width: 768px) {
        flex-direction: row;
        .col { flex: 1; margin-bottom: 0; }
      }
    }
    
    app-input.col { width: 100%; }

    /* User Type Selector Styles */
    .user-type-selector {
      display: flex;
      background-color: var(--color-neutral-100);
      border-radius: var(--radius-md);
      padding: 4px;
      margin-bottom: var(--space-4);
      gap: 4px;
      width: 100%; /* Full width */
      max-width: 400px; /* Limit on desktop */
    }
    
    .type-option {
      flex: 1; /* Equal width */
      text-align: center;
      padding: 8px 16px;
      border-radius: var(--radius-sm);
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--color-text-muted);
      transition: all 0.2s ease;
      user-select: none;
      
      &:hover { color: var(--color-text); }
      
      &.selected {
        background-color: var(--color-brand);
        color: #000;
        font-weight: 600;
        box-shadow: 0 1px 2px rgba(0,0,0,0.05);
      }
    }

    .company-fields {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      padding-left: var(--space-4);
      border-left: 2px solid var(--color-border);
      margin-bottom: var(--space-4);
    }

    /* File Upload Styles */
    .drop-zone {
      border: 2px dashed var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-6);
      text-align: center;
      cursor: pointer;
      color: var(--color-text-muted);
      transition: all 0.2s;
      &:hover { border-color: var(--color-brand); color: var(--color-brand); }
    }

    .file-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
      gap: var(--space-3);
      margin-top: var(--space-3);
    }

    .file-item {
      position: relative;
      background: var(--color-neutral-100);
      border-radius: var(--radius-sm);
      padding: var(--space-2);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      aspect-ratio: 1;
      overflow: hidden;
    }
    
    .preview-img {
       width: 100%; height: 100%; object-fit: cover; position: absolute; top:0; left:0;
       border-radius: var(--radius-sm);
    }
    
    .file-icon {
       font-weight: 700; color: var(--color-text-muted); font-size: 0.8rem;
    }

    .file-name {
       font-size: 0.65rem; color: var(--color-text); white-space: nowrap; overflow: hidden;
       text-overflow: ellipsis; width: 100%; text-align: center; position: absolute; bottom: 2px;
       padding: 0 4px; z-index: 2; background: rgba(255,255,255,0.8);
    }
    
    .remove-btn {
      position: absolute; top: 2px; right: 2px; z-index: 10;
      background: rgba(0,0,0,0.5); color: white; border: none; border-radius: 50%;
      width: 18px; height: 18px; font-size: 12px; cursor: pointer;
      display: flex; align-items: center; justify-content: center; line-height: 1;
      &:hover { background: red; }
    }
  `]
})
export class ContactFormComponent {
  form: FormGroup;
  sent = signal(false);
  files = signal<FilePreview[]>([]);
  
  get isCompany(): boolean {
    return this.form.get('isCompany')?.value;
  }
  
  requestTypes = [
    { value: 'custom', label: 'CONTACT.REQ_TYPE_CUSTOM' },
    { value: 'series', label: 'CONTACT.REQ_TYPE_SERIES' },
    { value: 'consult', label: 'CONTACT.REQ_TYPE_CONSULT' },
    { value: 'question', label: 'CONTACT.REQ_TYPE_QUESTION' }
  ];

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      requestType: ['custom', Validators.required],
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      message: ['', Validators.required],
      isCompany: [false],
      companyName: [''],
      referencePerson: ['']
    });

    // Handle conditional validation for Company fields
    this.form.get('isCompany')?.valueChanges.subscribe(isCompany => {
      const nameControl = this.form.get('name');
      const companyNameControl = this.form.get('companyName');
      const refPersonControl = this.form.get('referencePerson');

      if (isCompany) {
        // Company Mode: Name not required / cleared, Company defaults required
        nameControl?.clearValidators();
        nameControl?.setValue(''); // Optional: clear value
        
        companyNameControl?.setValidators([Validators.required]);
        refPersonControl?.setValidators([Validators.required]);
      } else {
        // Private Mode: Name required
        nameControl?.setValidators([Validators.required]);
        
        companyNameControl?.clearValidators();
        refPersonControl?.clearValidators();
      }
      
      nameControl?.updateValueAndValidity();
      companyNameControl?.updateValueAndValidity();
      refPersonControl?.updateValueAndValidity();
    });
  }

  setCompanyMode(isCompany: boolean) {
    this.form.patchValue({ isCompany });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) this.handleFiles(Array.from(input.files));
  }

  onDragOver(event: DragEvent) {
    event.preventDefault(); event.stopPropagation();
  }

  onDrop(event: DragEvent) {
    event.preventDefault(); event.stopPropagation();
    if (event.dataTransfer?.files) this.handleFiles(Array.from(event.dataTransfer.files));
  }

  handleFiles(newFiles: File[]) {
    const currentFiles = this.files();
    if (currentFiles.length + newFiles.length > 15) {
      alert("Max 15 files limit reached.");
      return;
    }

    newFiles.forEach(file => {
      const type = this.getFileType(file);
      const preview: FilePreview = { file, type };

      if (type === 'image') {
        const reader = new FileReader();
        reader.onload = (e) => {
          preview.url = e.target?.result as string;
          this.files.update(files => [...files]); 
        };
        reader.readAsDataURL(file);
      }
      this.files.update(files => [...files, preview]);
    });
  }

  removeFile(index: number) {
    this.files.update(files => files.filter((_, i) => i !== index));
  }

  getFileType(file: File): 'image' | 'pdf' | '3d' | 'other' {
    if (file.type.startsWith('image/')) return 'image';
    if (file.type === 'application/pdf') return 'pdf';
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (['stl', 'step', 'stp', '3mf', 'obj'].includes(ext || '')) return '3d';
    return 'other';
  }

  onSubmit() {
    if (this.form.valid) {
      const formData = {
        ...this.form.value,
        files: this.files().map(f => f.file)
      };
      console.log('Form Submit:', formData);
      
      this.sent.set(true);
      setTimeout(() => {
        this.sent.set(false);
        this.form.reset({ requestType: 'custom', isCompany: false });
        this.files.set([]);
      }, 3000);
    } else {
      this.form.markAllAsTouched();
    }
  }
}
