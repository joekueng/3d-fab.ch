import { PublicMediaDisplayImage } from '../../core/services/public-media.service';

export type HomeCapabilityUsageKey =
  | 'capability-prototyping'
  | 'capability-custom-parts'
  | 'capability-small-series'
  | 'capability-cad';

export interface HomeCapabilityConfig {
  usageKey: HomeCapabilityUsageKey;
  titleKey: string;
  textKey: string;
}

export interface HomeCapabilityCard extends HomeCapabilityConfig {
  image: PublicMediaDisplayImage | null;
}
