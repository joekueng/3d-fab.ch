import {
  afterNextRender,
  Component,
  ElementRef,
  OnDestroy,
  input,
  output,
  viewChild,
} from '@angular/core';
import { AppButtonComponent } from '../app-button/app-button.component';

@Component({
  selector: 'app-dialog',
  standalone: true,
  imports: [AppButtonComponent],
  templateUrl: './app-dialog.component.html',
  styleUrl: './app-dialog.component.scss',
})
export class AppDialogComponent implements OnDestroy {
  readonly title = input.required<string>();
  readonly closeLabel = input.required<string>();
  readonly subtitle = input('');
  readonly size = input<'small' | 'default' | 'large'>('default');
  readonly fullscreenOnMobile = input(false);
  readonly dismissed = output<void>();
  private readonly dialog =
    viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  ngOnDestroy(): void {
    const dialog = this.dialog().nativeElement;
    if (dialog.open) dialog.close();
  }

  cancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }

  backdropClick(event: MouseEvent): void {
    const dialog = this.dialog().nativeElement;
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (
      event.clientX < bounds.left ||
      event.clientX > bounds.right ||
      event.clientY < bounds.top ||
      event.clientY > bounds.bottom
    ) {
      this.dismissed.emit();
    }
  }
}
