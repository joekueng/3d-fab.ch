import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-locations',
  standalone: true,
  imports: [CommonModule, TranslateModule, RouterLink],
  templateUrl: './app-locations.component.html',
  styleUrl: './app-locations.component.scss'
})
export class AppLocationsComponent {
  selectedLocation: 'ticino' | 'bienne' = 'ticino';

  selectLocation(location: 'ticino' | 'bienne') {
    this.selectedLocation = location;
  }
}
