import { useCallback, useMemo, useState } from "react";

import { CeremonyStage } from "@/components/patterns/ceremony/CeremonyStage";
import { browserClock } from "@/components/patterns/ceremony/clock";
import { applyCues } from "@/components/patterns/ceremony/cues";
import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import {
  type FacepileMember,
  MemberFacepile,
} from "@/components/patterns/MemberFacepile";
import type { CeremonyCardProps } from "@/features/community/ui/ceremony/ceremony-card-props";
import {
  placeFlier,
  WELCOME_BEATS,
  WELCOME_LENGTH,
  WELCOME_SETTLE,
  welcomeCues,
  welcomePainter,
} from "@/features/community/ui/ceremony/welcome-choreography";
import { communityHue } from "@/features/community/ui/community-accent";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { HERO_FACES_SHOWN } from "@/features/community/ui/use-hero-faces";

export interface WelcomeCeremonyProps extends CeremonyCardProps {
  newcomer: FacepileMember | null;
  countFrom: number;
  countTo: number;
}

function withNewcomerFirst(
  faces: readonly FacepileMember[],
  newcomer: FacepileMember | null,
): readonly FacepileMember[] {
  if (newcomer === null) {
    return faces;
  }
  return [newcomer, ...faces.filter((face) => face.id !== newcomer.id)];
}

/** A member steps inside: doors open onto the community, then the newcomer takes a place in it. */
export function WelcomeCeremony({
  community,
  faces,
  newcomer,
  countFrom,
  countTo,
  footer,
  cornerControl,
  target,
  settle,
  placement = "viewport",
  onDone,
  clock,
}: Readonly<WelcomeCeremonyProps>) {
  const [count, setCount] = useState(countFrom);
  const hue = communityHue(community);
  const members = withNewcomerFirst(faces, newcomer);
  const pictured = community.heroUrl != null;
  const paint = useMemo(() => welcomePainter(hue), [hue]);
  const arriving = newcomer !== null;
  const cues = useMemo(
    () => welcomeCues({ pictured, arriving }),
    [pictured, arriving],
  );
  const onFrame = useCallback(
    (card: HTMLElement, t: number) => {
      applyCues(card, cues, t);
      placeFlier(card, t);
      if (t >= WELCOME_BEATS.count.start) {
        setCount(countTo);
      }
    },
    [countTo, cues],
  );

  return (
    <CeremonyStage
      length={WELCOME_LENGTH}
      target={target}
      settle={settle}
      settleSeconds={WELCOME_SETTLE}
      placement={placement}
      onFrame={onFrame}
      paint={paint}
      onDone={onDone}
      clock={clock ?? browserClock}
    >
      <CommunityHeroCard
        community={{ ...community, memberCount: count }}
        meta={
          members.length > 0 && (
            <span data-ceremony-part="facepile" className="flex">
              <MemberFacepile members={members} max={HERO_FACES_SHOWN} />
            </span>
          )
        }
        footer={footer}
        cornerControl={cornerControl}
      />
      {newcomer !== null && (
        <span
          data-ceremony-flier
          className="absolute top-0 left-0 flex opacity-0 *:size-full"
        >
          <MemberAvatar
            name={newcomer.name}
            hue={newcomer.hue}
            subject={newcomer.id}
            src={newcomer.src}
            shape="circle"
            className="size-full border-2 border-(color:--flier-ring) text-[length:inherit]"
          />
        </span>
      )}
    </CeremonyStage>
  );
}
