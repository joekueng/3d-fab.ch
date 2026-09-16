import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, firstValueFrom } from 'rxjs';
import { CopyOnClickDirective } from '../../../../shared/directives/copy-on-click.directive';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { QuoteEstimatorService } from '../../services/quote-estimator.service';
import {
  InformationModel,
  OrderInformationService,
} from '../../../order-information/order-information.service';

@Component({
  selector: 'app-session-email',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    AppButtonComponent,
    AppInputComponent,
    CopyOnClickDirective,
  ],
  templateUrl: './session-email.component.html',
  styleUrl: './session-email.component.scss',
})
export class SessionEmailComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly estimator = inject(QuoteEstimatorService);
  private readonly translate = inject(TranslateService);
  readonly information = inject(OrderInformationService);
  readonly sessionId = input.required<string>();
  readonly mode = input<'easy' | 'advanced'>('easy');
  readonly recalculationRequired = input(false);
  readonly models = input<InformationModel[]>([]);
  readonly missingModel = computed(
    () =>
      !!this.information.model() &&
      !this.models().some((model) => model.value === this.information.model()),
  );
  readonly expanded = signal(false);
  readonly sending = signal(false);
  readonly sent = signal(false);
  readonly copying = signal(false);
  readonly copied = signal(false);
  readonly link = signal('');
  readonly linkControl = new FormControl('', { nonNullable: true });
  readonly error = signal('');
  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.email,
        Validators.maxLength(254),
      ],
    }),
  });

  readonly copyLink = async (): Promise<string> => {
    this.sending.set(true);
    this.copying.set(true);
    this.copied.set(false);
    this.sent.set(false);
    this.error.set('');
    this.link.set('');
    const result = await firstValueFrom(
      this.estimator
        .sessionLink(
          this.sessionId(),
          this.translate.getCurrentLang() || 'it',
          this.mode(),
        )
        .pipe(takeUntilDestroyed(this.destroyRef)),
    );
    this.link.set(result.url);
    this.linkControl.setValue(result.url);
    return result.url;
  };

  finishCopy(): void {
    this.copied.set(true);
    this.sending.set(false);
    this.copying.set(false);
  }

  failCopy(error: unknown): void {
    this.sending.set(false);
    this.copying.set(false);
    this.error.set(
      this.link() ? 'SESSION_EMAIL.COPY_ERROR' : this.errorKey(error),
    );
  }

  private errorKey(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    return status === 429
      ? 'SESSION_EMAIL.RATE_LIMIT'
      : status === 503
        ? 'SESSION_EMAIL.SCAN_ERROR'
        : status === 410 || status === 404
          ? 'SESSION_EMAIL.UNAVAILABLE'
          : 'SESSION_EMAIL.ERROR';
  }

  send(): void {
    if (
      this.form.invalid ||
      this.sending() ||
      this.information.busy() ||
      this.recalculationRequired() ||
      this.missingModel()
    )
      return;
    this.sending.set(true);
    this.sent.set(false);
    this.copied.set(false);
    this.error.set('');
    this.estimator
      .emailSession(
        this.sessionId(),
        this.form.controls.email.value.trim(),
        this.translate.getCurrentLang() || 'it',
        this.mode(),
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.sending.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.sent.set(true);
          this.expanded.set(false);
        },
        error: (error: unknown) => {
          this.error.set(this.errorKey(error));
        },
      });
  }
}
