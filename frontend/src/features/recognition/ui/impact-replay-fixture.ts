import { renderHook } from "@testing-library/react";
import type { PropsWithChildren, ReactNode } from "react";
import { vi } from "vitest";

import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";
import type {
  NewLoves,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";
import {
  contributionKeyOf,
  useImpactReplay,
} from "@/features/recognition/ui/use-impact-replay";
import { useMediaQuery } from "@/lib/use-media-query";

// Callers mock use-new-loves, comet-trail, land-pulse and use-media-query before importing this.
export const markSeen = vi.fn();

function card(id: number, loveCount: number): RecognizedContribution {
  return {
    contributionType: "SLOT_PRESENTED",
    contributionId: id,
    communityId: 1,
    communitySlug: "eng",
    communityName: "Engineering",
    title: `Talk ${id}`,
    occurredAt: "2026-06-01T00:00:00Z",
    loveCount,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
  };
}

export const RUST = card(1, 7);
export const RUNBOOK = card(2, 3);
export const RUST_KEY = contributionKeyOf(RUST);

export const NEW_LOVES: NewLoves = {
  asOf: "2026-10-17T09:00:00Z",
  capped: false,
  items: [
    { contributionType: "SLOT_PRESENTED", contributionId: 1, newLoves: 2 },
  ],
};

export function element(top: number): HTMLElement {
  const el = document.createElement("span");
  el.getBoundingClientRect = () =>
    ({
      top,
      bottom: top + 16,
      left: 0,
      right: 16,
      width: 16,
      height: 16,
    }) as DOMRect;
  return el;
}

export function newLovesReturn(
  result: Partial<{ data: NewLoves; isError: boolean }>,
) {
  vi.mocked(useNewLoves).mockReturnValue(result as never);
}

export interface Props {
  cards: ReadonlyArray<RecognizedContribution>;
  communityId: number | undefined;
  scopeKnown: boolean;
  ready: boolean;
}

export function renderReplay(
  initial: Partial<Props> = {},
  wrapper?: ({ children }: PropsWithChildren) => ReactNode,
) {
  const hook = renderHook((props: Props) => useImpactReplay(props), {
    ...(wrapper === undefined ? {} : { wrapper }),
    initialProps: {
      cards: [RUST, RUNBOOK],
      communityId: undefined,
      scopeKnown: true,
      ready: false,
      ...initial,
    },
  });
  hook.result.current.avatarRef(element(0));
  hook.result.current.heartRef(RUST_KEY)(element(200));
  hook.result.current.heartRef(contributionKeyOf(RUNBOOK))(element(260));
  return hook;
}

export function start(
  hook: ReturnType<typeof renderReplay>,
  over: Partial<Props> = {},
) {
  hook.rerender({
    cards: [RUST, RUNBOOK],
    communityId: undefined,
    scopeKnown: true,
    ready: true,
    ...over,
  });
}

export function arrangeReplay() {
  vi.useFakeTimers();
  vi.clearAllMocks();
  vi.mocked(useMarkImpactSeen).mockReturnValue({ mutate: markSeen } as never);
  vi.mocked(useMediaQuery).mockReturnValue(false);
  newLovesReturn({ data: NEW_LOVES });
}
