import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import {
  AdminOperationsService,
  AdminQuoteSession,
  AdminQuoteSessionDetail,
} from '../services/admin-operations.service';

@Component({
  selector: 'app-admin-sessions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-sessions.component.html',
  styleUrl: './admin-sessions.component.scss',
})
export class AdminSessionsComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);

  sessions: AdminQuoteSession[] = [];
  sessionDetailsById: Record<string, AdminQuoteSessionDetail | undefined> = {};
  loading = false;
  deletingSessionIds = new Set<string>();
  loadingDetailSessionIds = new Set<string>();
  expandedSessionId: string | null = null;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;
    this.adminOperationsService.getSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le sessioni.';
      },
    });
  }

  deleteSession(session: AdminQuoteSession): void {
    if (this.deletingSessionIds.has(session.id)) {
      return;
    }

    const confirmed = window.confirm(
      `Vuoi eliminare la sessione ${session.id}? Questa azione non si puo annullare.`,
    );
    if (!confirmed) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.deletingSessionIds.add(session.id);

    this.adminOperationsService.deleteSession(session.id).subscribe({
      next: () => {
        this.sessions = this.sessions.filter((item) => item.id !== session.id);
        this.deletingSessionIds.delete(session.id);
        this.successMessage = 'Sessione eliminata.';
      },
      error: (err) => {
        this.deletingSessionIds.delete(session.id);
        this.errorMessage = this.extractErrorMessage(
          err,
          'Impossibile eliminare la sessione.',
        );
      },
    });
  }

  isDeletingSession(sessionId: string): boolean {
    return this.deletingSessionIds.has(sessionId);
  }

  toggleSessionDetail(session: AdminQuoteSession): void {
    if (this.expandedSessionId === session.id) {
      this.expandedSessionId = null;
      return;
    }

    this.expandedSessionId = session.id;
    if (
      this.sessionDetailsById[session.id] ||
      this.loadingDetailSessionIds.has(session.id)
    ) {
      return;
    }

    this.loadingDetailSessionIds.add(session.id);
    this.adminOperationsService.getSessionDetail(session.id).subscribe({
      next: (detail) => {
        this.sessionDetailsById = {
          ...this.sessionDetailsById,
          [session.id]: detail,
        };
        this.loadingDetailSessionIds.delete(session.id);
      },
      error: (err) => {
        this.loadingDetailSessionIds.delete(session.id);
        this.errorMessage = this.extractErrorMessage(
          err,
          'Impossibile caricare il dettaglio sessione.',
        );
      },
    });
  }

  isDetailOpen(sessionId: string): boolean {
    return this.expandedSessionId === sessionId;
  }

  isLoadingDetail(sessionId: string): boolean {
    return this.loadingDetailSessionIds.has(sessionId);
  }

  getSessionDetail(sessionId: string): AdminQuoteSessionDetail | undefined {
    return this.sessionDetailsById[sessionId];
  }

  formatPrintTime(seconds?: number): string {
    if (!seconds || seconds <= 0) {
      return '-';
    }
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return `${hours}h ${minutes}m`;
  }

  private extractErrorMessage(error: unknown, fallback: string): string {
    const err = error as { error?: { message?: string } };
    return err?.error?.message || fallback;
  }
}
