import { useNavigate } from "react-router-dom";

import { Breadcrumb } from "@/components/patterns/Breadcrumb";
import { PageHeader } from "@/components/patterns/PageHeader";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import { CommunityForm } from "@/features/community/ui/CommunityForm";

export function CommunityNewPage() {
  const navigate = useNavigate();
  const { data: currentOrg } = useCurrentOrganization();
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
          handOffCeremony("birth", slug);
          void navigate(`/communities/${slug}`);
        }}
      />
    </div>
  );
}
