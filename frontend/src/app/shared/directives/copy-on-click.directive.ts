import { isPlatformBrowser } from '@angular/common';
import {
  Directive,
  HostBinding,
  HostListener,
  Input,
  PLATFORM_ID,
  inject,
  output,
} from '@angular/core';

@Directive({
  selector: '[appCopyOnClick]',
  standalone: true,
})
export class CopyOnClickDirective {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  @Input('appCopyOnClick') value:
    | string
    | null
    | undefined
    | (() => Promise<string>);

  readonly copySucceeded = output<void>();
  readonly copyFailed = output<unknown>();

  @HostBinding('style.cursor') readonly cursor = 'pointer';

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    if (!this.value || !this.isBrowser) return;
    event.stopPropagation();
    void this.resolveAndCopy();
  }

  private async resolveAndCopy(): Promise<void> {
    try {
      const text = (
        typeof this.value === 'function'
          ? await this.value()
          : (this.value ?? '')
      ).trim();
      if (!text) return;
      await this.copy(text);
      this.copySucceeded.emit();
    } catch (error: unknown) {
      this.copyFailed.emit(error);
    }
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
      if (!document.execCommand('copy'))
        throw new Error('Clipboard unavailable');
    } finally {
      document.body.removeChild(textarea);
    }
  }
}
