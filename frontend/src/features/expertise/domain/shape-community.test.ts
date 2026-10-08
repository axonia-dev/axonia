import { describe, expect, it } from "vitest";

import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import type { SelfView } from "@/features/expertise/domain/expertise";
import { communityInScope } from "@/features/expertise/domain/shape-community";

const PAGE = { id: 1, slug: "platform", name: "Platform Guild" };

const VIEW: SelfView = {
  optedOut: false,
  areas: [
    {
      canonicalTag: "kafka",
      tier: "PRACTITIONER",
      hidden: false,
      communities: [{ id: 2, slug: "data", name: "Data Guild" }],
      communityTiers: [],
      evidence: {
        presentedCount: 1,
        contributedCount: 0,
        offeredCount: 0,
        recent: [],
      },
    },
  ],
  declarations: [
    {
      tag: "react",
      communityId: 3,
      communityName: "Web Guild",
      communitySlug: "web",
      hidden: false,
      declaredAt: "2026-09-01T00:00:00Z",
    },
  ],
  declarableCommunities: [],
};

describe("communityInScope", () => {
  it("is the page's community when no lens is picked", () => {
    expect(
      communityInScope(VIEW, { lensId: ALL_COMMUNITIES, page: PAGE }),
    ).toEqual(PAGE);
  });

  it("is nothing across all communities with no lens", () => {
    expect(
      communityInScope(VIEW, { lensId: ALL_COMMUNITIES, page: null }),
    ).toBeNull();
  });

  it("follows the lens, from an area's communities", () => {
    expect(communityInScope(VIEW, { lensId: 2, page: PAGE })).toEqual({
      id: 2,
      slug: "data",
      name: "Data Guild",
    });
  });

  it("follows the lens, from a declaration", () => {
    expect(communityInScope(VIEW, { lensId: 3, page: null })).toEqual({
      id: 3,
      slug: "web",
      name: "Web Guild",
    });
  });

  it("is nothing for a lens the view does not know", () => {
    expect(communityInScope(VIEW, { lensId: 9, page: null })).toBeNull();
  });

  it("falls back to a declaration's slug when the area's community carries none, an older self view", () => {
    const view: SelfView = {
      ...VIEW,
      areas: [
        { ...VIEW.areas[0]!, communities: [{ id: 2, name: "Data Guild" }] },
      ],
      declarations: [
        ...VIEW.declarations,
        {
          tag: "kafka",
          communityId: 2,
          communityName: "Data Guild",
          communitySlug: "data",
          hidden: false,
          declaredAt: "2026-09-01T00:00:00Z",
        },
      ],
    };
    expect(communityInScope(view, { lensId: 2, page: null })).toEqual({
      id: 2,
      slug: "data",
      name: "Data Guild",
    });
  });

  it("is nothing for a lens whose area community carries no slug and no declaration resolves it", () => {
    const view: SelfView = {
      ...VIEW,
      areas: [
        { ...VIEW.areas[0]!, communities: [{ id: 2, name: "Data Guild" }] },
      ],
    };
    expect(communityInScope(view, { lensId: 2, page: null })).toBeNull();
  });
});
