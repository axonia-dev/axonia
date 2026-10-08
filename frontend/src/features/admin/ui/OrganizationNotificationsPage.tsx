import { PageHeader } from "@/components/patterns/PageHeader";
import { useEdition } from "@/edition-context";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { CommunicationsKillSwitch } from "@/features/admin/ui/CommunicationsKillSwitch";
import { PermissionsLoadFailed } from "@/features/admin/ui/RequirePermission";
import { RoundupCard } from "@/features/admin/ui/RoundupCard";

/**
 * Every mail the organization sends on its own behalf: the edition's own card first, then the kill
 * switch, then the roundup. Each card gates itself; permissions that could not load say so.
 */
export function OrganizationNotificationsPage() {
  const { isError, retry } = usePermissions();
  const { notificationsCard } = useEdition();
  if (isError) {
    return <PermissionsLoadFailed title="Notifications" read={retry} />;
  }
  const EditionCard = notificationsCard?.Card;
  return (
    <div className="flex flex-col gap-8">
      <PageHeader title="Notifications" />
      {EditionCard !== undefined && <EditionCard />}
      <CommunicationsKillSwitch />
      <RoundupCard />
    </div>
  );
}
