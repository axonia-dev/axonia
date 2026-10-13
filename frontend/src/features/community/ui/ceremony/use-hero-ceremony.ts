import { useCallback, useDeferredValue, useEffect, useState } from "react";

import {
  type CeremonyKind,
  consumeCeremony,
  pendingCeremony,
} from "@/features/community/api/ceremony-handoff";
import type { Community } from "@/features/community/domain/community";

export interface ActiveCeremony {
  readonly kind: CeremonyKind;
  readonly countFrom: number;
  readonly countTo: number;
}

type HeroCommunity = Pick<Community, "slug" | "name" | "memberCount">;

interface State {
  readonly active: ActiveCeremony | null;
  readonly announcement: string;
}

function announcementFor(kind: CeremonyKind, name: string): string {
  return kind === "birth" ? `${name} created` : `Joined ${name}`;
}

/** A handed-off ceremony arrives after the act, so the newcomer is already counted. */
function fromHandoff(community: HeroCommunity): State {
  const kind = pendingCeremony(community.slug);
  if (kind === null) {
    return { active: null, announcement: "" };
  }
  const countFrom =
    kind === "welcome"
      ? Math.max(0, community.memberCount - 1)
      : community.memberCount;
  return {
    active: { kind, countFrom, countTo: community.memberCount },
    announcement: announcementFor(kind, community.name),
  };
}

export function useHeroCeremony(community: HeroCommunity) {
  const [state, setState] = useState<State>(() => fromHandoff(community));
  const { slug, name, memberCount } = community;
  // Mounted empty, then filled by a deferred pass: a live region only announces a change.
  const announcement = useDeferredValue(state.announcement, "");

  useEffect(() => {
    if (state.active !== null) {
      consumeCeremony(slug);
    }
  }, [slug, state.active]);

  const welcome = useCallback(() => {
    setState({
      active: {
        kind: "welcome",
        countFrom: memberCount,
        countTo: memberCount + 1,
      },
      announcement: announcementFor("welcome", name),
    });
  }, [memberCount, name]);

  const finish = useCallback(() => {
    setState((current) => ({ ...current, active: null }));
  }, []);

  return {
    active: state.active,
    announcement,
    welcome,
    finish,
  };
}
