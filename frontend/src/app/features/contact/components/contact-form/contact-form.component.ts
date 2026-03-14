import {
  Component,
  signal,
  effect,
  inject,
  OnDestroy,
  PLATFORM_ID,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import {
  AppToggleSelectorComponent,
  ToggleOption,
} from '../../../../shared/components/app-toggle-selector/app-toggle-selector.component';
import { QuoteEstimatorService } from '../../../calculator/services/quote-estimator.service';
import { QuoteRequestService } from '../../../../core/services/quote-request.service';
import { LanguageService } from '../../../../core/services/language.service';
import { SuccessStateComponent } from '../../../../shared/components/success-state/success-state.component';

interface FilePreview {
  file: File;
  url?: string;
  type: 'image' | 'video' | 'pdf' | '3d' | 'document' | 'other';
}

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    AppInputComponent,
    AppButtonComponent,
    AppToggleSelectorComponent,
    SuccessStateComponent,
  ],
  templateUrl: './contact-form.component.html',
  styleUrl: './contact-form.component.scss',
})
export class ContactFormComponent implements OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  form: FormGroup;
  sent = signal(false);
  files = signal<FilePreview[]>([]);
  readonly acceptedFormats =
    '.jpg,.jpeg,.png,.webp,.gif,.bmp,.svg,.heic,.heif,.pdf,.stl,.step,.stp,.3mf,.obj,.iges,.igs,.dwg,.dxf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.rtf,.csv,.mp4,.mov,.avi,.mkv,.webm,.m4v,.wmv';

  get isCompany(): boolean {
    return this.form.get('isCompany')?.value;
  }

  requestTypes = [
    { value: 'custom', label: 'CONTACT.REQ_TYPE_CUSTOM' },
    { value: 'series', label: 'CONTACT.REQ_TYPE_SERIES' },
    { value: 'consult', label: 'CONTACT.REQ_TYPE_CONSULT' },
    { value: 'question', label: 'CONTACT.REQ_TYPE_QUESTION' },
  ];
  customerTypeOptions: ToggleOption[] = [
    { label: 'CONTACT.TYPE_PRIVATE', value: false },
    { label: 'CONTACT.TYPE_COMPANY', value: true },
  ];

  private quoteRequestService = inject(QuoteRequestService);
  readonly languageService = inject(LanguageService);

  constructor(
    private fb: FormBuilder,
    private translate: TranslateService,
    private estimator: QuoteEstimatorService,
  ) {
    this.form = this.fb.group({
      requestType: ['custom', Validators.required],
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      message: ['', Validators.required],
      isCompany: [false],
      companyName: [''],
      referencePerson: [''],
      acceptLegal: [false, Validators.requiredTrue],
    });

    // Handle conditional validation for Company fields
    this.form.get('isCompany')?.valueChanges.subscribe((isCompany) => {
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
        message: pending.message,
      });

      // Process files
      const filePreviews: FilePreview[] = pending.files.map((f) => {
        const type = this.getFileType(f);
        return {
          file: f,
          type,
          url:
            this.isBrowser && this.shouldCreatePreview(type)
              ? URL.createObjectURL(f)
              : undefined,
        };
      });
      this.files.set(filePreviews);
    }
  }

  ngOnDestroy(): void {
    this.revokeAllPreviewUrls();
  }

  setCompanyMode(isCompany: boolean) {
    this.form.patchValue({ isCompany });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) this.handleFiles(Array.from(input.files));
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer?.files)
      this.handleFiles(Array.from(event.dataTransfer.files));
  }

  handleFiles(newFiles: File[]) {
    const currentFiles = this.files();
    const blockedCompressed = newFiles.filter((file) =>
      this.isCompressedFile(file),
    );
    if (blockedCompressed.length > 0) {
      alert(this.translate.instant('CONTACT.ERR_COMPRESSED_FILES'));
    }

    const allowedFiles = newFiles.filter(
      (file) => !this.isCompressedFile(file),
    );
    if (allowedFiles.length === 0) return;

    if (currentFiles.length + allowedFiles.length > 15) {
      alert(this.translate.instant('CONTACT.ERR_MAX_FILES'));
      return;
    }

    allowedFiles.forEach((file) => {
      const type = this.getFileType(file);
      const preview: FilePreview = {
        file,
        type,
        url:
          this.isBrowser && this.shouldCreatePreview(type)
            ? URL.createObjectURL(file)
            : undefined,
      };
      this.files.update((files) => [...files, preview]);
    });
  }

  removeFile(index: number) {
    this.files.update((files) => {
      const fileToRemove = files[index];
      if (fileToRemove) this.revokePreviewUrl(fileToRemove);
      return files.filter((_, i) => i !== index);
    });
  }

  getFileType(
    file: File,
  ): 'image' | 'video' | 'pdf' | '3d' | 'document' | 'other' {
    const ext = this.getExtension(file.name);

    if (
      file.type.startsWith('image/') ||
      [
        'jpg',
        'jpeg',
        'png',
        'webp',
        'gif',
        'bmp',
        'svg',
        'heic',
        'heif',
      ].includes(ext)
    ) {
      return 'image';
    }
    if (
      file.type.startsWith('video/') ||
      ['mp4', 'mov', 'avi', 'mkv', 'webm', 'm4v', 'wmv'].includes(ext)
    ) {
      return 'video';
    }
    if (file.type === 'application/pdf' || ext === 'pdf') return 'pdf';
    if (
      [
        'stl',
        'step',
        'stp',
        '3mf',
        'obj',
        'iges',
        'igs',
        'dwg',
        'dxf',
      ].includes(ext)
    )
      return '3d';
    if (
      [
        'doc',
        'docx',
        'xls',
        'xlsx',
        'ppt',
        'pptx',
        'txt',
        'rtf',
        'csv',
      ].includes(ext)
    )
      return 'document';
    return 'other';
  }

  onSubmit() {
    if (this.form.valid) {
      const formVal = this.form.value;
      const isCompany = formVal.isCompany;

      const requestDto: any = {
        requestType: formVal.requestType,
        customerType: isCompany ? 'BUSINESS' : 'PRIVATE',
        language: this.languageService.selectedLang(),
        email: formVal.email,
        phone: formVal.phone,
        message: formVal.message,
        acceptTerms: formVal.acceptLegal,
        acceptPrivacy: formVal.acceptLegal,
      };

      if (isCompany) {
        requestDto.companyName = formVal.companyName;
        requestDto.contactPerson = formVal.referencePerson;
      } else {
        requestDto.name = formVal.name;
      }

      this.quoteRequestService
        .createRequest(
          requestDto,
          this.files().map((f) => f.file),
        )
        .subscribe({
          next: () => {
            this.sent.set(true);
          },
          error: (err) => {
            console.error('Submission failed', err);
            alert(this.translate.instant('CONTACT.ERROR_SUBMIT'));
          },
        });
    } else {
      this.form.markAllAsTouched();
    }
  }

  resetForm() {
    this.sent.set(false);
    this.form.reset({ requestType: 'custom', isCompany: false });
    this.revokeAllPreviewUrls();
    this.files.set([]);
  }

  private getExtension(fileName: string): string {
    const index = fileName.lastIndexOf('.');
    return index > -1 ? fileName.substring(index + 1).toLowerCase() : '';
  }

  private shouldCreatePreview(type: FilePreview['type']): boolean {
    return type === 'image' || type === 'video';
  }

  private isCompressedFile(file: File): boolean {
    const ext = this.getExtension(file.name);
    const compressedExtensions = [
      'zip',
      'rar',
      '7z',
      'tar',
      'gz',
      'tgz',
      'bz2',
      'tbz2',
      'xz',
      'txz',
      'zst',
    ];
    const compressedMimeTypes = [
      'application/zip',
      'application/x-zip-compressed',
      'application/x-rar-compressed',
      'application/vnd.rar',
      'application/x-7z-compressed',
      'application/gzip',
      'application/x-gzip',
      'application/x-tar',
      'application/x-bzip2',
      'application/x-xz',
      'application/zstd',
      'application/x-zstd',
    ];
    return (
      compressedExtensions.includes(ext) ||
      compressedMimeTypes.includes((file.type || '').toLowerCase())
    );
  }

  private revokePreviewUrl(file: FilePreview): void {
    if (!this.isBrowser) {
      return;
    }
    if (file.url?.startsWith('blob:')) {
      URL.revokeObjectURL(file.url);
    }
  }

  private revokeAllPreviewUrls(): void {
    this.files().forEach((file) => this.revokePreviewUrl(file));
  }
}
