import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import {
  ActivatedRoute,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { AdminAuthService } from '../services/admin-auth.service';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss',
})
export class AdminShellComponent {
  private readonly adminAuthService = inject(AdminAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  logout(): void {
    this.adminAuthService.logout().subscribe({
      next: () => {
        void this.router.navigate(['/', this.resolveLang(), 'admin', 'login']);
      },
      error: () => {
        void this.router.navigate(['/', this.resolveLang(), 'admin', 'login']);
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
}
