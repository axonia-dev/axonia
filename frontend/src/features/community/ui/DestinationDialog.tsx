import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { TextInput } from "@/components/ui/TextInput";
import {
  useCreateChatDestination,
  useUpdateChatDestination,
} from "@/features/community/api/use-chat-destinations";
import { chatDestinationErrorMessage } from "@/features/community/community-errors";
import type {
  ChatDestination,
  ChatPlatform,
} from "@/features/community/domain/chat-destination";
import {
  CHAT_PLATFORM_COPY,
  CHAT_PLATFORM_OPTIONS,
} from "@/features/community/domain/chat-destination";

/**
 * The consent statement lives here rather than on the page: adding a
 * destination IS the decision to broadcast member-visible content into a room
 * axonia does not control, and this is the moment it is made. Read once, above
 * the button that makes it, rather than skimmed on every visit to a settings
 * page.
 */
function ConsentNotice() {
  return (
    <p className="border-warning bg-surface-sunken text-muted-foreground rounded-r-md border-l-2 px-3 py-2 text-xs">
      Posts carry the same content as the email.
    </p>
  );
}

function canSubmitDestination(
  isEdit: boolean,
  trimmedName: string,
  trimmedUrl: string,
  isPending: boolean,
): boolean {
  if (trimmedName === "" || isPending) {
    return false;
  }
  return isEdit || trimmedUrl !== "";
}

/**
 * The URL field's placeholder and helper text. What to type differs by the chat
 * tool, whose URLs look nothing alike, and by whether a value is already on file
 * - the backend never gives that value back, so an edit cannot prefill it and
 * has to say what leaving the field alone will do.
 *
 * The stored address is named in the description rather than the placeholder
 * because a placeholder clips and a description wraps. A Power Automate mask
 * runs past eighty characters, and clipping takes the tail first: the one part
 * that tells two destinations in the same environment apart, since everything
 * ahead of it is an environment GUID they share.
 */
function urlFieldCopy(
  destination: ChatDestination | null,
  platform: ChatPlatform,
): { placeholder: string; description: string } {
  const { urlPlaceholder, urlHint } = CHAT_PLATFORM_COPY[platform];
  if (destination === null) {
    return { placeholder: urlPlaceholder, description: urlHint };
  }
  return {
    placeholder: "Paste a new URL to replace the current one",
    description: `Leave empty to keep ${destination.maskedUrl}.`,
  };
}

interface DestinationFormProps {
  slug: string;
  destination: ChatDestination | null;
  close: () => void;
  /** Called once a create or update actually succeeds, before `close`: lets
   *  the caller clear state it keeps about this destination outside the
   *  dialog - see `DestinationsCard`'s stale test-outcome clearing. */
  onSaved: () => void;
}

/**
 * Its own state, seeded straight from `destination` with no reset effect: the
 * dialog always remounts this on every open (see the `key` on its call site
 * below), so a fresh mount already starts from the right values - React's own
 * fix for "reset state when a prop changes" rather than a synchronised effect.
 */
function DestinationForm({
  slug,
  destination,
  close,
  onSaved,
}: Readonly<DestinationFormProps>) {
  const create = useCreateChatDestination(slug);
  const update = useUpdateChatDestination(slug);
  const [name, setName] = useState(destination?.name ?? "");
  const [platform, setPlatform] = useState<ChatPlatform>(
    destination?.platform ?? "SLACK",
  );
  const [url, setUrl] = useState("");

  const isEdit = destination !== null;
  const mutation = isEdit ? update : create;
  const trimmedName = name.trim();
  const trimmedUrl = url.trim();
  const canSubmit = canSubmitDestination(
    isEdit,
    trimmedName,
    trimmedUrl,
    mutation.isPending,
  );
  const { placeholder, description } = urlFieldCopy(destination, platform);

  function saved() {
    onSaved();
    close();
  }

  function submit() {
    const input = {
      name: trimmedName,
      platform,
      webhookUrl: trimmedUrl || null,
    };
    if (destination !== null) {
      update.mutate({ id: destination.id, input }, { onSuccess: saved });
      return;
    }
    create.mutate({ ...input, webhookUrl: trimmedUrl }, { onSuccess: saved });
  }

  return (
    <div className="flex flex-col gap-4">
      <TextInput
        label="Name"
        value={name}
        onChange={setName}
        isRequired
        placeholder="#platform-guild"
      />
      <SegmentedControl
        label="Chat tool"
        value={platform}
        options={CHAT_PLATFORM_OPTIONS}
        onChange={(next) => setPlatform(next as ChatPlatform)}
        // The group is `inline-flex`, but as a flex item in this column it
        // stretches anyway, trailing its sunken track past the last pill.
        className="items-start"
      />
      <TextInput
        label="Webhook URL"
        type="url"
        value={url}
        onChange={setUrl}
        isRequired={!isEdit}
        placeholder={placeholder}
        description={description}
      />
      {!isEdit && <ConsentNotice />}
      {mutation.isError && (
        <FormError
          error={mutation.error}
          message={chatDestinationErrorMessage(mutation.error, platform)}
        />
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={close}>
          Cancel
        </Button>
        <Button onPress={submit} isDisabled={!canSubmit}>
          {isEdit ? "Save" : "Add"}
        </Button>
      </DialogFooter>
    </div>
  );
}

interface DestinationDialogProps {
  slug: string;
  destination: ChatDestination | null;
  isOpen: boolean;
  /**
   * Bumped by the caller on every explicit open, never on close. `destination`
   * alone can't drive the form's remount key: the caller deliberately keeps
   * the last-opened `destination` in place while `isOpen` goes false, so the
   * title and the URL field's placeholder don't flip to the Add dialog's
   * copy during the exit animation (`ModalOverlay` keeps its children mounted
   * while `data-[exiting]` plays). `isOpen` itself can't be in the key either,
   * for the same reason - flipping it would remount mid-fade. `resetKey`
   * changes only on open, so a fresh mount still starts from the right values
   * every time, including reopening the very same destination.
   */
  resetKey: number;
  onOpenChange: (open: boolean) => void;
  /** Called once a create or update actually succeeds - see `DestinationForm`'s own prop. */
  onSaved: () => void;
}

export function DestinationDialog({
  slug,
  destination,
  isOpen,
  resetKey,
  onOpenChange,
  onSaved,
}: Readonly<DestinationDialogProps>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title={destination !== null ? "Edit destination" : "Add destination"}
    >
      {({ close }) => (
        <DestinationForm
          key={`${destination?.id ?? "add"}-${resetKey}`}
          slug={slug}
          destination={destination}
          close={close}
          onSaved={onSaved}
        />
      )}
    </Dialog>
  );
}
