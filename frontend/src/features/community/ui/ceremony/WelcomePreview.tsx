import type { RefObject } from "react";

import type { Community } from "@/features/community/domain/community";
import { HeroCeremony } from "@/features/community/ui/ceremony/HeroCeremony";
import { useHeroFaces } from "@/features/community/ui/use-hero-faces";

export interface WelcomePreviewProps {
  community: Community;
  target: RefObject<HTMLElement | null>;
  onDone: () => void;
}

/** The welcome a newcomer would get, with the viewer standing in as the newcomer. */
export function WelcomePreview({
  community,
  target,
  onDone,
}: Readonly<WelcomePreviewProps>) {
  const faces = useHeroFaces(community.slug);
  return (
    <HeroCeremony
      ceremony={{
        kind: "welcome",
        countFrom: community.memberCount,
        countTo: community.memberCount,
        placement: "viewport",
      }}
      community={community}
      faces={faces}
      target={target}
      settle="fade"
      onDone={onDone}
    />
  );
}
