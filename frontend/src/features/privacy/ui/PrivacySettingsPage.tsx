import { Download, Trash2 } from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";

import { useAuth } from "@/auth/session";
import { PageHeader } from "@/components/patterns/PageHeader";
import { Button } from "@/components/ui/Button";
import { notifyError } from "@/components/ui/toast";
import { useEdition } from "@/edition-context";
import { useMe } from "@/features/identity/api/use-me";
import { resolveAccountEmail } from "@/features/identity/ui/account-identity";
import {
  classifyDownloadError,
  downloadExportErrorMessage,
  useDownloadExport,
} from "@/features/privacy/api/use-download-export";
import { DeleteAccountDialog } from "@/features/privacy/ui/DeleteAccountDialog";

const OWN_EXPORT_PATH = "/axonia/me/data-export";

interface CardProps {
  title: string;
  body?: string;
  children: ReactNode;
}

const MEMBER_DOWNLOAD =
  "A machine-readable copy of everything recorded against this account: the profile, the contributions and the activity.";

const PERSONAL_DOWNLOAD =
  "A machine-readable copy of what is held for this account outside any community.";

const DELETE =
  "Permanently erases this account and anonymizes everything contributed under it. This cannot be undone, and nothing can be restored afterwards.";

function Card({ title, body, children }: Readonly<CardProps>) {
  return (
    <section className="border-border bg-surface flex flex-col gap-3 rounded-md border p-5">
      <h2 className="text-foreground font-display text-base font-bold">
        {title}
      </h2>
      {body && <p className="text-muted-foreground text-sm">{body}</p>}
      <div className="flex">{children}</div>
    </section>
  );
}

/**
 * The two data rights, and the edition's privacy notice when it has one, on a page rather than
 * in a menu. Deletion is irreversible and Log out is not, so putting them side by side in a menu
 * was always the wrong shape: here the consequence can be stated before the button is reached.
 *
 * Only ever routed for a hub session or a person with no organization (`personal`), never
 * delegated. In an enterprise session the organization is the controller of the data, and
 * the backend refuses both endpoints - that gating lives upstream of this page, not inside it.
 */
export function PrivacySettingsPage({
  personal = false,
}: Readonly<{ personal?: boolean }>) {
  const { PrivacyNotice } = useEdition();
  const auth = useAuth();
  const me = useMe();
  const downloadExport = useDownloadExport(OWN_EXPORT_PATH);
  const [deleting, setDeleting] = useState(false);

  function downloadOwnData() {
    downloadExport.mutate(undefined, {
      onError: (error: unknown) =>
        notifyError(downloadExportErrorMessage(classifyDownloadError(error))),
    });
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Privacy" />
      <Card
        title="Download my data"
        body={personal ? PERSONAL_DOWNLOAD : MEMBER_DOWNLOAD}
      >
        <Button
          variant="secondary"
          onPress={downloadOwnData}
          isDisabled={downloadExport.isPending}
        >
          <Download size={16} aria-hidden />
          {downloadExport.isPending ? "Preparing…" : "Download my data"}
        </Button>
      </Card>
      <Card title="Delete my account" body={DELETE}>
        <Button variant="danger" onPress={() => setDeleting(true)}>
          <Trash2 size={16} aria-hidden />
          Delete my account
        </Button>
      </Card>
      {PrivacyNotice !== null && (
        <Card title="Privacy notice">
          <PrivacyNotice />
        </Card>
      )}
      <DeleteAccountDialog
        email={resolveAccountEmail(me.data, auth.user?.profile)}
        isOpen={deleting}
        onOpenChange={setDeleting}
      />
    </div>
  );
}
