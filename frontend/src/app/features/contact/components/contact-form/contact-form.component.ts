import { Component, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { QuoteEstimatorService } from '../../../calculator/services/quote-estimator.service';

interface FilePreview {
  file: File;
  url?: string;
  type: 'image' | 'pdf' | '3d' | 'other';
}

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, AppInputComponent, AppButtonComponent],
  templateUrl: './contact-form.component.html',
  styleUrl: './contact-form.component.scss'
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

  constructor(
      private fb: FormBuilder, 
      private translate: TranslateService,
      private estimator: QuoteEstimatorService
  ) {
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
    
    // Check for pending consultation data
    effect(() => {
        // Use timeout or run in constructor to ensure dependency availability? 
        // Actually best in constructor or ngOnInit. Let's stick to constructor logic but executed immediately.
    });
    
    const pending = this.estimator.getPendingConsultation();
    if (pending) {
        this.form.patchValue({
            requestType: 'consult',
            message: pending.message
        });
        
        // Process files
        const filePreviews: FilePreview[] = [];
        pending.files.forEach(f => {
            filePreviews.push({ file: f, type: this.getFileType(f) });
        });
        this.files.set(filePreviews);
    }
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
      alert(this.translate.instant('CONTACT.ERR_MAX_FILES'));
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
    } else {
      this.form.markAllAsTouched();
    }
  }

  resetForm() {
    this.sent.set(false);
    this.form.reset({ requestType: 'custom', isCompany: false });
    this.files.set([]);
  }
}
