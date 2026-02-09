import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AppLocationsComponent } from '../../shared/components/app-locations/app-locations.component';

@Component({
  selector: 'app-about-page',
  standalone: true,
  imports: [TranslateModule, AppLocationsComponent],
  templateUrl: './about-page.component.html',
  styleUrl: './about-page.component.scss'
})
export class AboutPageComponent {}

