import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { AdminContactRequest, AdminOperationsService } from '../services/admin-operations.service';

@Component({
  selector: 'app-admin-contact-requests',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-contact-requests.component.html',
  styleUrl: './admin-contact-requests.component.scss'
})
export class AdminContactRequestsComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);

  requests: AdminContactRequest[] = [];
  loading = false;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadRequests();
  }

  loadRequests(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOperationsService.getContactRequests().subscribe({
      next: (requests) => {
        this.requests = requests;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le richieste di contatto.';
      }
    });
  }
}
