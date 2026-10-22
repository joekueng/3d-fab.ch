import {
  Component,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TranslatePipe} from '@ngx-translate/core';
import {Observable} from 'rxjs';
import {AppDialogComponent} from '../app-dialog/app-dialog.component';
import {StlViewerComponent} from '../stl-viewer/stl-viewer.component';

@Component({
  selector: 'app-model-preview-dialog',
  standalone: true,
  imports: [TranslatePipe, AppDialogComponent, StlViewerComponent],
  templateUrl: './model-preview-dialog.component.html',
  styleUrl: './model-preview-dialog.component.scss',
})
export class ModelPreviewDialogComponent implements OnInit {
  readonly filename = input.required<string>();
  readonly source = input.required<Observable<Blob>>();
  readonly color = input('#facf0a');
  readonly dismissed = output<void>();
  readonly file = signal<Blob | null>(null);
  readonly error = signal(false);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.source()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (file) => this.file.set(file),
        error: () => this.error.set(true),
      });
  }
}
