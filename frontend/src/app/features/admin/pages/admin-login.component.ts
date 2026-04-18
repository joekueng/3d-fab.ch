import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import {
  AdminAuthResponse,
  AdminAuthService,
} from '../services/admin-auth.service';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [CommonModule, FormsModule, AppInputComponent, AppButtonComponent],
  templateUrl: './admin-login.component.html',
  styleUrl: './admin-login.component.scss',
})
export class AdminLoginComponent implements OnDestroy {
  private readonly authService = inject(AdminAuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  password = '';
  loading = false;
  errorMessage: string | null = null;
  lockSecondsRemaining = 0;
  private lockTimer: ReturnType<typeof setInterval> | null = null;

  submit(): void {
    if (
      !this.password.trim() ||
      this.loading ||
      this.lockSecondsRemaining > 0
    ) {
      return;
    }

    this.loading = true;
    this.errorMessage = null;

    this.authService.login(this.password).subscribe({
      next: (response: AdminAuthResponse) => {
        this.loading = false;
        if (!response?.authenticated) {
          this.handleLoginFailure(response?.retryAfterSeconds);
          return;
        }

        this.clearLock();
        const redirect = this.route.snapshot.queryParamMap.get('redirect');
        if (redirect && redirect.startsWith('/')) {
          void this.router.navigateByUrl(redirect);
          return;
        }

        void this.router.navigate(['/', this.resolveLang(), 'admin', 'orders']);
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.handleLoginFailure(this.extractRetryAfterSeconds(error));
      },
    });
  }

  private resolveLang(): string {
    for (const level of this.route.pathFromRoot) {
      const lang = level.snapshot.paramMap.get('lang');
      if (lang && SUPPORTED_LANGS.has(lang)) {
        return lang;
      }
    }
    return 'it';
  }

  private handleLoginFailure(retryAfterSeconds: number | undefined): void {
    const timeout = this.normalizeTimeout(retryAfterSeconds);
    this.errorMessage = 'Password non valida.';
    this.startLock(timeout);
  }

  private extractRetryAfterSeconds(error: HttpErrorResponse): number {
    const fromBody = Number(error?.error?.retryAfterSeconds);
    if (Number.isFinite(fromBody) && fromBody > 0) {
      return Math.floor(fromBody);
    }

    const fromHeader = Number(error?.headers?.get('Retry-After'));
    if (Number.isFinite(fromHeader) && fromHeader > 0) {
      return Math.floor(fromHeader);
    }

    return 2;
  }

  private normalizeTimeout(value: number | undefined): number {
    const parsed = Number(value);
    if (Number.isFinite(parsed) && parsed > 0) {
      return Math.floor(parsed);
    }
    return 2;
  }

  private startLock(seconds: number): void {
    this.lockSecondsRemaining = Math.max(this.lockSecondsRemaining, seconds);
    this.stopTimer();
    this.lockTimer = setInterval(() => {
      this.lockSecondsRemaining = Math.max(0, this.lockSecondsRemaining - 1);
      if (this.lockSecondsRemaining === 0) {
        this.stopTimer();
      }
    }, 1000);
  }

  private clearLock(): void {
    this.lockSecondsRemaining = 0;
    this.stopTimer();
  }

  private stopTimer(): void {
    if (this.lockTimer !== null) {
      clearInterval(this.lockTimer);
      this.lockTimer = null;
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }
}
