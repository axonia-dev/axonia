import { z } from "zod";

import { namedAreaSchema } from "@/features/expertise/domain/named-area";

const tierSchema = z.enum([
  "PRACTITIONER",
  "CONTRIBUTOR",
  "INTERESTED",
  "OPEN_TO_QUESTIONS",
]);

const signalTypeSchema = z.enum([
  "PRESENTED",
  "CONTRIBUTED",
  "OFFERED",
  "COMPLETED",
]);

const sourceTypeSchema = z.enum(["SLOT", "ARTIFACT", "TOPIC", "ACTION_ITEM"]);

const evidenceItemSchema = z.object({
  signalType: signalTypeSchema,
  sourceType: sourceTypeSchema,
  sourceId: z.number(),
  label: z.string().nullable(),
  communitySlug: z.string().nullable(),
  occurredAt: z.string(),
  /** Session a presented slot belongs to: the deep-link target for SLOT evidence. */
  sessionId: z.number().nullable().default(null),
  /** Section type (slots) or artifact type (artifacts): id/label/icon/accent for the badge. */
  typeId: z.number().nullable().default(null),
  typeLabel: z.string().nullable().default(null),
  typeIcon: z.string().nullable().default(null),
  typeAccentColor: z.string().nullable().default(null),
});

const evidenceSchema = z.object({
  presentedCount: z.number(),
  contributedCount: z.number(),
  offeredCount: z.number(),
  recent: z.array(evidenceItemSchema),
});

export const practitionerSchema = z.object({
  userId: z.string(),
  displayName: z.string().nullable(),
  avatarUrl: z.string().nullable(),
  orgUnit: z.string().nullable(),
  tier: tierSchema,
  evidence: evidenceSchema,
  openToQuestions: z.boolean().default(false),
  declaredAt: z.string().nullable().default(null),
});

export const expertiseAreaSchema = z.object({
  canonicalTag: z.string(),
  variants: z.array(z.string()),
  /** People who can be asked about the area: a signal or a declaration, counted once each. */
  peopleCount: z.number(),
});

const topicNodeSchema = z.object({
  canonicalTag: z.string(),
  practitionerCount: z.number(),
  signalCount: z.number(),
  /** The largest single practitioner's share of signalCount, 0 to 1. */
  topHolderShare: z.number(),
  /**
   * Null for an area carried only by declarations: it has no holder and no activity (the map
   * draws a declaration and never measures it).
   */
  topHolderName: z.string().nullable(),
  presentedCount: z.number(),
  contributedCount: z.number(),
  offeredCount: z.number(),
  lastActivityAt: z.string().nullable(),
  /** People who declared the area here, practitioners included. Not `offeredCount`, which is OFFERED signals. */
  openToQuestionsCount: z.number(),
});

const topicLinkSchema = z.object({
  a: z.string(),
  b: z.string(),
  weight: z.number(),
});

export const topicMapSchema = z.object({
  topics: z.array(topicNodeSchema),
  links: z.array(topicLinkSchema),
  namedAreas: z.array(namedAreaSchema).default([]),
});

const mapPersonSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
});

const mapContributionSchema = z.object({
  signalId: z.number(),
  canonicalTag: z.string(),
  userId: z.string(),
  /** People the contribution reached. Null means unknown, never zero: only a presented slot has an answer. */
  reach: z.number().nullable(),
  evidence: evidenceItemSchema,
});

export const mapContributionsSchema = z.object({
  people: z.array(mapPersonSchema),
  items: z.array(mapContributionSchema),
  /** The cap was hit. The picture is thinner than the practice; no count on the page comes from here. */
  truncated: z.boolean(),
});

const areaCommunitySchema = z.object({
  id: z.number(),
  /** Absent from an older self view; a declaration for the same community carries the slug instead. */
  slug: z.string().optional(),
  name: z.string(),
});

const communityTierSchema = z.object({
  communityId: z.number(),
  tier: tierSchema,
});

const selfAreaSchema = z.object({
  canonicalTag: z.string(),
  tier: tierSchema,
  hidden: z.boolean(),
  communities: z.array(areaCommunitySchema),
  evidence: evidenceSchema,
  /** The tier each community reads after the member's privacy choices; a hidden area carries none, and a server
   *  older than the shape sends no list at all. */
  communityTiers: z.array(communityTierSchema).optional(),
});

const selfDeclarationSchema = z.object({
  tag: z.string(),
  communityId: z.number(),
  communityName: z.string(),
  communitySlug: z.string(),
  hidden: z.boolean(),
  declaredAt: z.string(),
});

/** A community the member can declare an area in. */
const declarableCommunitySchema = z.object({
  id: z.number(),
  slug: z.string(),
  name: z.string(),
});

export const selfViewSchema = z.object({
  optedOut: z.boolean(),
  areas: z.array(selfAreaSchema),
  declarations: z.array(selfDeclarationSchema).default([]),
  declarableCommunities: z.array(declarableCommunitySchema).default([]),
});

export const declaredAreaSchema = z.object({
  tag: z.string(),
  declaredAt: z.string(),
});

export const mergeSuggestionSchema = z.object({
  tagA: z.string(),
  tagB: z.string(),
  score: z.number(),
});

export const areaEvidencePageSchema = z.object({
  items: z.array(evidenceItemSchema),
  hasMore: z.boolean(),
});

/** How much of a community's real content carries a tag, per kind of content. */
export const tagUsageSchema = z.object({
  artifacts: z.number(),
  topics: z.number(),
  talks: z.number(),
  /** Members who declared the tag, over the variants the delete would remove: one per member
   *  whatever spelling they typed, and not privacy filtered, so it can exceed peopleCount. */
  declarations: z.number().default(0),
  /** Members who want to learn the tag in this community, counted once each. */
  learningIntents: z.number().default(0),
});

export type Tier = z.infer<typeof tierSchema>;
export type SignalType = z.infer<typeof signalTypeSchema>;
export type SourceType = z.infer<typeof sourceTypeSchema>;
export type EvidenceItem = z.infer<typeof evidenceItemSchema>;
export type Evidence = z.infer<typeof evidenceSchema>;
export type Practitioner = z.infer<typeof practitionerSchema>;
export type ExpertiseArea = z.infer<typeof expertiseAreaSchema>;
export type SelfArea = z.infer<typeof selfAreaSchema>;
export type SelfView = z.infer<typeof selfViewSchema>;
export type SelfDeclaration = z.infer<typeof selfDeclarationSchema>;
export type DeclarableCommunity = z.infer<typeof declarableCommunitySchema>;
export type DeclaredArea = z.infer<typeof declaredAreaSchema>;
export type MergeSuggestion = z.infer<typeof mergeSuggestionSchema>;
export type AreaEvidencePage = z.infer<typeof areaEvidencePageSchema>;
export type TagUsage = z.infer<typeof tagUsageSchema>;
export type TopicNode = z.infer<typeof topicNodeSchema>;
export type TopicLink = z.infer<typeof topicLinkSchema>;
export type TopicMap = z.infer<typeof topicMapSchema>;
export type MapPerson = z.infer<typeof mapPersonSchema>;
export type MapContribution = z.infer<typeof mapContributionSchema>;
export type MapContributions = z.infer<typeof mapContributionsSchema>;
