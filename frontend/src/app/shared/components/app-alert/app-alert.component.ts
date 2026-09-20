import { Component, input } from '@angular/core';

@Component({
  selector: 'app-alert',
  standalone: true,
  templateUrl: './app-alert.component.html',
  styleUrl: './app-alert.component.scss',
})
export class AppAlertComponent {
  type = input<'info' | 'warning' | 'error' | 'success'>('info');
}
