import type { OptionsResponse } from "../../src/app/features/calculator/services/quote-estimator.service";
import type { PublicMediaUsageDto } from "../../src/app/core/services/public-media.service";
export const options: OptionsResponse;
export function mediaUsage(
  type: string,
  key: string,
  lang?: string,
): PublicMediaUsageDto[];
