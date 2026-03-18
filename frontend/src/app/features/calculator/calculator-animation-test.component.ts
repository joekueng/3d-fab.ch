import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import {
  BrandAnimationLogoComponent,
  BrandAnimationVariant,
} from '../../shared/components/brand-animation-logo/brand-animation-logo.component';

@Component({
  selector: 'app-calculator-animation-test',
  standalone: true,
  imports: [CommonModule, BrandAnimationLogoComponent],
  templateUrl: './calculator-animation-test.component.html',
  styleUrl: './calculator-animation-test.component.scss',
})
export class CalculatorAnimationTestComponent {
  readonly variant = signal<BrandAnimationVariant>('site-intro');

  setVariant(variant: BrandAnimationVariant): void {
    this.variant.set(variant);
  }
}
