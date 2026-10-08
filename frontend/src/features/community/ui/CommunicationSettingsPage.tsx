import { useParams } from "react-router-dom";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useCommunicationSettingsQuery } from "@/features/community/api/use-communication-settings";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import type { CommunicationSettings } from "@/features/community/domain/communication-settings";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import { DestinationsCard } from "@/features/community/ui/DestinationsCard";
import { SessionMessagesCard } from "@/features/community/ui/SessionMessagesCard";

interface BodyProps {
  slug: string;
  canManage: boolean;
  settings: CommunicationSettings | undefined;
  isPending: boolean;
  communicationsEnabled: boolean;
}

/** Mirrors `CommunityPolicyPage`'s `PolicyBody`: the denied/loading/error strings
 *  render as the body of the section, below the header - so a denied member still
 *  sees what "Communication settings" is and an organizer's first paint (loading)
 *  isn't a bare, unstyled sentence with no page title. */
function CommunicationSettingsBody({
  slug,
  canManage,
  settings,
  isPending,
  communicationsEnabled,
}: Readonly<BodyProps>) {
  if (!canManage) {
    return (
      <p className="text-muted-foreground text-sm">
        No access to communication settings.
      </p>
    );
  }
  if (isPending) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (settings == null) {
    return (
      <p role="alert" className="text-muted-foreground text-sm">
        Could not load the communication settings.
      </p>
    );
  }
  return (
    <div className="flex max-w-xl flex-col gap-5">
      <SessionMessagesCard
        slug={slug}
        initial={settings}
        communicationsEnabled={communicationsEnabled}
      />
      <DestinationsCard slug={slug} />
    </div>
  );
}

/**
 * Community-level communication settings (the second-widest governance layer,
 * behind the delegable `canManageCommunication` axis): whether this community sends
 * upcoming-session and wrap-up messages at all, how far ahead the reminder goes out,
 * and how long the wrap-up waits after a session is marked held. One combined
 * request on Save, matching the backend's single PUT - never a partial write of just
 * the field a person touched.
 */
export function CommunicationSettingsPage() {
  const { slug = "" } = useParams();
  const access = useManagementAccess(slug);
  const settingsQuery = useCommunicationSettingsQuery(slug);
  const { data: organization } = useCurrentOrganization();
  useCommunityCrumbs([{ label: "Communication" }]);

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h1 className="text-foreground font-display text-xl font-semibold">
          Communication
        </h1>
        <p className="text-muted-foreground text-sm">
          What this community sends, and where it goes.
        </p>
      </header>
      <CommunicationSettingsBody
        slug={slug}
        canManage={access.communication}
        settings={settingsQuery.data}
        isPending={settingsQuery.isPending}
        communicationsEnabled={organization?.communicationsEnabled ?? true}
      />
    </section>
  );
}
