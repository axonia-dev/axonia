import { Link } from "react-router-dom";

import { PageHeader } from "@/components/patterns/PageHeader";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { CommunityList } from "@/features/community/ui/CommunityList";

export function CommunitiesPage() {
  const { can } = usePermissions();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Communities"
        action={
          can("community:create") ? (
            <Link
              to="/communities/new"
              className="bg-primary text-primary-foreground hover:bg-primary-strong inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition-colors"
            >
              Start a community
            </Link>
          ) : undefined
        }
      />
      <CommunityList />
    </div>
  );
}
