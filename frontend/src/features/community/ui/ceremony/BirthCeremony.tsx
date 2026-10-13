import { useCallback, useMemo } from "react";

import { CeremonyStage } from "@/components/patterns/ceremony/CeremonyStage";
import { browserClock } from "@/components/patterns/ceremony/clock";
import { applyCues } from "@/components/patterns/ceremony/cues";
import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import {
  BIRTH_LENGTH,
  birthCues,
  birthPainter,
} from "@/features/community/ui/ceremony/birth-choreography";
import type { CeremonyCardProps } from "@/features/community/ui/ceremony/ceremony-card-props";
import { communityHue } from "@/features/community/ui/community-accent";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { HERO_FACES_SHOWN } from "@/features/community/ui/use-hero-faces";

/** A community comes into being: sparks condense into its name, and a glint crosses it. */
export function BirthCeremony({
  community,
  faces,
  footer,
  cornerControl,
  target,
  settle,
  onDone,
  clock,
}: Readonly<CeremonyCardProps>) {
  const hue = communityHue(community);
  const cues = useMemo(() => birthCues(hue), [hue]);
  const paint = useMemo(() => birthPainter(hue), [hue]);
  const onFrame = useCallback(
    (card: HTMLElement, t: number) => applyCues(card, cues, t),
    [cues],
  );

  return (
    <CeremonyStage
      length={BIRTH_LENGTH}
      target={target}
      settle={settle}
      onFrame={onFrame}
      paint={paint}
      onDone={onDone}
      clock={clock ?? browserClock}
    >
      <CommunityHeroCard
        community={community}
        meta={<MemberFacepile members={faces} max={HERO_FACES_SHOWN} />}
        footer={footer}
        cornerControl={cornerControl}
      />
    </CeremonyStage>
  );
}
