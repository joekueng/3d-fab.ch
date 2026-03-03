import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, AppButtonComponent, AppCardComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent {
  readonly shopGalleryImages = [
    {
      src: 'assets/images/home/supporto-bici.jpg',
      alt: 'HOME.SHOP_IMAGE_ALT_1'
    }
  ];

  readonly founderImages = [
    {
      src: 'assets/images/home/da-cambiare.jpg',
      alt: 'HOME.FOUNDER_IMAGE_ALT_1'
    },
    {
      src: 'assets/images/home/vino.JPG',
      alt: 'HOME.FOUNDER_IMAGE_ALT_2'
    }
  ];

  founderImageIndex = 0;

  prevFounderImage(): void {
    this.founderImageIndex =
      this.founderImageIndex === 0
        ? this.founderImages.length - 1
        : this.founderImageIndex - 1;
  }

  nextFounderImage(): void {
    this.founderImageIndex =
      this.founderImageIndex === this.founderImages.length - 1
        ? 0
        : this.founderImageIndex + 1;
  }
}
