import { useCallback, useDeferredValue, useEffect, useState } from "react";

import type { StagePlacement } from "@/components/patterns/ceremony/CeremonyStage";
import { liftCurtain } from "@/components/patterns/ceremony/curtain";
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
  readonly placement: StagePlacement;
}

type HeroCommunity = Pick<Community, "slug" | "name" | "memberCount">;

interface State {
  readonly active: ActiveCeremony | null;
  readonly announcement: string;
  /** How many joins this visit has welcomed: a rejoin is announced again even with the same words. */
  readonly joins: number;
}

function announcementFor(kind: CeremonyKind, name: string): string {
  return kind === "birth" ? `${name} created` : `Joined ${name}`;
}

/** A handed-off ceremony arrives after the act, so the newcomer is already counted. */
function fromHandoff(community: HeroCommunity): State {
  const kind = pendingCeremony(community.slug);
  if (kind === null) {
    return { active: null, announcement: "", joins: 0 };
  }
  const countFrom =
    kind === "welcome"
      ? Math.max(0, community.memberCount - 1)
      : community.memberCount;
  return {
    active: {
      kind,
      countFrom,
      countTo: community.memberCount,
      placement: "viewport",
    },
    announcement: announcementFor(kind, community.name),
    joins: 0,
  };
}

export function useHeroCeremony(community: HeroCommunity) {
  const [state, setState] = useState<State>(() => fromHandoff(community));
  const { slug, name, memberCount } = community;
  // Mounted empty, then filled by a deferred pass: a live region only announces a change.
  const announcement = useDeferredValue(state.announcement, "");

  useEffect(() => {
    if (state.active === null) {
      liftCurtain();
    } else {
      consumeCeremony(slug);
    }
  }, [slug, state.active]);

  const welcome = useCallback(() => {
    setState((current) => ({
      active: {
        kind: "welcome",
        countFrom: memberCount,
        countTo: memberCount + 1,
        placement: "in-place",
      },
      announcement: announcementFor("welcome", name),
      joins: current.joins + 1,
    }));
  }, [memberCount, name]);

  const finish = useCallback(() => {
    setState((current) => ({ ...current, active: null }));
  }, []);

  return {
    active: state.active,
    announcement,
    announcementKey: state.joins,
    welcome,
    finish,
  };
}
