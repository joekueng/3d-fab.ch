import {Component, input, signal} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {AppButtonComponent} from '../app-button/app-button.component';
import {AppDialogComponent} from '../app-dialog/app-dialog.component';

@Component({
  selector: 'app-image-preview',
  standalone: true,
  imports: [TranslatePipe, AppButtonComponent, AppDialogComponent],
  templateUrl: './image-preview.component.html',
  styleUrl: './image-preview.component.scss',
})
export class ImagePreviewComponent {
  readonly src = input.required<string>();
  readonly alt = input.required<string>();
  readonly open = signal(false);
  readonly zoomed = signal(false);

  close(): void {
    this.open.set(false);
    this.zoomed.set(false);
  }
}
