import { Share2 } from "lucide-react";
import { lazy, Suspense, useRef, useState } from "react";

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
import { useHeroCeremony } from "@/features/community/ui/ceremony/use-hero-ceremony";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { CommunityMenu } from "@/features/community/ui/CommunityMenu";
import { JoinAction } from "@/features/community/ui/JoinAction";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import {
  HERO_FACES_SHOWN,
  useHeroFaces,
} from "@/features/community/ui/use-hero-faces";

const HeroCeremony = lazy(() =>
  import("@/features/community/ui/ceremony/HeroCeremony").then((module) => ({
    default: module.HeroCeremony,
  })),
);

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
  onJoined,
}: Readonly<{
  community: Community;
  slug: string;
  access: ManagementAccess;
  onJoined: () => void;
}>) {
  const { data: membership, isError } = useMyMembership(slug);

  if (isError) {
    return <JoinAction slug={slug} onJoined={onJoined} />;
  }
  if (membership === undefined) {
    return <div className="h-9" />;
  }
  if (membership === null) {
    return <JoinAction slug={slug} onJoined={onJoined} />;
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
  const ceremony = useHeroCeremony(community);
  const hero = useRef<HTMLDivElement>(null);

  const meta = <MemberFacepile members={faces} max={HERO_FACES_SHOWN} />;
  const footer = (
    <HeroFooter
      community={community}
      slug={slug}
      access={access}
      onJoined={ceremony.welcome}
    />
  );
  const cornerControl = showInvite ? (
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
  ) : undefined;

  return (
    <>
      <div
        ref={hero}
        style={ceremony.active === null ? undefined : { visibility: "hidden" }}
      >
        <CommunityHeroCard
          community={community}
          meta={meta}
          footer={footer}
          cornerControl={cornerControl}
        />
      </div>
      {ceremony.active !== null && (
        <Suspense fallback={null}>
          <HeroCeremony
            ceremony={ceremony.active}
            community={community}
            faces={faces}
            footer={footer}
            cornerControl={cornerControl}
            target={hero}
            settle="morph"
            onDone={ceremony.finish}
          />
        </Suspense>
      )}
      <p role="status" className="sr-only">
        {ceremony.announcement}
      </p>
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
