import { isPlatformBrowser } from '@angular/common';
import {
  Directive,
  HostBinding,
  HostListener,
  Input,
  PLATFORM_ID,
  inject,
} from '@angular/core';

@Directive({
  selector: '[appCopyOnClick]',
  standalone: true,
})
export class CopyOnClickDirective {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  @Input('appCopyOnClick') value: string | null | undefined;

  @HostBinding('style.cursor') readonly cursor = 'pointer';

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    const text = (this.value ?? '').trim();
    if (!text) {
      return;
    }

    event.stopPropagation();
    void this.copy(text);
  }

  private async copy(text: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }

    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(text);
        return;
      } catch {
        // Fallback below for browsers/environments that block clipboard API.
      }
    }

    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
    } finally {
      document.body.removeChild(textarea);
    }
  }
}
