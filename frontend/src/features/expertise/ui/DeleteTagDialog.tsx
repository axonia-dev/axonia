import type { UseQueryResult } from "@tanstack/react-query";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useTagUsage } from "@/features/expertise/api/use-tag-lifecycle";
import type { TagUsage } from "@/features/expertise/domain/expertise";
import { plural } from "@/lib/plural";

interface Props {
  slug: string;
  /** The tag being deleted. `null` closes the dialog. */
  tag: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}

function contentParts(usage: TagUsage): string[] {
  const parts: string[] = [];
  if (usage.artifacts > 0) parts.push(plural(usage.artifacts, "artifact"));
  if (usage.topics > 0) parts.push(plural(usage.topics, "topic"));
  if (usage.talks > 0) parts.push(plural(usage.talks, "talk"));
  return parts;
}

/** "a, b and c", so the sentence reads as prose rather than as a list. */
function joined(parts: string[]): string {
  if (parts.length < 2) return parts[0] ?? "";
  return `${parts.slice(0, -1).join(", ")} and ${parts.at(-1)}`;
}

/** "1 member’s declarations" / "3 members’ declarations", the possessive the count needs. */
function declarersPart(members: number): string {
  if (members === 0) return "";
  return members === 1
    ? "1 member’s declarations"
    : `${members} members’ declarations`;
}

/** "1 member’s areas to learn" / "3 members’ areas to learn". */
function learnersPart(members: number): string {
  if (members === 0) return "";
  return members === 1
    ? "1 member’s areas to learn"
    : `${members} members’ areas to learn`;
}

/** What the delete takes away: content loses the tag, and the members who declared it or want to
 *  learn it lose it too. Both counts are members, each counted once whatever spelling they typed. */
function deleteEffect(tag: string, usage: TagUsage): string {
  const content = contentParts(usage);
  const members = [
    declarersPart(usage.declarations),
    learnersPart(usage.learningIntents),
  ].filter(Boolean);
  if (content.length === 0 && members.length === 0) {
    return `Nothing in the community carries ${tag}. Deleting it only clears the vocabulary.`;
  }
  if (content.length === 0) {
    return `Removes ${tag} from ${joined(members)}.`;
  }
  return `Strips ${tag} from ${joined([...content, ...members])}.`;
}

interface SummaryProps {
  tag: string;
  usageQuery: UseQueryResult<TagUsage>;
}

/** Names what the delete will actually take away, kind by kind. */
function UsageSummary({ tag, usageQuery }: Readonly<SummaryProps>) {
  if (usageQuery.isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        {"Couldn’t check what uses this tag."}
      </p>
    );
  }
  if (!usageQuery.isSuccess) {
    return (
      <p className="text-muted-foreground text-sm">
        Checking what uses this tag…
      </p>
    );
  }
  return (
    <p className="text-foreground text-sm">
      {deleteEffect(tag, usageQuery.data)}
    </p>
  );
}

/**
 * Deletes a tag from the community's content. The counts land before the button unlocks: a
 * curator must not confirm a destructive act before seeing its size.
 */
export function DeleteTagDialog({
  slug,
  tag,
  onCancel,
  onConfirm,
}: Readonly<Props>) {
  const usageQuery = useTagUsage(slug, tag);
  const current = tag ?? "";

  return (
    <Dialog
      isOpen={tag !== null}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
      title={`Delete ${current}?`}
      role="alertdialog"
    >
      <div className="flex flex-col gap-4">
        <UsageSummary tag={current} usageQuery={usageQuery} />
        <p className="text-muted-foreground text-sm">This cannot be undone.</p>
        <DialogFooter>
          <Button variant="ghost" onPress={onCancel}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onPress={onConfirm}
            isDisabled={!usageQuery.isSuccess}
          >
            Delete
          </Button>
        </DialogFooter>
      </div>
    </Dialog>
  );
}
