import type { ReactNode } from "react";

import { KindBadge } from "@/components/patterns/KindBadge";
import { MorphPart } from "@/components/patterns/morph/MorphPart";
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
  morphId?: string | undefined;
}

function Badge({
  community,
  hasBanner,
  editing,
  overlay,
  morphId,
}: Readonly<{
  community: Community;
  hasBanner: boolean;
  editing: boolean;
  overlay?: ReactNode;
  morphId: string | undefined;
}>) {
  return (
    <div
      data-focal-frame
      data-ceremony-part="badge"
      className={cn(
        "relative mb-3 inline-flex",
        hasBanner ? "-mt-14" : "mt-0",
        editing && "group/badge",
        editing && community.badgeUrl == null && "size-24",
      )}
    >
      {community.badgeUrl != null && (
        <MorphPart part="badge" id={morphId}>
          <img
            data-focal-image
            src={community.badgeUrl}
            alt={community.name}
            style={{ objectPosition: focalPosition(community.badgeFocus) }}
            className="border-surface size-24 rounded-2xl border-4 object-cover shadow-md outline-3 -outline-offset-7 outline-[var(--community-accent)]"
          />
        </MorphPart>
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
  morphId,
}: Readonly<HeroIdentityProps>) {
  return (
    <div
      data-ceremony-part="identity"
      className={cn("relative", hasBanner ? "px-8 pb-8" : "p-8")}
      style={hasBanner ? IDENTITY_WASH : { backgroundColor: "var(--surface)" }}
    >
      {/* The ground stays under the hero's ambient field; only the content rises over it. */}
      <div className="relative z-[2]">
        {showBadge && (
          <Badge
            community={community}
            hasBanner={hasBanner}
            editing={editing}
            overlay={badgeOverlay}
            morphId={morphId}
          />
        )}
        <div
          data-ceremony-part="meta"
          className="text-muted-foreground flex flex-wrap items-center gap-2 text-sm"
        >
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
        <h1
          data-ceremony-part="name"
          className="font-display text-foreground mt-3 text-4xl font-bold tracking-tight"
        >
          <MorphPart part="name" id={morphId}>
            <span className="inline-block">{community.name}</span>
          </MorphPart>
        </h1>
        {community.description && (
          <p
            data-ceremony-part="description"
            className="text-foreground mt-2.5 max-w-prose text-lg"
          >
            {community.description}
          </p>
        )}
        {isArchived(community) && (
          <p className="text-muted-foreground mt-2 text-sm font-medium">
            Archived
          </p>
        )}
        {footer && (
          <div data-ceremony-part="footer" className="mt-5">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
