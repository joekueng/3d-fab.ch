import { TestBed } from '@angular/core/testing';
import { AppDialogComponent } from './app-dialog.component';

describe('AppDialogComponent', () => {
  it('opens a native modal, keeps inside clicks open and delegates backdrop/Escape/close actions', async () => {
    await TestBed.configureTestingModule({
      imports: [AppDialogComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppDialogComponent);
    fixture.componentRef.setInput('title', 'Preview');
    fixture.componentRef.setInput('closeLabel', 'Close');
    const dismissed = jasmine.createSpy('dismissed');
    fixture.componentInstance.dismissed.subscribe(dismissed);
    fixture.detectChanges();
    await fixture.whenStable();
    const dialog: HTMLDialogElement =
      fixture.nativeElement.querySelector('dialog');
    expect(dialog.open).toBeTrue();
    expect(dialog.matches(':modal')).toBeTrue();
    expect(dialog.getAttribute('aria-label')).toBe('Preview');
    dialog
      .querySelector('h3')!
      .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(dismissed).not.toHaveBeenCalled();
    const cancel = new Event('cancel', { cancelable: true });
    dialog.dispatchEvent(cancel);
    expect(cancel.defaultPrevented).toBeTrue();
    expect(dismissed).toHaveBeenCalledTimes(1);
    dialog.dispatchEvent(new MouseEvent('click', { clientX: -1, clientY: -1 }));
    expect(dismissed).toHaveBeenCalledTimes(2);
    dialog.querySelector('button')!.click();
    expect(dismissed).toHaveBeenCalledTimes(3);
    fixture.destroy();
    expect(dialog.open).toBeFalse();
  });
  for (const width of [1440, 375]) {
    it(`keeps long titles and modal content within a ${width}px viewport`, async () => {
      await TestBed.configureTestingModule({
        imports: [AppDialogComponent],
      }).compileComponents();
      const fixture = TestBed.createComponent(AppDialogComponent);
      fixture.componentRef.setInput(
        'title',
        'Druckvorschau für benutzerdefinierte Bauteile '.repeat(8),
      );
      fixture.componentRef.setInput('closeLabel', 'Schließen');
      fixture.componentRef.setInput('fullscreenOnMobile', true);
      fixture.detectChanges();
      await fixture.whenStable();
      const frame = document.createElement('iframe');
      frame.style.cssText = `width: ${width}px; height: 800px; border: 0;`;
      document.body.appendChild(frame);
      const dialog: HTMLDialogElement =
        fixture.nativeElement.querySelector('dialog');
      dialog.close();
      try {
        const target = frame.contentDocument!;
        await Promise.all(
          Array.from(
            document.querySelectorAll('style, link[rel="stylesheet"]'),
          ).map((source) => {
            const copy = source.cloneNode(true);
            if (copy instanceof HTMLLinkElement) {
              copy.href = (source as HTMLLinkElement).href;
              return new Promise<void>((resolve, reject) => {
                copy.onload = () => resolve();
                copy.onerror = () =>
                  reject(new Error('Could not load application styles'));
                target.head.appendChild(copy);
              });
            }
            target.head.appendChild(copy);
            return Promise.resolve();
          }),
        );
        target.body.style.margin = '0';
        target.body.appendChild(fixture.nativeElement);
        dialog.showModal();
        const bounds = dialog.getBoundingClientRect();
        expect(bounds.left).toBeGreaterThanOrEqual(0);
        expect(bounds.right).toBeLessThanOrEqual(width + 1);
        expect(bounds.bottom).toBeLessThanOrEqual(801);
        expect(dialog.scrollWidth).toBeLessThanOrEqual(dialog.clientWidth + 1);
        const layoutViewportWidth = target.documentElement.clientWidth;
        expect(Math.round(bounds.width)).toBe(
          width === 375 ? layoutViewportWidth : 860,
        );
      } finally {
        fixture.destroy();
        frame.remove();
      }
    });
  }
});
