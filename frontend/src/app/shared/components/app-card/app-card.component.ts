import { Component, input } from '@angular/core';

@Component({
  selector: 'app-card',
  standalone: true,
  templateUrl: './app-card.component.html',
  styleUrl: './app-card.component.scss',
})
export class AppCardComponent {
  title = input<string>('');
  subtitle = input<string>('');
  bodyPadding = input<string>('');
}
