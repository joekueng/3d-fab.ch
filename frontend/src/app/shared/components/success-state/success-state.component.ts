import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { AppButtonComponent } from '../app-button/app-button.component';

export type SuccessContext = 'contact' | 'calc' | 'shop';

@Component({
  selector: 'app-success-state',
  standalone: true,
  imports: [CommonModule, TranslatePipe, AppButtonComponent],
  templateUrl: './success-state.component.html',
  styleUrl: './success-state.component.scss',
})
export class SuccessStateComponent {
  context = input.required<SuccessContext>();
  action = output<void>();
}
