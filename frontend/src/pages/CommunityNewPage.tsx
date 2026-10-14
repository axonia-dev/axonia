import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { Breadcrumb } from "@/components/patterns/Breadcrumb";
import { PageHeader } from "@/components/patterns/PageHeader";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { departForCeremony } from "@/features/community/ui/ceremony/ceremony-arrival";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";
import { CommunityForm } from "@/features/community/ui/CommunityForm";

export function CommunityNewPage() {
  const navigate = useNavigate();
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
      <CommunityForm
        onCreated={(slug) => {
          void departForCeremony("birth", slug).then(() =>
            navigate(`/communities/${slug}`),
          );
        }}
      />
    </div>
  );
}
