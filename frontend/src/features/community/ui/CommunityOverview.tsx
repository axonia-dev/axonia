import { useParams } from "react-router-dom";

import { useCommunity } from "@/features/community/api/use-communities";
import { ChatChannelWarning } from "@/features/community/ui/ChatChannelWarning";
import { CommunityPulse } from "@/features/community/ui/CommunityPulse";
import { IdentityHero } from "@/features/community/ui/IdentityHero";
import { LibraryFeed } from "@/features/community/ui/LibraryFeed";
import { MostLovedFeed } from "@/features/community/ui/MostLovedFeed";
import { NewMembers } from "@/features/community/ui/NewMembers";
import { SessionMarquee } from "@/features/community/ui/SessionMarquee";
import { TrendingTags } from "@/features/community/ui/TrendingTags";

/**
 * The community home: present the guild, catch a reader up on what it just did and what it does
 * next, then show what it has made and quantify its health. Each section owns its own
 * loading/empty/error state so one slow context never blanks the page.
 *
 * The marquee sits full width above the columns rather than inside one: it is the page's answer to
 * "what did I miss", and it carries the next session too, which is why neither the hero nor the
 * rail states that any more.
 */
export function CommunityOverview() {
  const { slug = "" } = useParams();
  const { data: community, isLoading, isError } = useCommunity(slug);

  if (isLoading) {
    return <p className="text-muted-foreground">Loading…</p>;
  }
  if (isError || !community) {
    return (
      <p role="alert" className="text-muted-foreground">
        Could not load this community.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <IdentityHero community={community} slug={slug} />
      <ChatChannelWarning slug={slug} />
      <SessionMarquee slug={slug} />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="flex flex-col gap-6">
          <LibraryFeed slug={slug} />
          <MostLovedFeed slug={slug} />
          <TrendingTags slug={slug} />
        </div>
        <div className="flex flex-col gap-6">
          <CommunityPulse slug={slug} memberCount={community.memberCount} />
          <NewMembers slug={slug} />
        </div>
      </div>
    </div>
  );
}
