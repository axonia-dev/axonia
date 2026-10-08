import type { ExpertiseSearchParams } from "@/features/expertise/domain/expertise-gateway";

export const expertiseKeys = {
  all: ["expertise"] as const,
  community: (slug: string) => [...expertiseKeys.all, slug] as const,
  search: (slug: string, params: ExpertiseSearchParams) =>
    [...expertiseKeys.community(slug), "search", params] as const,
  areas: (slug: string, search?: string) =>
    [...expertiseKeys.community(slug), "areas", search] as const,
  map: (slug: string) => [...expertiseKeys.community(slug), "map"] as const,
  mapContributions: (slug: string) =>
    [...expertiseKeys.map(slug), "contributions"] as const,
  suggestions: (slug: string, tags: string[]) =>
    [...expertiseKeys.community(slug), "suggestions", tags] as const,
  curation: (slug: string) =>
    [...expertiseKeys.community(slug), "curation"] as const,
  curationVocabulary: (slug: string) =>
    [...expertiseKeys.curation(slug), "vocabulary"] as const,
  namedAreas: (slug: string) =>
    [...expertiseKeys.curation(slug), "named-areas"] as const,
  /** A community's self view sits under the organization's, so invalidating `selfView()` refreshes both. */
  selfView: (slug?: string) =>
    slug === undefined
      ? ([...expertiseKeys.all, "self-view"] as const)
      : ([...expertiseKeys.all, "self-view", slug] as const),
  areaEvidence: (tag: string, slug?: string) =>
    slug === undefined
      ? ([...expertiseKeys.all, "area-evidence", tag] as const)
      : ([...expertiseKeys.all, "area-evidence", tag, slug] as const),
};
