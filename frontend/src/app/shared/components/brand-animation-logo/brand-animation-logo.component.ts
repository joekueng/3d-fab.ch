import { Component, computed, input } from '@angular/core';

export type BrandAnimationVariant = 'site-intro' | 'calculator-loader';

interface AnimationLetter {
  key: string;
  darkSrc: string;
  yellowSrc: string;
  wordX: string;
}

interface ResolvedAnimationLetter {
  key: string;
  src: string;
  wordX: string;
}

const LETTERS: readonly AnimationLetter[] = [
  {
    key: '3',
    darkSrc: '/assets/images/animation/31200.svg',
    yellowSrc: '/assets/images/animation/3g1200.svg',
    wordX: '-9.4rem',
  },
  {
    key: 'd',
    darkSrc: '/assets/images/animation/d1200.svg',
    yellowSrc: '/assets/images/animation/Dg1200.svg',
    wordX: '-4.9rem',
  },
  {
    key: 'F',
    darkSrc: '/assets/images/animation/F1200.svg',
    yellowSrc: '/assets/images/animation/Fg1200.svg',
    wordX: '1rem',
  },
  {
    key: 'A',
    darkSrc: '/assets/images/animation/A1200.svg',
    yellowSrc: '/assets/images/animation/Ag1200.svg',
    wordX: '5.6rem',
  },
  {
    key: 'B',
    darkSrc: '/assets/images/animation/B1200.svg',
    yellowSrc: '/assets/images/animation/Bg1200.svg',
    wordX: '10.2rem',
  },
] as const;

@Component({
  selector: 'app-brand-animation-logo',
  standalone: true,
  templateUrl: './brand-animation-logo.component.html',
  styleUrl: './brand-animation-logo.component.scss',
})
export class BrandAnimationLogoComponent {
  readonly variant = input<BrandAnimationVariant>('site-intro');
  readonly decorative = input(true);
  readonly ariaLabel = input('3D fab animated logo');

  readonly resolvedLetters = computed<ResolvedAnimationLetter[]>(() =>
    LETTERS.map((letter) => ({
      key: letter.key,
      src: this.variant() === 'site-intro' ? letter.yellowSrc : letter.darkSrc,
      wordX: letter.wordX,
    })),
  );
}
