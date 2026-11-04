import { Pencil } from "lucide-react";
import type { KeyboardEvent, ReactNode, Ref } from "react";
import { useEffect, useId, useRef, useState } from "react";

import { MorphPart } from "@/components/patterns/morph/MorphPart";
import { useRenameSession } from "@/features/events/api/use-sessions";
import type { Session } from "@/features/events/domain/session";
import { eventsErrorMessage } from "@/features/events/events-errors";

/** The column's own bound, so the field stops where the server would refuse. */
const MAX_TITLE_LENGTH = 255;

interface Props {
  slug: string;
  session: Session;
  canRename: boolean;
  morphId: string | undefined;
}

const headingClass =
  "text-foreground font-display min-w-0 text-xl font-bold break-words max-sm:basis-full sm:flex-1";

function Heading({
  title,
  morphId,
  children,
}: Readonly<{
  title: string;
  morphId: string | undefined;
  children?: ReactNode;
}>) {
  const text = (
    <MorphPart part="title" id={morphId}>
      <span className="inline-block max-w-full align-top sm:truncate">
        {title}
      </span>
    </MorphPart>
  );
  return <h2 className={headingClass}>{children ?? text}</h2>;
}

/**
 * The session's name as its page heading. Whoever may manage the schedule renames it in place:
 * the heading opens into a field on click, Enter or leaving the field saves, Escape drops the
 * edit. An emptied field clears the session's own title, so it goes back to the automatic name
 * the placeholder shows.
 */
export function SessionTitleEditor({
  slug,
  session,
  canRename,
  morphId,
}: Readonly<Props>) {
  if (!canRename) {
    return <Heading title={session.displayTitle} morphId={morphId} />;
  }
  return <EditableTitle slug={slug} session={session} morphId={morphId} />;
}

/** The name to show: the one being saved while a save is in flight, so the heading never flashes
 *  back to the old name between Enter and the server's answer. */
function shownTitle(
  session: Session,
  rename: ReturnType<typeof useRenameSession>,
): string {
  if (!rename.isPending) return session.displayTitle;
  return rename.variables ?? session.automaticTitle;
}

function EditableTitle({
  slug,
  session,
  morphId,
}: Readonly<Omit<Props, "canRename">>) {
  const rename = useRenameSession(slug, session.id);
  const [editing, setEditing] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  const refocus = useRef(false);
  const hintId = useId();
  const title = shownTitle(session, rename);

  useEffect(() => {
    if (!editing && refocus.current) {
      refocus.current = false;
      button.current?.focus();
    }
  }, [editing]);

  const close = (returnFocus: boolean) => {
    refocus.current = returnFocus;
    setEditing(false);
  };
  const save = (draft: string, returnFocus: boolean) => {
    const next = draft.trim();
    if (next !== (session.title ?? ""))
      rename.mutate(next === "" ? null : next);
    close(returnFocus);
  };

  return (
    <>
      <Heading title={title} morphId={morphId}>
        {editing ? (
          <TitleField
            initial={session.title ?? ""}
            placeholder={session.automaticTitle}
            onSave={save}
            onCancel={() => close(true)}
          />
        ) : (
          <RenameButton
            ref={button}
            title={title}
            morphId={morphId}
            hintId={hintId}
            onPress={() => {
              rename.reset();
              setEditing(true);
            }}
          />
        )}
      </Heading>
      <span id={hintId} hidden>
        Rename
      </span>
      {rename.error != null && (
        <p role="alert" className="text-danger basis-full text-sm">
          {eventsErrorMessage(rename.error)}
        </p>
      )}
    </>
  );
}

interface ButtonProps {
  ref: Ref<HTMLButtonElement>;
  title: string;
  morphId: string | undefined;
  hintId: string;
  onPress: () => void;
}

/**
 * The heading's own text, pressable. Its name stays the title alone, so the heading reads the
 * same to assistive tech as it does for a reader who cannot rename; the hint rides as a
 * description. The pencil shows on hover and focus, and always where nothing hovers.
 */
function RenameButton({
  ref,
  title,
  morphId,
  hintId,
  onPress,
}: Readonly<ButtonProps>) {
  return (
    <button
      ref={ref}
      type="button"
      aria-describedby={hintId}
      onClick={onPress}
      className="group hover:bg-surface-sunken focus-visible:ring-focus-ring -ml-1.5 inline-flex max-w-[calc(100%+0.375rem)] cursor-text items-center gap-2 rounded-md px-1.5 text-left outline-none focus-visible:ring-2"
    >
      <MorphPart part="title" id={morphId}>
        <span className="inline-block max-w-full min-w-0 align-top sm:truncate">
          {title}
        </span>
      </MorphPart>
      <Pencil
        aria-hidden="true"
        className="text-muted-foreground size-4 shrink-0 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 [@media(hover:none)]:opacity-100"
      />
    </button>
  );
}

interface FieldProps {
  initial: string;
  placeholder: string;
  onSave: (draft: string, returnFocus: boolean) => void;
  onCancel: () => void;
}

function TitleField({
  initial,
  placeholder,
  onSave,
  onCancel,
}: Readonly<FieldProps>) {
  const [draft, setDraft] = useState(initial);
  const input = useRef<HTMLInputElement>(null);
  const settled = useRef(false);

  useEffect(() => {
    input.current?.focus();
    input.current?.select();
  }, []);

  const settle = (then: () => void) => {
    if (settled.current) return;
    settled.current = true;
    then();
  };
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.preventDefault();
      settle(() => onSave(draft, true));
    } else if (event.key === "Escape") {
      event.preventDefault();
      settle(onCancel);
    }
  };

  return (
    <input
      ref={input}
      type="text"
      aria-label="Session title"
      value={draft}
      placeholder={placeholder}
      maxLength={MAX_TITLE_LENGTH}
      onChange={(event) => setDraft(event.target.value)}
      onKeyDown={onKeyDown}
      onBlur={() => settle(() => onSave(draft, false))}
      className="border-border bg-surface text-foreground placeholder:text-muted-foreground focus-visible:ring-focus-ring -my-px -ml-1.5 w-[calc(100%+0.375rem)] rounded-md border px-1.5 py-0 outline-none focus-visible:ring-2"
    />
  );
}
