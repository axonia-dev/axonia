import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { useParams } from "react-router-dom";
import { vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import {
  useDismissSignal,
  useHideTag,
  useSetOptOut,
  useUnhideTag,
} from "@/features/expertise/api/use-expertise-prefs";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import type {
  SelfArea,
  SelfDeclaration,
} from "@/features/expertise/domain/expertise";
import { MyExpertiseView } from "@/features/expertise/ui/MyExpertiseView";
import { useMe } from "@/features/identity/api/use-me";

/** Shared by the MyExpertiseView declaration suites; each suite mocks the modules imported here. */

interface Community {
  id: number;
  name: string;
  slug: string;
}

export const ENGINEERING: Community = {
  id: 1,
  name: "Engineering",
  slug: "engineering",
};
export const DATA: Community = { id: 2, name: "Data", slug: "data" };

function mutation(mutate = vi.fn()) {
  return { mutate, isPending: false } as never;
}

export function removal(mutate = vi.fn()) {
  return { mutate, isRemoving: () => false } as never;
}

export function area(
  canonicalTag: string,
  communities: Community[],
  over: Partial<SelfArea> = {},
): SelfArea {
  return {
    canonicalTag,
    tier: "PRACTITIONER",
    hidden: false,
    communities: communities.map(({ id, slug, name }) => ({ id, slug, name })),
    evidence: {
      presentedCount: 1,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    communityTiers: [],
    ...over,
  };
}

export function declaration(
  tag: string,
  community: Community,
  over: Partial<SelfDeclaration> = {},
): SelfDeclaration {
  return {
    tag,
    communityId: community.id,
    communityName: community.name,
    communitySlug: community.slug,
    hidden: false,
    declaredAt: "2026-09-12T12:00:00Z",
    ...over,
  };
}

export function givenSelfView(
  areas: SelfArea[],
  declarations: SelfDeclaration[] = [],
  declarableCommunities: Community[] = [],
) {
  vi.mocked(useSelfView).mockReturnValue({
    data: { optedOut: false, areas, declarations, declarableCommunities },
    isLoading: false,
  } as never);
}

export function onPage(community?: Community) {
  vi.mocked(useParams).mockReturnValue(
    community === undefined ? {} : { slug: community.slug },
  );
  vi.mocked(useCommunity).mockReturnValue({
    data: community === undefined ? undefined : { id: community.id },
  } as never);
}

export function stubHooks() {
  vi.mocked(useSetOptOut).mockReturnValue(mutation());
  vi.mocked(useHideTag).mockReturnValue(mutation());
  vi.mocked(useUnhideTag).mockReturnValue(mutation());
  vi.mocked(useDismissSignal).mockReturnValue(mutation());
  vi.mocked(useRemoveDeclaration).mockReturnValue(removal());
  vi.mocked(useMe).mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant" },
  } as never);
  onPage();
}

export function renderView() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const ui = () => (
    <QueryClientProvider client={client}>
      <MyExpertiseView />
    </QueryClientProvider>
  );
  const result = render(ui());
  return { ...result, rerender: () => result.rerender(ui()) };
}
