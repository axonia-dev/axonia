import { Download } from "lucide-react";

import { PageHeader } from "@/components/patterns/PageHeader";
import { Button } from "@/components/ui/Button";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import {
  classifyDownloadError,
  downloadExportErrorMessage,
  useDownloadExport,
} from "@/features/privacy/api/use-download-export";

const ORGANIZATION_EXPORT_PATH = "/axonia/admin/data-export";

function DataExportContent() {
  const exportDownload = useDownloadExport(ORGANIZATION_EXPORT_PATH);
  const isPreparing = exportDownload.isPending;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Data export" />
      <div className="flex flex-col gap-4">
        <p className="text-muted-foreground text-sm">
          This package contains every row of personal data this organization
          holds about its members, across memberships, sessions, topics,
          artifacts, recognition and expertise, packaged as a single zip file.
          It is a record of people, not a backup of the organization: reference
          data that names nobody, such as the community and artifact-type
          catalogues, is not included, so a row referring to a community shows
          its id rather than its name.
        </p>
        <p className="text-muted-foreground text-sm">
          It does not include anything outside this organization, nor anything
          already erased under an account deletion. A member’s own “Download my
          data”, from their account menu, is scoped narrower still: it carries
          only the rows where they are the actor or subject, never rows where
          they are merely referenced by someone else, since those are other
          people’s data.
        </p>
        {exportDownload.isError && (
          <p role="alert" className="text-danger text-sm">
            {downloadExportErrorMessage(
              classifyDownloadError(exportDownload.error),
            )}
          </p>
        )}
        <div>
          <Button
            onPress={() => exportDownload.mutate()}
            isDisabled={isPreparing}
          >
            <Download size={16} aria-hidden />
            {isPreparing ? "Preparing…" : "Download"}
          </Button>
        </div>
      </div>
    </div>
  );
}

export function DataExportPage() {
  return (
    <RequirePermission permission="organization:export" title="Data export">
      <DataExportContent />
    </RequirePermission>
  );
}
