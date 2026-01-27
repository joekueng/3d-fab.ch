import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PrintService } from '../../print.service';
import { StlViewerComponent } from '../../common/stl-viewer/stl-viewer.component';

@Component({
  selector: 'app-advanced-quote',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StlViewerComponent],
  templateUrl: './advanced-quote.component.html',
  styleUrls: ['./advanced-quote.component.scss']
})
export class AdvancedQuoteComponent {
  printService = inject(PrintService);
  
  selectedFile: File | null = null;
  isDragOver = false;
  isCalculating = false;
  quoteResult: any = null;

  // Parameters
  params = {
    layerHeight: '0.20',
    infill: 15,
    walls: 2,
    topBottom: 3,
    material: 'PLA'
  };

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

  calculate() {
    if (!this.selectedFile) return;

    this.isCalculating = true;

    // Use PrintService
    this.printService.calculateQuote(this.selectedFile, this.params)
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
