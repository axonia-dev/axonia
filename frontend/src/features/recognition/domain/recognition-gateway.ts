import type {
  ContributionKey,
  Love,
  LoveSummary,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";

export interface RecognitionGateway {
  getSummary(key: ContributionKey): Promise<LoveSummary>;
  love(input: ContributionKey): Promise<Love>;
  unlove(key: ContributionKey): Promise<void>;
  listMyContributions(): Promise<RecognizedContribution[]>;
}
