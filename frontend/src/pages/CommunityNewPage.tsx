import { useEffect } from "react";

import { Breadcrumb } from "@/components/patterns/Breadcrumb";
import { PageHeader } from "@/components/patterns/PageHeader";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";
import { useDepartForCeremony } from "@/features/community/ui/ceremony/use-depart-for-ceremony";
import { CommunityForm } from "@/features/community/ui/CommunityForm";

export function CommunityNewPage() {
  const depart = useDepartForCeremony();
  const { data: currentOrg } = useCurrentOrganization();
  useEffect(preloadHeroCeremony, []);
  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: "Communities", to: "/communities" },
          { label: "New community" },
        ]}
      />
      <PageHeader title="New community" />
      {currentOrg && (
        <p className="text-muted-foreground text-sm">
          Creating in{" "}
          <span className="text-foreground font-medium">{currentOrg.name}</span>
        </p>
      )}
      <CommunityForm onCreated={(slug) => void depart("birth", slug)} />
    </div>
  );
}
