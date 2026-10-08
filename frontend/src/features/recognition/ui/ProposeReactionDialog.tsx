import { ImagePlus } from "lucide-react";
import { useState } from "react";

import { Button, buttonClass } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { ACCEPTED_IMAGE_TYPES } from "@/components/ui/image-drop";
import { TextInput } from "@/components/ui/TextInput";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useProposeReaction } from "@/features/recognition/api/use-reactions";
import { MyProposals } from "@/features/recognition/ui/MyProposals";
import { serverMessage } from "@/lib/server-message";
import { useObjectUrl } from "@/lib/use-object-url";

const MIN_NAME_LENGTH = 2;
const MAX_NAME_LENGTH = 32;
const MAX_IMAGE_BYTES = 128 * 1024;

const NAME_HELP =
  "2 to 32 characters. This is the word members read when they pick it.";
const IMAGE_HELP = "128 KB maximum. PNG, JPEG or WebP.";
const CHOOSE_LABEL = "Choose an image";
const IMAGE_INPUT_ID = "propose-reaction-image";
const SIZE_ERROR = "Image is over the 128 KB limit.";
const TYPE_ERROR = "Unsupported image type. Use PNG, JPEG or WebP.";
const PROPOSE_ERROR = "Could not propose the reaction. Try again.";

/** Reactions are open to enterprise organizations as well as hubs, so the word that stands in
 *  for a missing name has to fit both. */
function bodyCopy(organizationName: string | undefined): string {
  const where = organizationName ?? "the organization";
  return `Added to ${where} once an organizer approves it. Everyone in the organization can use it from then on.`;
}

function isValidName(name: string): boolean {
  return name.length >= MIN_NAME_LENGTH && name.length <= MAX_NAME_LENGTH;
}

export function ProposeReactionDialog({
  isOpen,
  onOpenChange,
}: Readonly<{
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}>) {
  const organization = useCurrentOrganization();

  return (
    <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title="Add a reaction">
      {({ close }) => (
        <ProposeReactionForm
          organizationName={organization.data?.name}
          close={close}
        />
      )}
    </Dialog>
  );
}

function ImagePreviewChip({
  previewUrl,
  name,
}: Readonly<{ previewUrl: string; name: string }>) {
  const label = name.trim() === "" ? "New reaction" : name;
  return (
    <span className="border-border inline-flex w-fit items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs">
      <img
        src={previewUrl}
        alt="Preview"
        width={17}
        height={17}
        className="size-[17px] rounded-full object-cover"
      />
      <span>{label}</span>
    </span>
  );
}

interface ProposeReactionFormProps {
  organizationName: string | undefined;
  close: () => void;
}

function ProposeReactionForm({
  organizationName,
  close,
}: Readonly<ProposeReactionFormProps>) {
  const proposeReaction = useProposeReaction();
  const [name, setName] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const previewUrl = useObjectUrl(file);

  function handleSelect(chosen: File) {
    if (!ACCEPTED_IMAGE_TYPES.includes(chosen.type)) {
      setFile(null);
      setError(TYPE_ERROR);
      return;
    }
    if (chosen.size > MAX_IMAGE_BYTES) {
      setFile(null);
      setError(SIZE_ERROR);
      return;
    }
    setError(null);
    setFile(chosen);
  }

  function handlePropose() {
    if (file === null || !isValidName(name)) return;
    setError(null);
    proposeReaction.mutate(
      { name, file },
      {
        onSuccess: close,
        onError: (mutationError) =>
          setError(serverMessage(mutationError) ?? PROPOSE_ERROR),
      },
    );
  }

  const canPropose =
    isValidName(name) && file !== null && !proposeReaction.isPending;

  return (
    <div className="flex flex-col gap-4">
      {/* Above the form, because a member who has filled the open-proposal cap comes here to be
       *  refused and needs the Withdraw in front of them, not below the field that refuses. */}
      <MyProposals />

      <p className="text-muted-foreground text-sm">
        {bodyCopy(organizationName)}
      </p>

      <TextInput
        label="Name"
        value={name}
        onChange={setName}
        description={NAME_HELP}
        maxLength={MAX_NAME_LENGTH}
      />

      <div className="flex flex-col gap-1">
        <span className="text-foreground text-sm font-medium">Image</span>
        {/* A native input, not `FileTrigger`: `FileTrigger` deliberately strips `aria-label`
         *  from the hidden input it renders (`filterDOMProps(rest, { global: true })` omits
         *  the labelable set), so it can never be named for assistive tech through that prop.
         *  `aria-label` on this input names it directly; the `label` below only forwards the
         *  click, styled to look like the panel a `FileTrigger` button would be. */}
        <label
          htmlFor={IMAGE_INPUT_ID}
          className={buttonClass("outline", "md", "w-full justify-start gap-3")}
        >
          {previewUrl === null ? (
            <>
              <ImagePlus aria-hidden size={18} />
              <span className="text-muted-foreground text-sm">
                {CHOOSE_LABEL}
              </span>
            </>
          ) : (
            <ImagePreviewChip previewUrl={previewUrl} name={name} />
          )}
        </label>
        <input
          id={IMAGE_INPUT_ID}
          type="file"
          aria-label="Image"
          accept={ACCEPTED_IMAGE_TYPES.join(",")}
          className="sr-only"
          onChange={(event) => {
            const chosen = event.target.files?.[0];
            if (chosen) {
              handleSelect(chosen);
            }
            event.target.value = "";
          }}
        />
        <span className="text-muted-foreground text-xs">{IMAGE_HELP}</span>
      </div>

      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}

      <DialogFooter>
        <Button variant="ghost" onPress={close}>
          Cancel
        </Button>
        <Button onPress={handlePropose} isDisabled={!canPropose}>
          Propose
        </Button>
      </DialogFooter>
    </div>
  );
}
