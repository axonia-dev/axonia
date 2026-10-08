import type { ReactNode } from "react";

import { KindBadge } from "@/components/patterns/KindBadge";
import { AnimatedCount } from "@/components/ui/AnimatedCount";
import {
  type Community,
  isArchived,
} from "@/features/community/domain/community";
import {
  communityHue,
  focalPosition,
  IDENTITY_WASH,
} from "@/features/community/ui/community-accent";
import { cn } from "@/lib/cn";
import { pluralWord } from "@/lib/plural";

interface HeroIdentityProps {
  community: Community;
  hasBanner: boolean;
  showBadge: boolean;
  editing: boolean;
  badgeOverlay?: ReactNode;
  meta?: ReactNode;
  footer?: ReactNode;
}

function Badge({
  community,
  hasBanner,
  editing,
  overlay,
}: Readonly<{
  community: Community;
  hasBanner: boolean;
  editing: boolean;
  overlay?: ReactNode;
}>) {
  return (
    <div
      data-focal-frame
      className={cn(
        "relative mb-3 inline-flex",
        hasBanner ? "-mt-14" : "mt-0",
        editing && "group/badge",
        editing && community.badgeUrl == null && "size-24",
      )}
    >
      {community.badgeUrl != null && (
        <img
          data-focal-image
          src={community.badgeUrl}
          alt={community.name}
          style={{ objectPosition: focalPosition(community.badgeFocus) }}
          className="border-surface size-24 rounded-2xl border-4 object-cover shadow-md outline-3 -outline-offset-7 outline-[var(--community-accent)]"
        />
      )}
      {overlay}
    </div>
  );
}

export function HeroIdentity({
  community,
  hasBanner,
  showBadge,
  editing,
  badgeOverlay,
  meta,
  footer,
}: Readonly<HeroIdentityProps>) {
  return (
    <div
      className={cn("relative", hasBanner ? "px-8 pb-8" : "p-8")}
      style={hasBanner ? IDENTITY_WASH : { backgroundColor: "var(--surface)" }}
    >
      {showBadge && (
        <Badge
          community={community}
          hasBanner={hasBanner}
          editing={editing}
          overlay={badgeOverlay}
        />
      )}
      <div className="text-muted-foreground flex flex-wrap items-center gap-2 text-sm">
        <KindBadge
          kindId={community.kindId}
          label={community.kind}
          accentColor={communityHue(community)}
        />
        {meta}
        <span>
          <AnimatedCount value={community.memberCount} />{" "}
          {pluralWord(community.memberCount, "member")}
        </span>
      </div>
      <h1 className="font-display text-foreground mt-3 text-4xl font-bold tracking-tight">
        {community.name}
      </h1>
      {community.description && (
        <p className="text-foreground mt-2.5 max-w-prose text-lg">
          {community.description}
        </p>
      )}
      {isArchived(community) && (
        <p className="text-muted-foreground mt-2 text-sm font-medium">
          Archived
        </p>
      )}
      {footer && <div className="mt-5">{footer}</div>}
    </div>
  );
}
