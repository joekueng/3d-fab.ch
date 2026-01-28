import { Component, inject, OnInit } from '@angular/core';
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
export class AdvancedQuoteComponent implements OnInit {
  printService = inject(PrintService);
  
  selectedFile: File | null = null;
  isDragOver = false;
  isCalculating = false;
  quoteResult: any = null;

  // Available Profiles
  machines: string[] = [];
  filaments: string[] = [];
  qualities: string[] = [];

  // Parameters
  params = {
    machine: 'bambu_a1',
    filament: 'pla_basic',
    quality: 'standard',
    layer_height: null,
    infill_density: 15,
    support_enabled: false
  };

  ngOnInit() {
    this.printService.getProfiles().subscribe(data => {
      this.machines = data.machines;
      this.filaments = data.filaments;
      this.qualities = data.processes;
    });
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
        machine: this.params.machine,
        filament: this.params.filament,
        quality: this.params.quality,
        infill_density: this.params.infill_density,
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
