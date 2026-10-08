import type { SyntheticEvent } from "react";
import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { useCaptureArtifact } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import type {
  CaptureArtifactInput,
  ProvenanceInput,
} from "@/features/knowledge/domain/knowledge-gateway";
import {
  captureErrorMessage,
  creditErrorMessage,
} from "@/features/knowledge/knowledge-errors";
import { ArtifactFormFields } from "@/features/knowledge/ui/ArtifactFormFields";
import {
  ContributorField,
  useCredit,
} from "@/features/knowledge/ui/ContributorField";

interface Props {
  slug: string;
  defaultTitle?: string;
  defaultUrl?: string;
  defaultDescription?: string;
  provenance?: ProvenanceInput;
  /** Presenters of the slot this resource is captured against, if any; whether the caller may
   *  credit one of them is the community's Credit contributions axis, read by this form. */
  presenterUserIds?: string[];
  submitLabel?: string;
  onBack?: () => void;
  onCaptured: (artifact: Artifact) => void;
}

const URL_PATTERN = /^https?:\/\/.+/;

function BackButton({
  onBack,
}: Readonly<{ onBack: (() => void) | undefined }>) {
  if (!onBack) {
    return null;
  }
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className="hover:text-primary-strong self-start px-0 hover:bg-transparent"
      onPress={onBack}
    >
      ← Back to search
    </Button>
  );
}

/** Crediting is to blame for a refusal only when the capture credited somebody else. */
function refusalMessage(
  error: unknown,
  input: CaptureArtifactInput | undefined,
): string {
  return input?.contributor === undefined
    ? captureErrorMessage(error)
    : creditErrorMessage(error);
}

export function CaptureArtifactForm({
  slug,
  defaultTitle = "",
  defaultUrl = "",
  defaultDescription = "",
  provenance,
  presenterUserIds = [],
  submitLabel = "Save",
  onBack,
  onCaptured,
}: Readonly<Props>) {
  const [title, setTitle] = useState(defaultTitle);
  const [typeId, setTypeId] = useState<number | null>(null);
  const [url, setUrl] = useState(defaultUrl);
  const [description, setDescription] = useState(defaultDescription);
  const [tags, setTags] = useState<string[]>([]);
  const access = useManagementAccess(slug);
  const credit = useCredit(presenterUserIds, access.credit);

  const capture = useCaptureArtifact(slug);

  const urlValid = URL_PATTERN.test(url.trim());
  const disabled =
    title.trim() === "" ||
    typeId === null ||
    !urlValid ||
    credit.mustChoose ||
    access.isLoading;

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (typeId === null || !urlValid || credit.mustChoose || access.isLoading) {
      return;
    }
    const input: CaptureArtifactInput = {
      title: title.trim(),
      typeId,
      url: url.trim(),
      description: description.trim(),
      tags,
      ...(provenance ? { provenance } : {}),
      ...credit.credited,
    };
    capture.mutate(input, { onSuccess: onCaptured });
  }

  return (
    <form onSubmit={handleSubmit} className="flex max-w-md flex-col gap-4">
      <BackButton onBack={onBack} />
      <ArtifactFormFields
        title={title}
        onTitleChange={setTitle}
        typeId={typeId}
        onTypeChange={setTypeId}
        url={url}
        onUrlChange={setUrl}
        urlValid={urlValid}
        description={description}
        onDescriptionChange={setDescription}
        tags={tags}
        onTagsChange={setTags}
      />
      <ContributorField
        slug={slug}
        presenterUserIds={presenterUserIds}
        credit={credit}
      />
      <DialogFooter>
        <Button type="submit" isDisabled={disabled}>
          {submitLabel}
        </Button>
      </DialogFooter>

      <FormError
        error={capture.isError}
        message={refusalMessage(capture.error, capture.variables)}
      />
    </form>
  );
}
