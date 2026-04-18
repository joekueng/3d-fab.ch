import { Component, input } from '@angular/core';

@Component({
  selector: 'app-feature-panel',
  standalone: true,
  templateUrl: './feature-panel.component.html',
  styleUrl: './feature-panel.component.scss',
})
export class FeaturePanelComponent {
  readonly eyebrow = input<string>('');
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
