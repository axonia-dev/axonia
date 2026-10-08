import { useNavigate, useParams } from "react-router-dom";

import { Breadcrumb } from "@/components/patterns/Breadcrumb";
import { PageHeader } from "@/components/patterns/PageHeader";
import { useCommunity } from "@/features/community/api/use-communities";
import { CommunityForm } from "@/features/community/ui/CommunityForm";

export function CommunityEditPage() {
  const { slug = "" } = useParams();
  const navigate = useNavigate();
  const { data, isLoading } = useCommunity(slug);

  if (isLoading || !data) {
    return <p className="text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: "Communities", to: "/communities" },
          { label: data.name, to: `/communities/${slug}` },
          { label: "Edit" },
        ]}
      />
      <PageHeader title={`Edit ${data.name}`} />
      <CommunityForm
        community={data}
        onCreated={(s) => {
          void navigate(`/communities/${s}`);
        }}
      />
    </div>
  );
}
