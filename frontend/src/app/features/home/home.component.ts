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
export class HomeComponent {}
