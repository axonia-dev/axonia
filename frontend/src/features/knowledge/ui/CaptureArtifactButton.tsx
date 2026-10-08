import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { CaptureArtifactForm } from "@/features/knowledge/ui/CaptureArtifactForm";

interface Props {
  slug: string;
}

/**
 * Opens the shared modal around the standalone capture form (no provenance).
 * The capture hook already invalidates the community's artifact, session, and
 * stats queries on success; here we just close once it lands.
 */
export function CaptureArtifactButton({ slug }: Readonly<Props>) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button variant="primary" onPress={() => setOpen(true)}>
        Capture artifact
      </Button>

      <Dialog isOpen={open} onOpenChange={setOpen} title="Capture an artifact">
        <CaptureArtifactForm slug={slug} onCaptured={() => setOpen(false)} />
      </Dialog>
    </>
  );
}
