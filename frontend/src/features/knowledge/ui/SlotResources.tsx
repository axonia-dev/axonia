import { useSessionResources } from "@/features/knowledge/api/use-session-resources";
import { ResourceList } from "@/features/knowledge/ui/ResourceList";

interface Props {
  slug: string;
  sessionId: number;
  segmentId: number;
  canManage?: boolean;
}

export function SlotResources({
  slug,
  sessionId,
  segmentId,
  canManage = false,
}: Readonly<Props>) {
  const query = useSessionResources(slug, sessionId);
  const resources = (query.data ?? []).filter((r) => r.segmentId === segmentId);

  if (query.isLoading) {
    return null;
  }
  if (resources.length === 0) {
    return null;
  }
  return (
    <ResourceList
      slug={slug}
      sessionId={sessionId}
      resources={resources}
      canManage={canManage}
    />
  );
}
