import { Link } from "react-router-dom";

import { useChatDestinationsQuery } from "@/features/community/api/use-chat-destinations";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { DangerAlert } from "@/features/community/ui/DangerAlert";

interface ChatChannelWarningProps {
  slug: string;
}

const DESTINATION_LIST_FORMAT = new Intl.ListFormat("en", {
  style: "long",
  type: "conjunction",
});

/** Reads "#eng-leads", "#eng-leads and #other-team", or "#eng-leads,
 *  #other-team, and #another" instead of a bare comma join, which reads as a
 *  fragment once there are three or more names. */
function namesSentence(names: string[]): string {
  return DESTINATION_LIST_FORMAT.format(names);
}

/**
 * Surfaces every broken chat destination on the community's own home, not only
 * inside Communication settings, so a leader who never opens that page again
 * still meets the problem in normal traffic. Gated on the same `communication`
 * axis `CommunityMenu` hides the settings link behind: today an ordinary member
 * has no way to learn this community posts to chat at all, and a broken
 * destination's name and last error are leadership-only information, not
 * something to disclose to every member (or non-member) who opens the page.
 * Silent while the destinations query is disabled, still loading, or has
 * failed: a warning that flashes in on every page load, or one raised by a
 * secondary query's own outage, would be worse than the problem it reports.
 */
export function ChatChannelWarning({
  slug,
}: Readonly<ChatChannelWarningProps>) {
  const access = useManagementAccess(slug);
  const { data: destinations } = useChatDestinationsQuery(slug, {
    enabled: access.communication,
  });
  const broken = (destinations ?? []).filter(
    (destination) => destination.broken,
  );

  if (!access.communication || broken.length === 0) {
    return null;
  }

  return (
    <DangerAlert>
      <p>
        axonia has stopped posting to{" "}
        {namesSentence(broken.map((destination) => destination.name))}.
      </p>
      <Link
        to={`/communities/${slug}/settings/communication`}
        className="text-danger font-medium underline-offset-2 hover:underline"
      >
        Fix it in Communication settings
      </Link>
    </DangerAlert>
  );
}
