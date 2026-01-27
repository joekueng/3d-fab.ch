// calculator.component.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

interface QuoteResponse {
  printer: string;
  print_time_formatted: string;
  material_grams: number;
  cost: {
    material: number;
    machine: number;
    energy: number;
    markup: number;
    total: number;
  };
}

@Component({
  selector: 'app-calculator',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './calculator.component.html',
  styleUrls: ['./calculator.component.scss']
})
export class CalculatorComponent {
  file: File | null = null;
  results: QuoteResponse | null = null;
  error = '';
  loading = false;

  constructor(private http: HttpClient) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.file = input.files[0];
      this.results = null;
      this.error = '';
    }
  }

  uploadAndCalculate(): void {
    if (!this.file) {
      this.error = 'Please select a file first.';
      return;
    }
    const formData = new FormData();
    formData.append('file', this.file);
    this.loading = true;
    this.error = '';
    this.results = null;

    this.http.post<QuoteResponse>('http://localhost:8000/calculate/stl', formData)
      .subscribe({
        next: res => {
          this.results = res;
          this.loading = false;
        },
        error: err => {
          console.error(err);
          this.error = err.error?.detail || "An error occurred during calculation.";
          this.loading = false;
        }
      });
  }
}