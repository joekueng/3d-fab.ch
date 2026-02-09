import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../app-button/app-button.component';

export type SuccessContext = 'contact' | 'calc' | 'shop';

@Component({
  selector: 'app-success-state',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppButtonComponent],
  templateUrl: './success-state.component.html',
  styleUrl: './success-state.component.scss'
})
export class SuccessStateComponent {
  context = input.required<SuccessContext>();
  action = output<void>();
}
