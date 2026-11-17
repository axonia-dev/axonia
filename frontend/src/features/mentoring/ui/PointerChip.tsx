import { X } from "lucide-react";
import { type SyntheticEvent, useState } from "react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { TextInput } from "@/components/ui/TextInput";
import type { Pointer } from "@/features/mentoring/domain/mentoring";
import { pointerErrorMessage } from "@/features/mentoring/mentoring-errors";
import { useSwapFocus } from "@/features/mentoring/ui/use-swap-focus";

const MAX_URL_LENGTH = 2000;

interface ChipProps {
  pointer: Pointer;
  onRemove?: (() => void) | undefined;
}

const chipClass =
  "bg-surface border-border text-foreground hover:border-border-strong inline-flex max-w-full items-center gap-1 rounded-sm border px-2 py-0.5 text-xs";

function Target({ pointer }: Readonly<{ pointer: Pointer }>) {
  const { href, title } = pointer;
  if (!pointer.available || href === null) {
    return <span className="text-muted-foreground italic">Unavailable</span>;
  }
  const label = title ?? href;
  if (pointer.kind === "LINK") {
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer noopener"
        className="text-secondary wrap-anywhere hover:underline"
      >
        {label}
      </a>
    );
  }
  return (
    <Link to={href} className="text-secondary wrap-anywhere hover:underline">
      {label}
    </Link>
  );
}

/** A link held by a topic or a note: an outside address as typed, anything else with its current title or Unavailable. */
function PointerChip({ pointer, onRemove }: Readonly<ChipProps>) {
  return (
    <span className={chipClass}>
      <Target pointer={pointer} />
      {onRemove !== undefined && (
        <IconButton
          label={`Remove link ${pointer.title ?? pointer.href ?? ""}`.trim()}
          onPress={onRemove}
        >
          <X aria-hidden />
        </IconButton>
      )}
    </span>
  );
}

interface FieldProps {
  subject: string;
  isPending: boolean;
  /** Stores the address; `done` closes the field, `fail` says why the address was refused. */
  onAdd: (
    url: string,
    done: () => void,
    fail: (error: unknown) => void,
  ) => void;
}

function AddressForm({
  subject,
  isPending,
  onAdd,
  onClose,
}: Readonly<FieldProps & { onClose: () => void }>) {
  const [url, setUrl] = useState("");
  const [error, setError] = useState<string | undefined>(undefined);
  const trimmed = url.trim();

  function submit(event: SyntheticEvent) {
    event.preventDefault();
    if (trimmed === "" || isPending) return;
    onAdd(trimmed, onClose, (refusal) =>
      setError(pointerErrorMessage(refusal)),
    );
  }

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-2">
      <TextInput
        label={`Address to link from ${subject}`}
        type="url"
        value={url}
        onChange={(next) => {
          setUrl(next);
          setError(undefined);
        }}
        maxLength={MAX_URL_LENGTH}
        placeholder="Paste an address"
        {...(error === undefined ? {} : { errorMessage: error })}
      />
      <div className="flex gap-2">
        <Button
          type="submit"
          variant="secondary"
          size="sm"
          isDisabled={trimmed === "" || isPending}
        >
          Add link
        </Button>
        <Button variant="ghost" size="sm" onPress={onClose}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

interface PointersProps extends FieldProps {
  pointers: Pointer[];
  writable: boolean;
  onRemove: (pointerId: number) => void;
}

/** A topic's or a note's links, then "Add link", which opens the address on its own row so it never squeezes beside a chip. */
export function Pointers({
  pointers,
  writable,
  onRemove,
  ...field
}: Readonly<PointersProps>) {
  const [open, setOpen] = useState(false);
  const { box, swap } = useSwapFocus<HTMLDivElement>(open);
  if (pointers.length === 0 && !writable) return null;

  function toggle(next: boolean) {
    swap();
    setOpen(next);
  }

  return (
    <div ref={box} className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-1">
        {pointers.map((pointer) => (
          <PointerChip
            key={pointer.id}
            pointer={pointer}
            onRemove={writable ? () => onRemove(pointer.id) : undefined}
          />
        ))}
        {writable && !open && (
          <Button
            variant="ghost"
            size="sm"
            data-swap-trigger
            {...(pointers.length === 0 ? { className: "-ml-3" } : {})}
            aria-label={`Add link to ${field.subject}`}
            onPress={() => toggle(true)}
          >
            Add link
          </Button>
        )}
      </div>
      {writable && open && (
        <AddressForm {...field} onClose={() => toggle(false)} />
      )}
    </div>
  );
}
