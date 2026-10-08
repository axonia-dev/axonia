import type { SyntheticEvent } from "react";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useEditArtifact } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactFormFields } from "@/features/knowledge/ui/ArtifactFormFields";

interface Props {
  artifact: Artifact;
  slug: string;
  onDone: () => void;
}

const URL_PATTERN = /^https?:\/\/.+/;

export function EditArtifactForm({ artifact, slug, onDone }: Readonly<Props>) {
  const [title, setTitle] = useState(artifact.title);
  const [typeId, setTypeId] = useState<number | null>(artifact.typeId);
  const [url, setUrl] = useState(artifact.url);
  const [description, setDescription] = useState(artifact.description);
  const [tags, setTags] = useState<string[]>(artifact.tags);

  const edit = useEditArtifact(slug);

  const urlValid = URL_PATTERN.test(url.trim());
  const busy = edit.isPending;
  const saveDisabled =
    busy || title.trim() === "" || typeId === null || !urlValid;
  const error = edit.error;

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (typeId === null || !urlValid) return;
    edit.mutate(
      {
        id: artifact.id,
        input: {
          title: title.trim(),
          typeId,
          url: url.trim(),
          description: description.trim(),
          tags,
        },
      },
      { onSuccess: onDone },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
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

      <DialogFooter>
        <Button
          type="button"
          variant="ghost"
          isDisabled={busy}
          onPress={onDone}
        >
          Cancel
        </Button>
        <Button type="submit" isDisabled={saveDisabled}>
          Save
        </Button>
      </DialogFooter>

      {error != null && (
        <p role="alert" className="text-danger text-sm">
          Something went wrong. Please try again.
        </p>
      )}
    </form>
  );
}
