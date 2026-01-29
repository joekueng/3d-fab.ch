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
  selectedStrength: 'standard' | 'strong' | 'ultra' = 'standard';
  isDragOver = false;
  isCalculating = false;
  quoteResult: any = null;
  private strengthToSettings: Record<'standard' | 'strong' | 'ultra', { infill_density: number; quality: 'draft' | 'standard' | 'fine' }> = {
    standard: { infill_density: 15, quality: 'standard' },
    strong: { infill_density: 30, quality: 'standard' },
    ultra: { infill_density: 50, quality: 'standard' }
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

  selectStrength(strength: 'standard' | 'strong' | 'ultra') {
    this.selectedStrength = strength;
  }

  calculate() {
    if (!this.selectedFile) return;

    this.isCalculating = true;
    
    const settings = this.strengthToSettings[this.selectedStrength];

    this.printService.calculateQuote(this.selectedFile, {
      quality: settings.quality,
      infill_density: settings.infill_density
    })
      .subscribe({
        next: (res) => {
          if (res?.success) {
            this.quoteResult = res.data;
          } else {
            console.error('Quote API returned error:', res?.error);
            alert('Calculation failed: ' + (res?.error || 'Unknown error'));
            this.quoteResult = null;
          }
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
