import { Share2 } from "lucide-react";
import { useState } from "react";

import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { Button } from "@/components/ui/Button";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  type ManagementAccess,
  useManagementAccess,
} from "@/features/community/api/use-management-access";
import { useMyMembership } from "@/features/community/api/use-membership";
import { type Community } from "@/features/community/domain/community";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { CommunityMenu } from "@/features/community/ui/CommunityMenu";
import { JoinAction } from "@/features/community/ui/JoinAction";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import {
  HERO_FACES_SHOWN,
  useHeroFaces,
} from "@/features/community/ui/use-hero-faces";

interface IdentityHeroProps {
  community: Community;
  slug: string;
}

// Invites are a self-serve concern: the affordance exists only in hub-kind organizations, for
// whoever the management policy lets invite, and only where the edition supplies the dialog.
function useShowInvite(inviteAccess: boolean, supplied: boolean): boolean {
  const { data: organization } = useCurrentOrganization();
  return supplied && inviteAccess && organization?.kind === "HUB";
}

/** The footer: what you are here, and the one quiet door out. A visitor is offered
 *  Join and nothing else; a member gets their standing and the menu that holds
 *  Leave. While the membership query is still open we commit to neither: showing a
 *  member a Join button would be a lie. `data` reads `undefined` for both a
 *  still-loading and a failed query, so `isError` is what tells them apart: a
 *  failed lookup falls back to Join rather than staying blank forever, since a
 *  visitor should not be locked out of joining by a transient failure. */
function HeroFooter({
  community,
  slug,
  access,
}: Readonly<{
  community: Community;
  slug: string;
  access: ManagementAccess;
}>) {
  const { data: membership, isError } = useMyMembership(slug);

  if (isError) {
    return <JoinAction slug={slug} />;
  }
  if (membership === undefined) {
    return <div className="h-9" />;
  }
  if (membership === null) {
    return <JoinAction slug={slug} />;
  }
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-3">
        <MembershipChip role={membership.role} />
      </div>
      <CommunityMenu community={community} slug={slug} access={access} />
    </div>
  );
}

// Sits over the banner corner, so the glyph reads on any imagery: a dark
// translucent puck rather than the accent-tinted ghost used inside the card.
const shareTriggerClass =
  "rounded-full bg-black/35 p-2 text-white backdrop-blur hover:bg-primary hover:text-primary-foreground";

/**
 * The community's front door: the shared hero card with the primary Join call-to-action.
 * Self-serve hubs get a quiet share glyph in the card's top-right corner for whoever the
 * invite axis opens for. Management lives in the card's footer, next to the membership chip.
 */
export function IdentityHero({ community, slug }: Readonly<IdentityHeroProps>) {
  const access = useManagementAccess(slug);
  const { CommunityInvite } = useEdition();
  const showInvite = useShowInvite(access.invite, CommunityInvite !== null);
  const [inviting, setInviting] = useState(false);
  const faces = useHeroFaces(slug);

  return (
    <>
      <CommunityHeroCard
        community={community}
        meta={<MemberFacepile members={faces} max={HERO_FACES_SHOWN} />}
        footer={
          <HeroFooter community={community} slug={slug} access={access} />
        }
        cornerControl={
          showInvite ? (
            <div className="absolute top-4 right-4">
              <Button
                variant="ghost"
                size="sm"
                aria-label="Invite people"
                className={shareTriggerClass}
                onPress={() => setInviting(true)}
              >
                <Share2 size={18} aria-hidden />
              </Button>
            </div>
          ) : undefined
        }
      />
      {showInvite && CommunityInvite !== null && (
        <CommunityInvite
          slug={slug}
          community={community}
          isOpen={inviting}
          onOpenChange={setInviting}
        />
      )}
    </>
  );
}
