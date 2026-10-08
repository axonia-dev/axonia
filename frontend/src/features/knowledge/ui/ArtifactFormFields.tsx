import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Select } from "@/components/ui/Select";
import { TagInput } from "@/components/ui/TagInput";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import { failedToLoad, stillLoading } from "@/lib/failed-to-load";
import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

function typeOptionsOf(types: ReturnType<typeof useArtifactTypes>) {
  return (types.data ?? []).map((type) => ({
    id: type.id,
    label: type.label,
    icon: (
      <TypeIcon
        typeId={type.id}
        label={type.label}
        icon={type.icon}
        accentColor={type.accentColor}
        size={15}
        className="p-1"
      />
    ),
  }));
}

interface ArtifactFormFieldsProps {
  title: string;
  onTitleChange: (v: string) => void;
  typeId: number | null;
  onTypeChange: (v: number | null) => void;
  url: string;
  onUrlChange: (v: string) => void;
  urlValid: boolean;
  description: string;
  onDescriptionChange: (v: string) => void;
  tags: string[];
  onTagsChange: (v: string[]) => void;
}

export function ArtifactFormFields({
  title,
  onTitleChange,
  typeId,
  onTypeChange,
  url,
  onUrlChange,
  urlValid,
  description,
  onDescriptionChange,
  tags,
  onTagsChange,
}: Readonly<ArtifactFormFieldsProps>) {
  const types = useArtifactTypes();
  const typesFailed = failedToLoad(types);
  const typesRecovered = useAnsweredAfterFailing(typesFailed);
  return (
    <>
      <TextInput
        label="Title"
        value={title}
        onChange={onTitleChange}
        isRequired
      />

      {typesFailed ? (
        <LoadFailed
          inline
          message="Could not load the artifact types."
          query={types}
        />
      ) : (
        <Select
          label="Type"
          value={typeId}
          options={typeOptionsOf(types)}
          onChange={onTypeChange}
          placeholder="Choose a type"
          isLoading={stillLoading(types)}
          takesFocus={typesRecovered}
        />
      )}

      <TextInput
        label="Link"
        value={url}
        onChange={onUrlChange}
        type="url"
        isRequired
        placeholder="https://…"
        {...(url.trim() !== "" && !urlValid
          ? { errorMessage: "Enter an http(s) link." }
          : {})}
      />

      <TextArea
        label="Description"
        value={description}
        onChange={onDescriptionChange}
      />

      <TagInput
        label="Tags"
        value={tags}
        onChange={onTagsChange}
        placeholder="e.g. typescript"
      />
    </>
  );
}
