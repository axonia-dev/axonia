import type { CSSProperties } from "react";

import { MorphLink } from "@/components/patterns/morph/MorphLink";
import {
  communityMorphId,
  MorphPart,
} from "@/components/patterns/morph/MorphPart";
import { Avatar } from "@/components/ui/Avatar";
import { useSeedCommunityDetail } from "@/features/community/api/seed-community-detail";
import type { CommunityListing } from "@/features/community/domain/community";
import {
  communityHue,
  focalPosition,
} from "@/features/community/ui/community-accent";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import { plural } from "@/lib/plural";

export function CommunityTile({
  community,
}: Readonly<{ community: CommunityListing }>) {
  const accent = communityHue(community);
  const style = { "--community-accent": accent } as CSSProperties;
  const id = communityMorphId(community.slug);
  const seed = useSeedCommunityDetail();
  return (
    <MorphLink
      to={`/communities/${community.slug}`}
      ready={() => seed(community)}
      style={style}
      className="group border-border bg-surface focus-visible:ring-focus-ring flex w-full flex-col overflow-hidden rounded-lg border transition-[transform,box-shadow] duration-150 outline-none hover:shadow-md focus-visible:ring-2 motion-safe:hover:-translate-y-0.5"
    >
      <MorphPart part="banner" id={id}>
        <div
          data-tile-banner
          className="relative h-14 bg-linear-to-b from-[color-mix(in_srgb,var(--community-accent)_45%,var(--surface))] to-(--surface)"
        >
          {community.heroUrl != null && (
            <img
              src={community.heroUrl}
              alt=""
              className="absolute inset-0 size-full object-cover"
              style={{ objectPosition: focalPosition(community.heroFocus) }}
            />
          )}
        </div>
      </MorphPart>
      <div className="flex flex-1 flex-col gap-2 px-4 pb-4">
        <MorphPart
          part="badge"
          id={community.badgeUrl == null ? undefined : id}
        >
          <Avatar
            name={community.name}
            hue={accent}
            src={community.badgeUrl ?? null}
            className="border-surface relative -mt-5 size-10 border-[3px]"
          />
        </MorphPart>
        <MorphPart part="name" id={id}>
          <span className="text-foreground line-clamp-2 w-fit font-semibold">
            {community.name}
          </span>
        </MorphPart>
        <span className="mt-auto flex items-center justify-between gap-2">
          <span className="text-muted-foreground text-sm">
            {plural(community.memberCount, "member")}
          </span>
          {community.role !== null && <MembershipChip role={community.role} />}
        </span>
      </div>
    </MorphLink>
  );
}
