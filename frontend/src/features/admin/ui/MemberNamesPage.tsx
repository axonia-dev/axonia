import { Plus } from "lucide-react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { PageHeader } from "@/components/patterns/PageHeader";
import { Button } from "@/components/ui/Button";
import { useMemberNamePatterns } from "@/features/admin/api/use-member-names";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  changedLabel,
  fieldNumbers,
} from "@/features/admin/domain/member-name-rows";
import { MAX_NAME_PATTERNS } from "@/features/admin/domain/member-names";
import { PatternField } from "@/features/admin/ui/MemberNamePatternField";
import { PreviewTable } from "@/features/admin/ui/MemberNamePreviewTable";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import { useNamePreview } from "@/features/admin/ui/use-name-preview";
import { usePatternEditor } from "@/features/admin/ui/use-pattern-editor";
import { useSaveOutcome } from "@/features/admin/ui/use-save-outcome";
import { failedToLoad } from "@/lib/failed-to-load";

const PLACEHOLDERS = ["{given}", "{family}"] as const;

interface EditorProps {
  stored: ReadonlyArray<string>;
  canWrite: boolean;
}

type Editor = ReturnType<typeof usePatternEditor>;

interface PatternListProps {
  editor: Editor;
  canWrite: boolean;
  refusal: string | null;
  refusedId: number | undefined;
}

function PatternList({
  editor,
  canWrite,
  refusal,
  refusedId,
}: Readonly<PatternListProps>) {
  const { rows } = editor;
  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <h2 className="text-foreground text-base font-semibold">Patterns</h2>
        <p className="text-muted-foreground text-sm">
          Patterns are tried in order. The first match shows a name as given
          name then family name. Other names are kept as received.
        </p>
      </div>
      {rows.length === 0 && (
        <p className="text-muted-foreground text-sm">
          No patterns. Names are shown as received.
        </p>
      )}
      <ol className="flex flex-col gap-2">
        {rows.map((row, index) => (
          <PatternField
            key={row.id}
            row={row}
            position={index}
            count={rows.length}
            canWrite={canWrite}
            errorMessage={refusedId === row.id ? refusal : null}
            inputRef={(element) => editor.registerInput(row.id, element)}
            onFocus={() => editor.focus(row.id)}
            onChange={(text) => editor.setText(row.id, text)}
            onMove={(offset) => editor.move(index, offset)}
            onRemove={() => editor.remove(row.id)}
          />
        ))}
      </ol>
      {canWrite && (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            isDisabled={rows.length >= MAX_NAME_PATTERNS}
            onPress={editor.add}
          >
            <Plus aria-hidden />
            Add pattern
          </Button>
          {PLACEHOLDERS.map((placeholder) => (
            <Button
              key={placeholder}
              variant="secondary"
              size="sm"
              aria-label={`Insert ${placeholder}`}
              onPress={() => editor.insertPlaceholder(placeholder)}
            >
              <span className="font-mono">{placeholder}</span>
            </Button>
          ))}
        </div>
      )}
    </section>
  );
}

interface SaveBarProps {
  canSave: boolean;
  isPending: boolean;
  errorMessage: string | null;
  savedCount: number | null;
  onSave: () => void;
}

function SaveBar({
  canSave,
  isPending,
  errorMessage,
  savedCount,
  onSave,
}: Readonly<SaveBarProps>) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button isDisabled={!canSave} onPress={onSave}>
        {isPending ? "Saving…" : "Save"}
      </Button>
      {savedCount !== null && (
        <p role="status" className="text-muted-foreground text-sm">
          {changedLabel(savedCount)}
        </p>
      )}
      {errorMessage !== null && (
        <p role="alert" className="text-danger text-sm">
          {errorMessage}
        </p>
      )}
    </div>
  );
}

function MemberNamesEditor({ stored, canWrite }: Readonly<EditorProps>) {
  const editor = usePatternEditor(stored);
  const save = useSaveOutcome(editor.patterns, editor.edits);
  const preview = useNamePreview(editor.patterns, editor.rows);
  const canSave =
    JSON.stringify(editor.patterns) !== JSON.stringify(stored) &&
    preview.isReady &&
    !save.isPending;

  return (
    <div className="flex flex-col gap-8">
      <PatternList
        editor={editor}
        canWrite={canWrite}
        refusal={preview.refusal}
        refusedId={preview.refusedId}
      />
      <section className="flex flex-col gap-3">
        <h2 className="text-foreground text-base font-semibold">Preview</h2>
        {preview.failed && (
          <LoadFailed
            inline
            message="Could not load the preview."
            query={preview.query}
          />
        )}
        <PreviewTable
          rows={preview.rows}
          numbers={fieldNumbers(editor.rows)}
          dimmed={preview.dimmed}
        />
      </section>
      {canWrite && (
        <SaveBar
          canSave={canSave}
          isPending={save.isPending}
          errorMessage={save.error}
          savedCount={save.count}
          onSave={save.submit}
        />
      )}
    </div>
  );
}

function MemberNamesPageContent() {
  const { can } = usePermissions();
  const patternsQuery = useMemberNamePatterns();
  return (
    <div className="flex flex-col gap-8">
      <PageHeader title="Member names" />
      <MemberNamesBody query={patternsQuery} canWrite={can("member:write")} />
    </div>
  );
}

function MemberNamesBody({
  query,
  canWrite,
}: Readonly<{
  query: ReturnType<typeof useMemberNamePatterns>;
  canWrite: boolean;
}>) {
  if (failedToLoad(query)) {
    return (
      <LoadFailed
        inline
        message="Could not load the name patterns."
        query={query}
      />
    );
  }
  if (query.data === undefined) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  return <MemberNamesEditor stored={query.data} canWrite={canWrite} />;
}

export function MemberNamesPage() {
  return (
    <RequirePermission permission="member:read" title="Member names">
      <MemberNamesPageContent />
    </RequirePermission>
  );
}
