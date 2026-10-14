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
import { Button } from "@/components/ui/Button";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  type ManagementAccess,
  useManagementAccess,
} from "@/features/community/api/use-management-access";
import { useMyMembership } from "@/features/community/api/use-membership";
import { type Community } from "@/features/community/domain/community";
import { LazyHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";
import { useHeroCeremony } from "@/features/community/ui/ceremony/use-hero-ceremony";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { CommunityMenu } from "@/features/community/ui/CommunityMenu";
import { JoinAction } from "@/features/community/ui/JoinAction";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import {
  HERO_FACES_SHOWN,
  useHeroFaces,
} from "@/features/community/ui/use-hero-faces";
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

/** A polite live region, filled one pass after it mounts so a screen reader hears it; a new `key` announces the same words again. */
function Announcement({ text }: Readonly<{ text: string }>) {
  const shown = useDeferredValue(text, "");
  return (
    <p role="status" className="sr-only">
      {shown}
    </p>
  );
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
  const playing = ceremony.active !== null;
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const { data: membership } = useMyMembership(slug);
  const markJoined = useFocusFooterAfterJoin(
    hero,
    !playing && membership != null,
    playing,
  );
  const onJoined = () => {
    markJoined();
    ceremony.welcome();
  };

  const meta = <MemberFacepile members={faces} max={HERO_FACES_SHOWN} />;
  const footer = (
    <HeroFooter
      community={community}
      slug={slug}
      access={access}
      onJoined={onJoined}
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
        className={cn("rounded-lg", playing && !reduced && "invisible")}
      >
        <CommunityHeroCard
          community={community}
          meta={meta}
          footer={footer}
          cornerControl={cornerControl}
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
