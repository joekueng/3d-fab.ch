import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-dropzone',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './app-dropzone.component.html',
  styleUrl: './app-dropzone.component.scss',
})
export class AppDropzoneComponent {
  label = input<string>('DROPZONE.DEFAULT_LABEL');
  subtext = input<string>('DROPZONE.DEFAULT_SUBTEXT');
  accept = input<string>('.stl,.3mf');
  multiple = input<boolean>(true);
  showFileNames = input(true);
  disabled = input(false);

  filesDropped = output<File[]>();

  isDragOver = signal(false);
  fileNames = signal<string[]>([]);

  onDragOver(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    if (!this.disabled()) this.isDragOver.set(true);
  }

  onDragLeave(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(e: DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(false);
    if (!this.disabled() && e.dataTransfer?.files.length) {
      this.handleFiles(Array.from(e.dataTransfer.files));
    }
  }

  onFileSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      this.handleFiles(Array.from(input.files));
      input.value = "";
    }
  }

  handleFiles(files: File[]) {
    if (this.disabled()) return;
    files = this.multiple() ? files : files.slice(0, 1);
    const newNames = files.map((f) => f.name);
    this.fileNames.update((current) => [...current, ...newNames]);
    this.filesDropped.emit(files);
  }
}
