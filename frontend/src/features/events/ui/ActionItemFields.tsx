import { useState } from "react";

import {
  MemberSelectField,
  type PickedMember,
} from "@/components/patterns/MemberSelectField";
import { Button } from "@/components/ui/Button";
import { DatePicker } from "@/components/ui/DatePicker";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import type {
  ActionItem,
  UpdateActionItemInput,
} from "@/features/events/domain/action-item";
import { ActionOutcomeField } from "@/features/events/ui/ActionOutcomeField";
import { CheckpointSelect } from "@/features/events/ui/CheckpointSelect";

/** What an action item form holds. `dueDate` is the picker's own empty-string-for-none, turned
 *  back into a null on the way out. `outcome` is display/edit state only - it is applied through
 *  the settle routes, never through the update payload. */
export interface ActionItemFormValue {
  title: string;
  assignee: PickedMember | null;
  checkpointSessionId: number | null;
  description: string;
  dueDate: string;
  outcome: ActionItem["outcome"];
}

export const EMPTY_ACTION_ITEM_FORM: ActionItemFormValue = {
  title: "",
  assignee: null,
  checkpointSessionId: null,
  description: "",
  dueDate: "",
  outcome: "OPEN",
};

export function actionItemFormValueOf(item: ActionItem): ActionItemFormValue {
  return {
    title: item.title,
    assignee:
      item.assigneeUserId === null
        ? null
        : {
            userId: item.assigneeUserId,
            displayName: item.assigneeDisplayName ?? item.assigneeUserId,
          },
    checkpointSessionId: item.checkpoint.pinned
      ? item.checkpoint.sessionId
      : null,
    description: item.description,
    dueDate: item.dueDate ?? "",
    outcome: item.outcome,
  };
}

export function actionItemInputOf(
  value: ActionItemFormValue,
): UpdateActionItemInput {
  return {
    title: value.title,
    description: value.description,
    assignee: value.assignee?.userId ?? null,
    dueDate: value.dueDate === "" ? null : value.dueDate,
    checkpointSessionId: value.checkpointSessionId,
  };
}

interface Props {
  slug: string;
  value: ActionItemFormValue;
  onChange: (value: ActionItemFormValue) => void;
  /** Offers the status control. Off by default - capture has no status to correct yet. */
  showStatus?: boolean;
}

/**
 * The fields an action item is written with, shared by capture and edit so the two never drift.
 * Title, assignee and the session it is answered at lead; description and due date wait behind
 * "More", since the session is owed and the due date is not. An item that already carries one of
 * the two opens with them revealed, so an edit never hides what it is about to save.
 */
export function ActionItemFields({
  slug,
  value,
  onChange,
  showStatus = false,
}: Readonly<Props>) {
  const [showMore, setShowMore] = useState(
    value.description !== "" || value.dueDate !== "",
  );

  function update(patch: Partial<ActionItemFormValue>) {
    onChange({ ...value, ...patch });
  }

  return (
    <>
      {showStatus && (
        <ActionOutcomeField
          label="Status"
          value={value.outcome}
          onChange={(outcome) => update({ outcome })}
        />
      )}
      <TextInput
        label="Title"
        value={value.title}
        onChange={(title) => update({ title })}
        isRequired
      />
      <MemberSelectField
        slug={slug}
        label="Assignee"
        placeholder="Choose an assignee"
        picked={value.assignee}
        onPick={(assignee) => update({ assignee })}
        onClear={() => update({ assignee: null })}
      />
      <CheckpointSelect
        slug={slug}
        value={value.checkpointSessionId}
        onChange={(checkpointSessionId) => update({ checkpointSessionId })}
      />
      {showMore ? (
        <>
          <TextArea
            label="Description"
            value={value.description}
            onChange={(description) => update({ description })}
            rows={3}
          />
          <DatePicker
            label="Due date"
            value={value.dueDate}
            onChange={(dueDate) => update({ dueDate })}
          />
        </>
      ) : (
        <Button
          variant="ghost"
          size="sm"
          className="self-start"
          onPress={() => setShowMore(true)}
        >
          More
        </Button>
      )}
    </>
  );
}
