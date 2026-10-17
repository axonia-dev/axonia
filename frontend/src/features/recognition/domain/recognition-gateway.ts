import type {
  ContributionKey,
  Love,
  LoveSummary,
  MarkImpactSeen,
  NewLoves,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";

export interface RecognitionGateway {
  getSummary(key: ContributionKey): Promise<LoveSummary>;
  love(input: ContributionKey): Promise<Love>;
  unlove(key: ContributionKey): Promise<void>;
  listMyContributions(): Promise<RecognizedContribution[]>;
  getNewLoves(communityId?: number): Promise<NewLoves>;
  markImpactSeen(input: MarkImpactSeen): Promise<void>;
}
