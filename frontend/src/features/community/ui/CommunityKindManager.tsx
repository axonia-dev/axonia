import { CountBadge } from "@/components/patterns/CountBadge";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { MasterDetailManager } from "@/components/patterns/MasterDetailManager";
import { useTypeManager } from "@/components/patterns/useTypeManager";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useCommunityKinds,
  useCreateKind,
  useDeleteKind,
  useUpdateKind,
} from "@/features/community/api/use-community-kinds";
import type { CommunityKind } from "@/features/community/domain/community-kind";
import { KindEditor } from "@/features/community/ui/KindEditor";
import { failedToLoad } from "@/lib/failed-to-load";

export function CommunityKindManager() {
  const kinds = useCommunityKinds();
  const create = useCreateKind();
  const update = useUpdateKind();
  const remove = useDeleteKind();
  const { can } = usePermissions();
  const canWrite = can("community_kind:write");

  const { selection, selectedId, saveError, select, save } =
    useTypeManager<string>({
      createRecord: (label, onCreated, onError) =>
        create.mutate(label, {
          onSuccess: (created) => onCreated(created.id),
          onError,
        }),
      updateRecord: (id, label, onError) =>
        update.mutate({ id, label }, { onError }),
    });

  const all = kinds.data ?? [];
  const current = all.find((k) => k.id === selectedId) ?? null;

  if (failedToLoad(kinds)) {
    return (
      <LoadFailed
        inline
        message="Could not load the community types."
        query={kinds}
      />
    );
  }

  return (
    <MasterDetailManager<CommunityKind>
      items={all}
      getId={(k) => k.id}
      noun="kind"
      canWrite={canWrite}
      selection={selection}
      onSelect={select}
      renderRowContent={(kind) => (
        <>
          <span className="font-medium">{kind.label}</span>
          {kind.communityCount > 0 && (
            <CountBadge value={kind.communityCount} className="ml-auto" />
          )}
        </>
      )}
      detailTitle={(curr, isDraft) =>
        isDraft ? "New kind" : (curr?.label ?? "")
      }
      renderDetail={(curr, isDraft) => (
        <KindEditor
          // Remount per selection so the editor's fields reset to the selected values.
          key={isDraft ? "draft" : curr?.id}
          kindId={curr === null ? null : curr.id}
          initialLabel={curr === null ? "" : curr.label}
          communityCount={curr === null ? 0 : curr.communityCount}
          others={all.filter((k) => k.id !== selectedId)}
          onSave={(label) => save(current?.id ?? null, label)}
          isSaving={create.isPending || update.isPending}
          saveError={saveError}
          deleteKind={(args) => remove.mutateAsync(args)}
          isDeleting={remove.isPending}
          onDeleted={() => select(null)}
        />
      )}
    />
  );
}
