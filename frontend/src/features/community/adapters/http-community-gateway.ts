import { z } from "zod";

import type { ChatDestinationInput } from "@/features/community/domain/chat-destination";
import {
  chatDestinationListSchema,
  chatDestinationSchema,
} from "@/features/community/domain/chat-destination";
import type { CommunicationSettings } from "@/features/community/domain/communication-settings";
import { communicationSettingsSchema } from "@/features/community/domain/communication-settings";
import type { FocalPoint } from "@/features/community/domain/community";
import {
  communityListingSchema,
  communitySchema,
} from "@/features/community/domain/community";
import type {
  CommunityGateway,
  CommunityInput,
  ListParams,
  LookAndFeelAsset,
  MemberListParams,
} from "@/features/community/domain/community-gateway";
import { communityKindSchema } from "@/features/community/domain/community-kind";
import type { ContributionPolicy } from "@/features/community/domain/contribution-policy";
import { contributionPolicySchema } from "@/features/community/domain/contribution-policy";
import type { ManagementPolicy } from "@/features/community/domain/management-policy";
import { managementPolicySchema } from "@/features/community/domain/management-policy";
import {
  communityMemberSchema,
  membershipSchema,
} from "@/features/community/domain/membership";
import { pageSchema } from "@/features/community/domain/page";
import { ApiError, apiFetch, apiFetchVoid } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

const communityPageSchema = pageSchema(communityListingSchema);
const membershipPageSchema = pageSchema(membershipSchema);
const communityMemberListSchema = z.array(communityMemberSchema);
const communityKindListSchema = z.array(communityKindSchema);

export const httpCommunityGateway: CommunityGateway = {
  async list(params: ListParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities${query({ ...params })}`,
    );
    return communityPageSchema.parse(data);
  },

  async getBySlug(slug: string) {
    const data = await apiFetch<unknown>(`/axonia/communities/${slug}`);
    return communitySchema.parse(data);
  },

  async create(input: CommunityInput) {
    const data = await apiFetch<unknown>("/axonia/communities", {
      method: "POST",
      body: JSON.stringify(input),
    });
    return communitySchema.parse(data);
  },

  async update(slug: string, input: CommunityInput) {
    const data = await apiFetch<unknown>(`/axonia/communities/${slug}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return communitySchema.parse(data);
  },

  async archive(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/archive`,
      {
        method: "POST",
      },
    );
    return communitySchema.parse(data);
  },

  async listKinds() {
    const data = await apiFetch<unknown>("/axonia/community-kinds");
    return communityKindListSchema.parse(data);
  },

  async createKind(label: string) {
    const data = await apiFetch<unknown>("/axonia/community-kinds", {
      method: "POST",
      body: JSON.stringify({ label }),
    });
    return communityKindSchema.parse(data);
  },

  async renameKind(id: number, label: string) {
    const data = await apiFetch<unknown>(`/axonia/community-kinds/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ label }),
    });
    return communityKindSchema.parse(data);
  },

  async deleteKind(id: number, reassignToId?: number) {
    const suffix =
      reassignToId === undefined ? "" : `?reassignTo=${reassignToId}`;
    await apiFetchVoid(`/axonia/community-kinds/${id}${suffix}`, {
      method: "DELETE",
    });
  },

  async listMembers(slug: string, params: MemberListParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/members${query({ ...params })}`,
    );
    return membershipPageSchema.parse(data);
  },

  async searchMembers(slug: string, text: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/members/search${query({ q: text })}`,
    );
    return communityMemberListSchema.parse(data);
  },

  async myMembership(slug: string) {
    try {
      const data = await apiFetch<unknown>(
        `/axonia/communities/${slug}/members/me`,
      );
      return membershipSchema.parse(data);
    } catch (error) {
      // A non-member has no membership resource: 404 is the answer, not a failure.
      if (error instanceof ApiError && error.status === 404) {
        return null;
      }
      throw error;
    }
  },

  async join(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/members`,
      {
        method: "POST",
      },
    );
    return membershipSchema.parse(data);
  },

  async leave(slug: string) {
    await apiFetchVoid(`/axonia/communities/${slug}/members/me`, {
      method: "DELETE",
    });
  },

  async changeRole(slug: string, userId: string, role) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/members/${userId}/role`,
      {
        method: "PUT",
        body: JSON.stringify({ role }),
      },
    );
    return membershipSchema.parse(data);
  },

  async getContributionPolicy(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/contribution-policy`,
    );
    return contributionPolicySchema.parse(data);
  },

  async updateContributionPolicy(slug: string, policy: ContributionPolicy) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/contribution-policy`,
      {
        method: "PUT",
        body: JSON.stringify(policy),
      },
    );
    return contributionPolicySchema.parse(data);
  },

  async getManagementPolicy(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/management-policy`,
    );
    return managementPolicySchema.parse(data);
  },

  async updateManagementPolicy(slug: string, policy: ManagementPolicy) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/management-policy`,
      {
        method: "PUT",
        body: JSON.stringify(policy),
      },
    );
    return managementPolicySchema.parse(data);
  },

  async getCommunicationSettings(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/communication-settings`,
    );
    return communicationSettingsSchema.parse(data);
  },

  async updateCommunicationSettings(
    slug: string,
    settings: CommunicationSettings,
  ) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/communication-settings`,
      {
        method: "PUT",
        body: JSON.stringify(settings),
      },
    );
    return communicationSettingsSchema.parse(data);
  },

  async listChatDestinations(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/chat-destinations`,
    );
    return chatDestinationListSchema.parse(data);
  },

  async createChatDestination(slug: string, input: ChatDestinationInput) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/chat-destinations`,
      { method: "POST", body: JSON.stringify(input) },
    );
    return chatDestinationSchema.parse(data);
  },

  async updateChatDestination(
    slug: string,
    id: number,
    input: ChatDestinationInput,
  ) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/chat-destinations/${id}`,
      { method: "PATCH", body: JSON.stringify(input) },
    );
    return chatDestinationSchema.parse(data);
  },

  async removeChatDestination(slug: string, id: number) {
    await apiFetchVoid(`/axonia/communities/${slug}/chat-destinations/${id}`, {
      method: "DELETE",
    });
  },

  async testChatDestination(slug: string, id: number) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/chat-destinations/${id}/test`,
      { method: "POST" },
    );
  },

  async setAccent(slug: string, accentColor: string | null) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/look-and-feel`,
      { method: "PUT", body: JSON.stringify({ accentColor }) },
    );
    return communitySchema.parse(data);
  },

  async addAsset(slug: string, asset: LookAndFeelAsset, file: File) {
    const form = new FormData();
    form.append("file", file);
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/look-and-feel/${asset}`,
      { method: "POST", body: form },
    );
    return communitySchema.parse(data);
  },

  async removeAsset(slug: string, asset: LookAndFeelAsset) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/look-and-feel/${asset}`,
      { method: "DELETE" },
    );
    return communitySchema.parse(data);
  },

  async setFocalPoint(
    slug: string,
    asset: LookAndFeelAsset,
    focus: FocalPoint,
  ) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/look-and-feel/${asset}/focus`,
      { method: "PUT", body: JSON.stringify(focus) },
    );
    return communitySchema.parse(data);
  },
};
