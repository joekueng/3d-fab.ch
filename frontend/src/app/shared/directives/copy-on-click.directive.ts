import { Directive, HostBinding, HostListener, Input } from '@angular/core';

@Directive({
  selector: '[appCopyOnClick]',
  standalone: true,
})
export class CopyOnClickDirective {
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
