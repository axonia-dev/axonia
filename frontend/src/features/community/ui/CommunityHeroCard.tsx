import type { ReactNode } from "react";

import type { Community } from "@/features/community/domain/community";
import { accentVariables } from "@/features/community/ui/community-accent";
import { HeroBanner } from "@/features/community/ui/HeroBanner";
import { HeroIdentity } from "@/features/community/ui/HeroIdentity";

interface Props {
  community: Community;
  editing?: boolean;
  footer?: ReactNode;
  meta?: ReactNode;
  bannerOverlay?: ReactNode;
  badgeOverlay?: ReactNode;
  cornerControl?: ReactNode;
}

export function CommunityHeroCard({
  community,
  editing = false,
  footer,
  meta,
  bannerOverlay,
  badgeOverlay,
  cornerControl,
}: Readonly<Props>) {
  const showBanner = community.heroUrl != null || editing;
  const showBadge = community.badgeUrl != null || editing;

  return (
    <section
      aria-label="Community identity"
      className="flex flex-col gap-2"
      style={accentVariables(community)}
    >
      <div className="border-border bg-surface relative overflow-hidden rounded-lg border shadow-sm">
        {showBanner && (
          <HeroBanner
            heroUrl={community.heroUrl}
            focus={community.heroFocus}
            editing={editing}
            overlay={bannerOverlay}
          />
        )}
        <HeroIdentity
          community={community}
          hasBanner={showBanner}
          showBadge={showBadge}
          editing={editing}
          badgeOverlay={badgeOverlay}
          meta={meta}
          footer={footer}
        />
        {cornerControl}
      </div>
    </section>
  );
}
