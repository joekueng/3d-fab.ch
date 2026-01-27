import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PrintService } from '../../print.service';
import { StlViewerComponent } from '../../common/stl-viewer/stl-viewer.component';

@Component({
  selector: 'app-basic-quote',
  standalone: true,
  imports: [CommonModule, RouterLink, StlViewerComponent],
  templateUrl: './basic-quote.component.html',
  styleUrls: ['./basic-quote.component.scss']
})
export class BasicQuoteComponent {
  printService = inject(PrintService);
  
  selectedFile: File | null = null;
  selectedStrength: 'fragile' | 'medium' | 'resistant' = 'medium';
  isDragOver = false;
  isCalculating = false;
  quoteResult: any = null;

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      if (files[0].name.toLowerCase().endsWith('.stl')) {
        this.selectedFile = files[0];
        this.quoteResult = null;
      } else {
        alert('Please upload an STL file.');
      }
    }
  }

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (file) {
      this.selectedFile = file;
      this.quoteResult = null;
    }
  }

  removeFile(event: Event) {
    event.stopPropagation();
    this.selectedFile = null;
    this.quoteResult = null;
  }

  selectStrength(strength: 'fragile' | 'medium' | 'resistant') {
    this.selectedStrength = strength;
  }

  calculate() {
    if (!this.selectedFile) return;

    this.isCalculating = true;
    
    this.printService.calculateQuote(this.selectedFile, { strength: this.selectedStrength })
      .subscribe({
        next: (res) => {
          this.quoteResult = res;
          this.isCalculating = false;
        },
        error: (err) => {
          console.error(err);
          alert('Calculation failed: ' + (err.error?.detail || err.message));
          this.isCalculating = false;
        }
      });
  }
}
