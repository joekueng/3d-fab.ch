export function startGuidesFixture(): Promise<{
  origin: string;
  state: { failOptions: boolean; failMedia: boolean; hideExamples: boolean };
  requests: Array<{ path: string; lang: string | null; key: string | null }>;
  close(): Promise<void>;
}>;
