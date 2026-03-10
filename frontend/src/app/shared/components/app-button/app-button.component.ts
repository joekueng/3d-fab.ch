import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app-button.component.html',
  styleUrl: './app-button.component.scss',
})
export class AppButtonComponent {
  variant = input<
    'primary' | 'secondary' | 'outline' | 'text' | 'ghost' | 'danger'
  >('primary');
  type = input<'button' | 'submit' | 'reset'>('button');
  disabled = input<boolean>(false);
  fullWidth = input<boolean>(false);

  handleClick(event: Event) {
    if (this.disabled()) {
      event.preventDefault();
      event.stopPropagation();
    }
  }
}
