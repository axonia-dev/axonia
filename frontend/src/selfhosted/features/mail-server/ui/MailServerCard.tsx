import { useEffect, useId, useRef, useState } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { failedToLoad } from "@/lib/failed-to-load";
import { useMailServer } from "@/selfhosted/features/mail-server/api/use-mail-server";
import { MailNotConfiguredNotice } from "@/selfhosted/features/mail-server/ui/MailNotConfiguredNotice";
import { MailServerAtRest } from "@/selfhosted/features/mail-server/ui/MailServerAtRest";
import { MailServerForm } from "@/selfhosted/features/mail-server/ui/MailServerForm";
import {
  MailServerHeader,
  MailServerTitle,
} from "@/selfhosted/features/mail-server/ui/MailServerHeader";

const CARD =
  "border-border bg-surface flex flex-col gap-3 rounded-md border p-4";

/** The form opens and closes in place, so on closing focus returns to the action that opened it,
 *  Edit or Set up, rather than falling to the page. */
function useEditing() {
  const section = useRef<HTMLElement>(null);
  const returning = useRef(false);
  const [editing, setEditing] = useState(false);
  useEffect(() => {
    if (editing || !returning.current) return;
    returning.current = false;
    section.current
      ?.querySelector<HTMLElement>("[data-card-action] button")
      ?.focus();
  }, [editing]);
  return {
    section,
    editing,
    open: () => setEditing(true),
    close: () => {
      returning.current = true;
      setEditing(false);
    },
  };
}

type BodyProps = Readonly<{
  titleId: string;
  view: ReturnType<typeof useMailServer>;
  canWrite: boolean;
  editing: boolean;
  onOpen: () => void;
  onClose: () => void;
}>;

function PanelBody({
  titleId,
  view,
  canWrite,
  editing,
  onOpen,
  onClose,
}: BodyProps) {
  if (failedToLoad(view)) {
    return (
      <>
        <MailServerTitle id={titleId} />
        <LoadFailed
          inline
          message="Could not load the mail server."
          query={view}
        />
      </>
    );
  }
  if (view.data === undefined) {
    return (
      <>
        <MailServerTitle id={titleId} />
        <p className="text-muted-foreground text-sm">Loading…</p>
      </>
    );
  }
  return editing ? (
    <div className="flex flex-col gap-5">
      <MailServerHeader titleId={titleId} state={view.data.state} />
      <MailServerForm view={view.data} onClose={onClose} />
    </div>
  ) : (
    <MailServerAtRest
      titleId={titleId}
      view={view.data}
      canWrite={canWrite}
      onEdit={onOpen}
    />
  );
}

/** Drawn from the first render, so the cards below it do not jump once the mail server is read. */
function MailServerPanel({ canWrite }: Readonly<{ canWrite: boolean }>) {
  const titleId = useId();
  const view = useMailServer();
  const { section, editing, open, close } = useEditing();
  return (
    <section
      ref={section}
      aria-labelledby={titleId}
      aria-busy={view.data === undefined && !failedToLoad(view)}
      className={CARD}
    >
      <PanelBody
        titleId={titleId}
        view={view}
        canWrite={canWrite}
        editing={editing}
        onOpen={open}
        onClose={close}
      />
    </section>
  );
}

/**
 * The installation's mail server, first on the organization Notifications page. A holder of
 * `mail_server:read` sees the card; anyone else the page admits sees only whether mail is off.
 * Nothing until the permissions are known, so a reader never sees the notice flash first.
 */
export function MailServerCard() {
  const { can, isLoading } = usePermissions();
  if (isLoading) return null;
  if (!can("mail_server:read")) return <MailNotConfiguredNotice />;
  return <MailServerPanel canWrite={can("mail_server:write")} />;
}
