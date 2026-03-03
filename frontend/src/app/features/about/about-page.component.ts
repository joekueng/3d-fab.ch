import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AppLocationsComponent } from '../../shared/components/app-locations/app-locations.component';

type MemberId = 'joe' | 'matteo';
type PassionId =
  | 'bike-trial'
  | 'mountain'
  | 'ski'
  | 'self-hosting'
  | 'print-3d'
  | 'travel'
  | 'snowboard'
  | 'snowboard-instructor'
  | 'electronics'
  | 'woodworking'
  | 'van-life'
  | 'coffee'
  | 'software-development';

interface PassionChip {
  id: PassionId;
  labelKey: string;
}

@Component({
  selector: 'app-about-page',
  standalone: true,
  imports: [TranslateModule, AppLocationsComponent],
  templateUrl: './about-page.component.html',
  styleUrl: './about-page.component.scss',
})
export class AboutPageComponent {
  selectedMember: MemberId | null = null;
  hoveredMember: MemberId | null = null;

  readonly passions: ReadonlyArray<PassionChip> = [
    { id: 'mountain', labelKey: 'ABOUT.PASSION_MOUNTAIN' },
    { id: 'coffee', labelKey: 'ABOUT.PASSION_COFFEE' },
    { id: 'bike-trial', labelKey: 'ABOUT.PASSION_BIKE_TRIAL' },
    { id: 'electronics', labelKey: 'ABOUT.PASSION_ELECTRONICS' },
    { id: 'travel', labelKey: 'ABOUT.PASSION_TRAVEL' },
    { id: 'woodworking', labelKey: 'ABOUT.PASSION_WOODWORKING' },
    { id: 'print-3d', labelKey: 'ABOUT.PASSION_PRINT_3D' },
    { id: 'ski', labelKey: 'ABOUT.PASSION_SKI' },
    {
      id: 'software-development',
      labelKey: 'ABOUT.PASSION_SOFTWARE_DEVELOPMENT',
    },
    { id: 'snowboard', labelKey: 'ABOUT.PASSION_SNOWBOARD' },
    { id: 'van-life', labelKey: 'ABOUT.PASSION_VAN_LIFE' },
    { id: 'self-hosting', labelKey: 'ABOUT.PASSION_SELF_HOSTING' },
    {
      id: 'snowboard-instructor',
      labelKey: 'ABOUT.PASSION_SNOWBOARD_INSTRUCTOR',
    },
  ];

  private readonly memberPassions: Readonly<
    Record<MemberId, ReadonlyArray<PassionId>>
  > = {
    joe: [
      'bike-trial',
      'mountain',
      'ski',
      'self-hosting',
      'print-3d',
      'travel',
      'coffee',
      'software-development',
    ],
    matteo: [
      'bike-trial',
      'mountain',
      'snowboard',
      'snowboard-instructor',
      'electronics',
      'print-3d',
      'woodworking',
      'van-life',
    ],
  };

  get activeMember(): MemberId | null {
    return this.hoveredMember ?? this.selectedMember;
  }

  toggleSelectedMember(member: MemberId): void {
    this.selectedMember = this.selectedMember === member ? null : member;
  }

  setHoveredMember(member: MemberId | null): void {
    this.hoveredMember = member;
  }

  isMemberActive(member: MemberId): boolean {
    return this.activeMember === member;
  }

  isMemberSelected(member: MemberId): boolean {
    return this.selectedMember === member;
  }

  isPassionActive(passionId: PassionId): boolean {
    const member = this.activeMember;
    return member !== null && this.memberPassions[member].includes(passionId);
  }
}
