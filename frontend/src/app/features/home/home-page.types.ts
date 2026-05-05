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

export type Rgb = {
  r: number;
  g: number;
  b: number;
};

export type HomeProjectGlow = {
  top: string;
  right: string;
  bottom: string;
  left: string;
  topLeft: string;
  topRight: string;
  bottomRight: string;
  bottomLeft: string;
};
