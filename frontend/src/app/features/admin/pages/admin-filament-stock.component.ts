import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { AdminFilamentStockRow, AdminOperationsService } from '../services/admin-operations.service';

@Component({
  selector: 'app-admin-filament-stock',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-filament-stock.component.html',
  styleUrl: './admin-filament-stock.component.scss'
})
export class AdminFilamentStockComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);

  rows: AdminFilamentStockRow[] = [];
  loading = false;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadStock();
  }

  loadStock(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOperationsService.getFilamentStock().subscribe({
      next: (rows) => {
        this.rows = rows;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare lo stock filamenti.';
      }
    });
  }

  isLowStock(row: AdminFilamentStockRow): boolean {
    return Number(row.stockKg) < 1;
  }
}
