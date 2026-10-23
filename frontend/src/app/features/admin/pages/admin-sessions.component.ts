import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  PLATFORM_ID,
  inject,
  OnInit,
} from '@angular/core';
import {
  AdminOperationsService,
  AdminQuoteSession,
  AdminQuoteSessionDetail,
  AdminSessionStatistics,
} from '../services/admin-operations.service';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { ModelPreviewDialogComponent } from '../../../shared/components/model-preview-dialog/model-preview-dialog.component';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-admin-sessions',
  standalone: true,
  imports: [
    CommonModule,
    CopyOnClickDirective,
    AppButtonComponent,
    ModelPreviewDialogComponent,
    TranslatePipe,
  ],
  templateUrl: './admin-sessions.component.html',
  styleUrl: './admin-sessions.component.scss',
})
export class AdminSessionsComponent implements OnInit {
  preview: { filename: string; source: Observable<Blob> } | null = null;

  canPreview(filename: string): boolean {
    return /\.(stl|3mf|obj|step|stp)$/i.test(filename.trim());
  }

  openPreview(
    sessionId: string,
    item: { id: string; originalFilename: string },
  ): void {
    this.preview = {
      filename: item.originalFilename,
      source: this.adminOperationsService.getSessionItemPreview(
        sessionId,
        item.id,
      ),
    };
  }
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminOperationsService = inject(AdminOperationsService);

  sessions: AdminQuoteSession[] = [];
  statistics: AdminSessionStatistics | null = null;
  sessionDetailsById: Record<string, AdminQuoteSessionDetail | undefined> = {};
  loading = false;
  statisticsLoading = false;
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
    this.loadStatistics();
    this.adminOperationsService.getSessions().subscribe({
      next: (sessions) => {
        this.changeDetector.markForCheck();
        this.sessions = sessions;
        this.loading = false;
      },
      error: () => {
        this.changeDetector.markForCheck();
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le sessioni.';
      },
    });
  }

  private loadStatistics(): void {
    this.statisticsLoading = true;
    this.adminOperationsService.getSessionStatistics().subscribe({
      next: (statistics) => {
        this.changeDetector.markForCheck();
        this.statistics = statistics;
        this.statisticsLoading = false;
      },
      error: () => {
        this.changeDetector.markForCheck();
        this.statistics = null;
        this.statisticsLoading = false;
      },
    });
  }

  deleteSession(session: AdminQuoteSession): void {
    if (this.deletingSessionIds.has(session.id)) {
      return;
    }

    const confirmed =
      this.isBrowser &&
      window.confirm(
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
        this.changeDetector.markForCheck();
        this.sessions = this.sessions.filter((item) => item.id !== session.id);
        this.deletingSessionIds.delete(session.id);
        this.successMessage = 'Sessione eliminata.';
        this.loadStatistics();
      },
      error: (err) => {
        this.changeDetector.markForCheck();
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
        this.changeDetector.markForCheck();
        this.sessionDetailsById = {
          ...this.sessionDetailsById,
          [session.id]: detail,
        };
        this.loadingDetailSessionIds.delete(session.id);
      },
      error: (err) => {
        this.changeDetector.markForCheck();
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
