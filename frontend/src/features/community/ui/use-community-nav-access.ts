import { useIsDelegated } from "@/auth/delegated-session";
import type { Community } from "@/features/community/domain/community";
import type { CommunityNavAccess } from "@/features/community/ui/community-shell-nav";

/** What the rail's conditional entries read: stewardship access and the community's mentoring switch for a member. My mentoring inside a community shows that community's mentoring only, so it follows the same switch; mentoring elsewhere lives at the top level. */
export function useCommunityNavAccess(
  access: Pick<CommunityNavAccess, "curation" | "health"> & { member: boolean },
  community: Community | undefined,
): CommunityNavAccess {
  const delegated = useIsDelegated();
  const mentoring =
    community?.mentoringEnabled === true && access.member && !delegated;
  return {
    curation: access.curation,
    health: access.health,
    mentoring,
    myMentoring: mentoring,
  };
}
