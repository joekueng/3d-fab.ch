import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dropzone',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div 
      class="dropzone"
      [class.dragover]="isDragOver()"
      (dragover)="onDragOver($event)"
      (dragleave)="onDragLeave($event)"
      (drop)="onDrop($event)"
      (click)="fileInput.click()"
    >
      <input #fileInput type="file" (change)="onFileSelected($event)" hidden [accept]="accept()" [multiple]="multiple()">
      
      <div class="content">
        <div class="icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-upload-cloud"><polyline points="16 16 12 12 8 16"></polyline><line x1="12" y1="12" x2="12" y2="21"></line><path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"></path><polyline points="16 16 12 12 8 16"></polyline></svg>
        </div>
        <p class="text">{{ label() }}</p>
        <p class="subtext">{{ subtext() }}</p>
        
        @if (fileNames().length > 0) {
            <div class="file-badges">
                @for (name of fileNames(); track name) {
                    <div class="file-badge">{{ name }}</div>
                }
            </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .dropzone {
      border: 2px dashed var(--color-border);
      border-radius: var(--radius-lg);
      padding: var(--space-8);
      text-align: center;
      cursor: pointer;
      transition: all 0.2s;
      background-color: var(--color-neutral-50);
      
      &:hover, &.dragover {
        border-color: var(--color-brand);
        background-color: var(--color-neutral-100);
      }
    }
    .icon { color: var(--color-brand); margin-bottom: var(--space-4); }
    .text { font-weight: 600; margin-bottom: var(--space-2); }
    .subtext { font-size: 0.875rem; color: var(--color-text-muted); }
    .file-badges {
        margin-top: var(--space-4);
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
        justify-content: center;
    }
    .file-badge {
        padding: var(--space-2) var(--space-4);
        background: var(--color-neutral-200);
        border-radius: var(--radius-md);
        font-weight: 600;
        color: var(--color-primary-700);
        font-size: 0.85rem;
    }
  `]
})
export class AppDropzoneComponent {
  label = input<string>('Drop files here or click to upload');
  subtext = input<string>('Supports .stl, .obj');
  accept = input<string>('.stl,.obj');
  multiple = input<boolean>(true);
  
  filesDropped = output<File[]>();
  
  isDragOver = signal(false);
  fileNames = signal<string[]>([]);

  onDragOver(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(true);
  }

  onDragLeave(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(e: DragEvent) {
    console.log('Drop event', e);
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(false);
    if (e.dataTransfer?.files.length) {
      this.handleFiles(Array.from(e.dataTransfer.files));
    }
  }

  onFileSelected(e: Event) {
    console.log('File selected', e);
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      this.handleFiles(Array.from(input.files));
    }
  }

  handleFiles(files: File[]) {
    const newNames = files.map(f => f.name);
    this.fileNames.update(current => [...current, ...newNames]);
    this.filesDropped.emit(files);
  }
}
