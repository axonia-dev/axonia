import { Dialog } from "@/components/ui/Dialog";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { EditArtifactForm } from "@/features/knowledge/ui/EditArtifactForm";

interface Props {
  artifact: Artifact;
  slug: string;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ArtifactDialog({
  artifact,
  slug,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title="Edit artifact">
      {({ close }) => (
        <EditArtifactForm artifact={artifact} slug={slug} onDone={close} />
      )}
    </Dialog>
  );
}
