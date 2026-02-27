import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { AdminOperationsService, AdminQuoteSession } from '../services/admin-operations.service';

@Component({
  selector: 'app-admin-sessions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-sessions.component.html',
  styleUrl: './admin-sessions.component.scss'
})
export class AdminSessionsComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);

  sessions: AdminQuoteSession[] = [];
  loading = false;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOperationsService.getSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le sessioni.';
      }
    });
  }
}
