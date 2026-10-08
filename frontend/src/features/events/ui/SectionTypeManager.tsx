import { LoadFailed } from "@/components/patterns/LoadFailed";
import { MasterDetailManager } from "@/components/patterns/MasterDetailManager";
import { useTypeManager } from "@/components/patterns/useTypeManager";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useCreateSectionType,
  useDeleteSectionType,
  useSectionTypes,
  useUpdateSectionType,
} from "@/features/events/api/use-section-types";
import type { SectionTypeInput } from "@/features/events/domain/events-gateway";
import type { SectionType } from "@/features/events/domain/section-type";
import { SectionTypeIcon } from "@/features/events/ui/section-type-visual";
import { SectionTypeEditor } from "@/features/events/ui/SectionTypeEditor";
import { failedToLoad } from "@/lib/failed-to-load";

export function SectionTypeManager() {
  const types = useSectionTypes();
  const create = useCreateSectionType();
  const update = useUpdateSectionType();
  const remove = useDeleteSectionType();
  const { can } = usePermissions();
  const canWrite = can("section_type:write");

  const { selection, selectedId, saveError, select, save } =
    useTypeManager<SectionTypeInput>({
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
        message="Could not load the section types."
        query={types}
      />
    );
  }

  return (
    <MasterDetailManager<SectionType>
      items={all}
      getId={(t) => t.id}
      noun="type"
      canWrite={canWrite}
      selection={selection}
      onSelect={select}
      renderRowContent={(type) => (
        <>
          <SectionTypeIcon
            typeId={type.id}
            label={type.label}
            icon={type.icon}
            accentColor={type.accentColor}
            size={16}
          />
          <span className="font-medium">{type.label}</span>
        </>
      )}
      detailTitle={(curr, isDraft) =>
        isDraft ? "New type" : (curr?.label ?? "")
      }
      renderDetail={(curr, isDraft) => (
        <SectionTypeEditor
          // Remount per selection so the editor's fields reset to the selected values.
          key={isDraft ? "draft" : curr?.id}
          typeId={curr === null ? null : curr.id}
          initialLabel={curr === null ? "" : curr.label}
          initialIcon={curr === null ? null : curr.icon}
          initialAccent={curr === null ? null : curr.accentColor}
          initialExpectsPresenter={curr?.expectsPresenter ?? false}
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
