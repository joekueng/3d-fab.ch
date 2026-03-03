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
  accept = input<string>('.stl,.3mf,.step,.stp');
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
    const newNames = files.map((f) => f.name);
    this.fileNames.update((current) => [...current, ...newNames]);
    this.filesDropped.emit(files);
  }
}
