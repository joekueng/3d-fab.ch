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

  // Selectable options (mapped to backend profile ids where needed)
  readonly materialOptions = [
    { value: 'pla_basic', label: 'PLA' },
    { value: 'petg_basic', label: 'PETG' },
    { value: 'abs_basic', label: 'ABS' },
    { value: 'tpu_95a', label: 'TPU 95A' }
  ];
  readonly colorOptions = [
    'Black',
    'White',
    'Gray',
    'Red',
    'Blue',
    'Green',
    'Yellow'
  ];
  readonly qualityOptions = [
    { value: 'draft', label: 'Draft' },
    { value: 'standard', label: 'Standard' },
    { value: 'fine', label: 'Fine' }
  ];
  readonly infillPatternOptions = [
    { value: 'grid', label: 'Grid' },
    { value: 'gyroid', label: 'Gyroid' },
    { value: 'cubic', label: 'Cubic' },
    { value: 'triangles', label: 'Triangles' },
    { value: 'rectilinear', label: 'Rectilinear' },
    { value: 'crosshatch', label: 'Crosshatch' },
    { value: 'zig-zag', label: 'Zig-zag' },
    { value: 'alignedrectilinear', label: 'Aligned Rectilinear' }
  ];

  // Parameters
  params = {
    filament: 'pla_basic',
    material_color: 'Black',
    quality: 'standard',
    infill_density: 15,
    infill_pattern: 'grid',
    support_enabled: false
  };

  get materialLabel(): string {
    const match = this.materialOptions.find(option => option.value === this.params.filament);
    return match ? match.label : this.params.filament;
  }

  get infillPatternLabel(): string {
    const match = this.infillPatternOptions.find(option => option.value === this.params.infill_pattern);
    return match ? match.label : this.params.infill_pattern;
  }

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
    this.printService.calculateQuote(this.selectedFile, {
        filament: this.params.filament,
        quality: this.params.quality,
        infill_density: this.params.infill_density,
        infill_pattern: this.params.infill_pattern,
        // Optional mappings if user selected overrides
        // layer_height: this.params.layer_height, 
        // support_enabled: this.params.support_enabled
    })
      .subscribe({
        next: (res) => {
          console.log('API Response:', res);
          if (res.success) {
            this.quoteResult = res.data;
            console.log('Quote Result set to:', this.quoteResult);
          } else {
             console.error('API succeeded but returned error flag:', res.error);
             alert('Error: ' + res.error);
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
