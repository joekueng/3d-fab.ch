import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminAuthService } from '../services/admin-auth.service';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-login.component.html',
  styleUrl: './admin-login.component.scss'
})
export class AdminLoginComponent {
  private readonly authService = inject(AdminAuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  password = '';
  loading = false;
  errorMessage: string | null = null;

  submit(): void {
    if (!this.password.trim() || this.loading) {
      return;
    }

    this.loading = true;
    this.errorMessage = null;

    this.authService.login(this.password).subscribe({
      next: (isAuthenticated) => {
        this.loading = false;
        if (!isAuthenticated) {
          this.errorMessage = 'Password non valida.';
          return;
        }

        const redirect = this.route.snapshot.queryParamMap.get('redirect');
        if (redirect && redirect.startsWith('/')) {
          void this.router.navigateByUrl(redirect);
          return;
        }

        void this.router.navigate(['/', this.resolveLang(), 'admin']);
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Password non valida.';
      }
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
}
