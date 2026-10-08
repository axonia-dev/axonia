import type {
  ReactionDefinition,
  ReactionEligibility,
  ReactionRef,
  ReactionTally,
} from "@/features/recognition/domain/reaction";
import type { ContributionKey } from "@/features/recognition/domain/recognition";

export interface ReactionGateway {
  tally(key: ContributionKey): Promise<ReactionTally[]>;
  react(key: ContributionKey, ref: ReactionRef): Promise<ReactionTally>;
  unreact(key: ContributionKey, ref: ReactionRef): Promise<void>;
  listDefinitions(): Promise<ReactionDefinition[]>;
  listAllDefinitions(): Promise<ReactionDefinition[]>;
  /** The caller's own proposals, still waiting. Narrower than `listAllDefinitions`, and open to
   *  any member, because it answers with nothing the caller did not propose themselves. */
  listMyProposals(): Promise<ReactionDefinition[]>;
  eligibility(): Promise<ReactionEligibility>;
  propose(name: string, file: File): Promise<ReactionDefinition>;
  approve(id: number): Promise<ReactionDefinition>;
  remove(id: number): Promise<void>;
}
