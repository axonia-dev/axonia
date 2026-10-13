import type { ReactNode, RefObject } from "react";

import type { CeremonyClock } from "@/components/patterns/ceremony/clock";
import type { FacepileMember } from "@/components/patterns/MemberFacepile";
import type { Community } from "@/features/community/domain/community";

export interface CeremonyCardProps {
  community: Community;
  faces: readonly FacepileMember[];
  footer?: ReactNode;
  cornerControl?: ReactNode;
  target: RefObject<HTMLElement | null>;
  settle: "morph" | "fade";
  onDone: () => void;
  clock?: CeremonyClock;
}
