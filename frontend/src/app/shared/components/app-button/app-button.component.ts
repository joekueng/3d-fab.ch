import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule],
  host: {
    '[class.app-button-host--block]': 'fullWidth()',
  },
  templateUrl: './app-button.component.html',
  styleUrl: './app-button.component.scss',
})
export class AppButtonComponent {
  variant = input<
    | 'primary'
    | 'secondary'
    | 'outline'
    | 'text'
    | 'ghost'
    | 'ghost-danger'
    | 'danger'
  >('primary');
  size = input<'md' | 'sm'>('md');
  type = input<'button' | 'submit' | 'reset'>('button');
  disabled = input<boolean>(false);
  fullWidth = input<boolean>(false);

  buttonClass(): string {
    const variantClasses: Record<string, string> = {
      primary: 'ui-button',
      secondary: 'ui-button ui-button--secondary',
      outline: 'ui-button ui-button--outline',
      text: 'ui-button ui-button--text',
      ghost: 'ui-button ui-button--ghost',
      'ghost-danger': 'ui-button ui-button--ghost-danger',
      danger: 'ui-button ui-button--danger',
    };

    const baseClass =
      variantClasses[this.variant()] ?? variantClasses['primary'];
    const classes = [baseClass];
    if (this.size() === 'sm') {
      classes.push('ui-button--sm');
    }
    if (this.fullWidth()) {
      classes.push('ui-button--block');
    }
    return classes.join(' ');
  }

  handleClick(event: Event) {
    if (this.disabled()) {
      event.preventDefault();
      event.stopPropagation();
    }
  }
}
