import type { ReactNode, RefObject } from "react";

import type { StagePlacement } from "@/components/patterns/ceremony/CeremonyStage";
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
  placement?: StagePlacement;
  onDone: () => void;
  clock?: CeremonyClock;
}
