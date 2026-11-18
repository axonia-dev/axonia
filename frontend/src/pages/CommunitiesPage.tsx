import { Link } from "react-router-dom";

import { PageHeader } from "@/components/patterns/PageHeader";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { CommunityLobby } from "@/features/community/ui/lobby/CommunityLobby";
import { LOBBY_COLUMN } from "@/features/community/ui/lobby/lobby-column";

export function CommunitiesPage() {
  const { can } = usePermissions();
  const canCreate = can("community:create");
  return (
    <div className="flex flex-col gap-6">
      <div className={LOBBY_COLUMN}>
        <PageHeader
          title="Communities"
          action={
            canCreate ? (
              <Link
                to="/communities/new"
                className="bg-primary text-primary-foreground hover:bg-primary-strong inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition-colors"
              >
                Start a community
              </Link>
            ) : undefined
          }
        />
      </div>
      <CommunityLobby canCreate={canCreate} />
    </div>
  );
}
