import { useDetachResource } from "@/features/knowledge/api/use-session-resources";
import type { SessionResource } from "@/features/knowledge/domain/session-resource";
import { ResourceRow } from "@/features/knowledge/ui/ResourceRow";

interface Props {
  slug: string;
  sessionId: number;
  resources: SessionResource[];
  canManage: boolean;
}

export function ResourceList({
  slug,
  sessionId,
  resources,
  canManage,
}: Readonly<Props>) {
  const detach = useDetachResource(slug, sessionId);
  return (
    <ul className="flex flex-col gap-1.5">
      {resources.map((resource) => (
        <ResourceRow
          key={`${resource.attachmentId ?? "origin"}-${resource.artifact.id}`}
          resource={resource}
          slug={slug}
          canManage={canManage}
          onDetach={() =>
            detach.mutate({
              artifactId: resource.artifact.id,
              segmentId: resource.segmentId,
            })
          }
        />
      ))}
    </ul>
  );
}
