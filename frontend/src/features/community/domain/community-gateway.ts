import type {
  ChatDestination,
  ChatDestinationInput,
} from "@/features/community/domain/chat-destination";
import type { CommunicationSettings } from "@/features/community/domain/communication-settings";
import type {
  Community,
  FocalPoint,
} from "@/features/community/domain/community";
import type { CommunityKind } from "@/features/community/domain/community-kind";
import type { ContributionPolicy } from "@/features/community/domain/contribution-policy";
import type { ManagementPolicy } from "@/features/community/domain/management-policy";
import type {
  CommunityMember,
  CommunityRole,
  Membership,
} from "@/features/community/domain/membership";
import type { Page } from "@/features/community/domain/page";
import type { ImageLimits } from "@/lib/fit-image";

export interface ListParams {
  page?: number;
  size?: number;
  q?: string;
}

export interface MemberListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface CommunityInput {
  name: string;
  kindId: number;
  description: string;
}

export type LookAndFeelAsset = "badge" | "hero";

/** What each asset may weigh and measure, the same limits the server enforces. */
export const ASSET_LIMITS: Record<LookAndFeelAsset, ImageLimits> = {
  hero: { maxEdge: 2560, maxBytes: 5 * 1024 * 1024 },
  badge: { maxEdge: 512, maxBytes: 2 * 1024 * 1024 },
};

export interface CommunityGateway {
  list(params: ListParams): Promise<Page<Community>>;
  getBySlug(slug: string): Promise<Community>;
  create(input: CommunityInput): Promise<Community>;
  update(slug: string, input: CommunityInput): Promise<Community>;
  archive(slug: string): Promise<Community>;
  listKinds(): Promise<CommunityKind[]>;
  createKind(label: string): Promise<CommunityKind>;
  renameKind(id: number, label: string): Promise<CommunityKind>;
  deleteKind(id: number, reassignToId?: number): Promise<void>;
  listMembers(
    slug: string,
    params: MemberListParams,
  ): Promise<Page<Membership>>;
  /** The caller's own membership, or null when they do not belong. */
  myMembership(slug: string): Promise<Membership | null>;
  /** Members of this community whose name matches the typed text, at most 25. */
  searchMembers(slug: string, text: string): Promise<CommunityMember[]>;
  join(slug: string): Promise<Membership>;
  leave(slug: string): Promise<void>;
  changeRole(
    slug: string,
    userId: string,
    role: CommunityRole,
  ): Promise<Membership>;
  getContributionPolicy(slug: string): Promise<ContributionPolicy>;
  updateContributionPolicy(
    slug: string,
    policy: ContributionPolicy,
  ): Promise<ContributionPolicy>;
  getManagementPolicy(slug: string): Promise<ManagementPolicy>;
  updateManagementPolicy(
    slug: string,
    policy: ManagementPolicy,
  ): Promise<ManagementPolicy>;
  getCommunicationSettings(slug: string): Promise<CommunicationSettings>;
  updateCommunicationSettings(
    slug: string,
    settings: CommunicationSettings,
  ): Promise<CommunicationSettings>;
  listChatDestinations(slug: string): Promise<ChatDestination[]>;
  createChatDestination(
    slug: string,
    input: ChatDestinationInput,
  ): Promise<ChatDestination>;
  updateChatDestination(
    slug: string,
    id: number,
    input: ChatDestinationInput,
  ): Promise<ChatDestination>;
  removeChatDestination(slug: string, id: number): Promise<void>;
  testChatDestination(slug: string, id: number): Promise<void>;
  setAccent(slug: string, accentColor: string | null): Promise<Community>;
  addAsset(
    slug: string,
    asset: LookAndFeelAsset,
    file: File,
  ): Promise<Community>;
  removeAsset(slug: string, asset: LookAndFeelAsset): Promise<Community>;
  setFocalPoint(
    slug: string,
    asset: LookAndFeelAsset,
    focus: FocalPoint,
  ): Promise<Community>;
}
