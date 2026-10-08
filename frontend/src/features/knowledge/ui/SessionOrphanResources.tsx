import { useSessionResources } from "@/features/knowledge/api/use-session-resources";
import { ResourceList } from "@/features/knowledge/ui/ResourceList";

interface Props {
  slug: string;
  sessionId: number;
  knownSegmentIds: number[];
  canManage?: boolean;
}

export function SessionOrphanResources({
  slug,
  sessionId,
  knownSegmentIds,
  canManage = false,
}: Readonly<Props>) {
  const query = useSessionResources(slug, sessionId);
  const known = new Set(knownSegmentIds);
  const orphans = (query.data ?? []).filter(
    (r) => r.segmentId === null || !known.has(r.segmentId),
  );

  if (orphans.length === 0) {
    return null;
  }
  return (
    <section className="border-border bg-surface-sunken flex flex-col gap-2 rounded-md border p-3">
      <h4 className="text-foreground text-sm font-semibold">
        From this session
      </h4>
      <ResourceList
        slug={slug}
        sessionId={sessionId}
        resources={orphans}
        canManage={canManage}
      />
    </section>
  );
}
