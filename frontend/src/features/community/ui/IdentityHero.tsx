import { Share2 } from "lucide-react";
import {
  type ReactNode,
  type RefObject,
  Suspense,
  useDeferredValue,
  useEffect,
  useRef,
  useState,
} from "react";

import type { StagePlacement } from "@/components/patterns/ceremony/CeremonyStage";
import { StageBackdrop } from "@/components/patterns/ceremony/StageBackdrop";
import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { communityMorphId } from "@/components/patterns/morph/MorphPart";
import { Button } from "@/components/ui/Button";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { pendingCeremonySlug } from "@/features/community/api/ceremony-handoff";
import {
  type ManagementAccess,
  useManagementAccess,
} from "@/features/community/api/use-management-access";
import { useMyMembership } from "@/features/community/api/use-membership";
import { type Community } from "@/features/community/domain/community";
import { LazyHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";
import {
  type ActiveCeremony,
  useHeroCeremony,
} from "@/features/community/ui/ceremony/use-hero-ceremony";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { CommunityMenu } from "@/features/community/ui/CommunityMenu";
import { HeroAmbientField } from "@/features/community/ui/HeroAmbientField";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import {
  HERO_FACES_SHOWN,
  useHeroFaces,
} from "@/features/community/ui/use-hero-faces";
import { useRegisterVisitingJoin } from "@/features/community/ui/visiting-join-context";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

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

/** The footer: a member's standing and the menu that holds Leave. While the membership query
 *  is still open the row keeps its height, so a member's card does not grow once it answers. */
function HeroFooter({
  community,
  slug,
  access,
}: Readonly<{
  community: Community;
  slug: string;
  access: ManagementAccess;
}>) {
  const { data: membership } = useMyMembership(slug);

  if (membership == null) {
    return <div className="h-9" />;
  }
  return (
    <div className="flex min-h-9 items-center justify-between gap-3">
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

/** Focus lands on the member controls once a ceremony earned by a join is over and says member. */
function useFocusFooterAfterJoin(
  hero: RefObject<HTMLElement | null>,
  settled: boolean,
  mountedPlaying: boolean,
): () => void {
  const joined = useRef(mountedPlaying);
  useEffect(() => {
    if (settled && joined.current) {
      joined.current = false;
      hero.current
        ?.querySelector<HTMLElement>('[data-ceremony-part="footer"] button')
        ?.focus({ preventScroll: true });
    }
  }, [hero, settled]);
  return () => {
    joined.current = true;
  };
}

/** A failed lookup counts as a visit, so the Visiting bar still offers Join; loading is neither. */
function useMembershipStanding(slug: string): {
  member: boolean;
  visiting: boolean;
} {
  const { data, isError } = useMyMembership(slug);
  return { member: data != null, visiting: isError || data === null };
}

/** A polite live region, filled one pass after it mounts so a screen reader hears it; a new `key` announces the same words again. */
function Announcement({ text }: Readonly<{ text: string }>) {
  const shown = useDeferredValue(text, "");
  return (
    <p role="status" className="sr-only">
      {shown}
    </p>
  );
}

/** Only the live hero names its parts: a ceremony owns the card while one plays or waits. */
function liveMorphId(
  slug: string,
  active: ActiveCeremony | null,
): string | undefined {
  return active !== null || pendingCeremonySlug() === slug
    ? undefined
    : communityMorphId(slug);
}

/** An in-place welcome stands on the page; any other ceremony on a full-screen backdrop. */
function CeremonyBackdrop({
  placement,
  children,
}: Readonly<{ placement: StagePlacement; children: ReactNode }>) {
  return placement === "in-place" ? (
    <>{children}</>
  ) : (
    <StageBackdrop>{children}</StageBackdrop>
  );
}

/**
 * The community's front door: the shared hero card. A join from the Visiting bar welcomes in place.
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
  const playing = ceremony.active !== null;
  const morphId = liveMorphId(slug, ceremony.active);
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const { member, visiting } = useMembershipStanding(slug);
  const markJoined = useFocusFooterAfterJoin(hero, !playing && member, playing);
  useRegisterVisitingJoin(() => {
    markJoined();
    ceremony.welcome();
  }, playing);

  const meta = <MemberFacepile members={faces} max={HERO_FACES_SHOWN} />;
  // A visitor's card has no footer: Join lives in the Visiting bar above the page.
  const footer = visiting ? undefined : (
    <HeroFooter community={community} slug={slug} access={access} />
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
        className={cn("rounded-lg", playing && !reduced && "invisible")}
      >
        <CommunityHeroCard
          community={community}
          morphId={morphId}
          meta={meta}
          footer={footer}
          cornerControl={cornerControl}
          backdrop={
            playing ? undefined : <HeroAmbientField community={community} />
          }
        />
      </div>
      {ceremony.active !== null && (
        <CeremonyBackdrop placement={ceremony.active.placement}>
          <Suspense fallback={null}>
            <LazyHeroCeremony
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
        </CeremonyBackdrop>
      )}
      <Announcement
        key={ceremony.announcementKey}
        text={ceremony.announcement}
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
