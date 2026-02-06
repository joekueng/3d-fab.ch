import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-alert',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app-alert.component.html',
  styleUrl: './app-alert.component.scss'
})
export class AppAlertComponent {
  type = input<'info' | 'warning' | 'error' | 'success'>('info');
}
