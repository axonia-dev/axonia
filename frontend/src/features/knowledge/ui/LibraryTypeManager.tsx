import { CountBadge } from "@/components/patterns/CountBadge";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { MasterDetailManager } from "@/components/patterns/MasterDetailManager";
import { useTypeManager } from "@/components/patterns/useTypeManager";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useArtifactTypes,
  useCreateArtifactType,
  useDeleteArtifactType,
  useUpdateArtifactType,
} from "@/features/knowledge/api/use-artifact-types";
import type { ArtifactType } from "@/features/knowledge/domain/artifact-type";
import type { ArtifactTypeInput } from "@/features/knowledge/domain/knowledge-gateway";
import { LibraryTypeEditor } from "@/features/knowledge/ui/LibraryTypeEditor";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import { failedToLoad } from "@/lib/failed-to-load";

export function LibraryTypeManager() {
  const types = useArtifactTypes();
  const create = useCreateArtifactType();
  const update = useUpdateArtifactType();
  const remove = useDeleteArtifactType();
  const { can } = usePermissions();
  const canWrite = can("library_type:write");

  const { selection, selectedId, saveError, select, save } =
    useTypeManager<ArtifactTypeInput>({
      createRecord: (input, onCreated, onError) =>
        create.mutate(input, {
          onSuccess: (created) => onCreated(created.id),
          onError,
        }),
      updateRecord: (id, input, onError) =>
        update.mutate({ id, input }, { onError }),
    });

  const all = types.data ?? [];
  const current = all.find((t) => t.id === selectedId) ?? null;

  if (failedToLoad(types)) {
    return (
      <LoadFailed
        inline
        message="Could not load the library types."
        query={types}
      />
    );
  }

  return (
    <MasterDetailManager<ArtifactType>
      items={all}
      getId={(t) => t.id}
      noun="type"
      canWrite={canWrite}
      selection={selection}
      onSelect={select}
      renderRowContent={(type) => (
        <>
          <TypeIcon
            typeId={type.id}
            label={type.label}
            icon={type.icon}
            accentColor={type.accentColor}
            size={16}
          />
          <span className="font-medium">{type.label}</span>
          {type.artifactCount > 0 && (
            <CountBadge value={type.artifactCount} className="ml-auto" />
          )}
        </>
      )}
      detailTitle={(curr, isDraft) =>
        isDraft ? "New type" : (curr?.label ?? "")
      }
      renderDetail={(curr, isDraft) => (
        <LibraryTypeEditor
          // Remount per selection so the editor's fields reset to the selected values.
          key={isDraft ? "draft" : curr?.id}
          typeId={curr === null ? null : curr.id}
          initialLabel={curr === null ? "" : curr.label}
          initialIcon={curr === null ? null : curr.icon}
          initialAccent={curr === null ? null : curr.accentColor}
          artifactCount={curr === null ? 0 : curr.artifactCount}
          others={all.filter((t) => t.id !== selectedId)}
          onSave={(input) => save(current?.id ?? null, input)}
          isSaving={create.isPending || update.isPending}
          saveError={saveError}
          deleteType={(args) => remove.mutateAsync(args)}
          isDeleting={remove.isPending}
          onDeleted={() => select(null)}
        />
      )}
    />
  );
}
