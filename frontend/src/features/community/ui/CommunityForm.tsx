import type { SyntheticEvent } from "react";
import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useCreateCommunity,
  useUpdateCommunity,
} from "@/features/community/api/use-communities";
import { useCommunityKinds } from "@/features/community/api/use-community-kinds";
import {
  type Community,
  HUB_COMMUNITY_NAME_MAX_LENGTH,
} from "@/features/community/domain/community";
import { failedToLoad, stillLoading } from "@/lib/failed-to-load";
import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

interface Props {
  community?: Community;
  onCreated: (slug: string) => void;
}

const blank = { name: "", kindId: null, description: "", slug: "" };

export function CommunityForm({ community, onCreated }: Readonly<Props>) {
  const initial = community ?? blank;
  const [name, setName] = useState(initial.name);
  const [kindId, setKindId] = useState<number | null>(initial.kindId);
  const [description, setDescription] = useState(initial.description);

  const kinds = useCommunityKinds();
  const kindsFailed = failedToLoad(kinds);
  const kindsRecovered = useAnsweredAfterFailing(kindsFailed);
  const create = useCreateCommunity();
  const update = useUpdateCommunity(initial.slug);
  const { data: organization } = useCurrentOrganization();

  const editing = Boolean(community);
  const disabled = name.trim() === "" || kindId === null;
  const hasError = create.isError || update.isError;
  const kindOptions = (kinds.data ?? []).map((kind) => ({
    id: kind.id,
    label: kind.label,
  }));

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (kindId === null) {
      return;
    }
    const input = { name, kindId, description };
    const onSuccess = (saved: Community) => onCreated(saved.slug);
    const mutation = editing ? update : create;
    mutation.mutate(input, { onSuccess });
  }

  // In a hub the community's name IS the hub's public name: same cap as creation.
  const isHub = organization?.kind === "HUB";

  return (
    <form onSubmit={handleSubmit} className="flex max-w-md flex-col gap-4">
      <TextInput
        label="Name"
        value={name}
        onChange={setName}
        isRequired
        {...(isHub ? { maxLength: HUB_COMMUNITY_NAME_MAX_LENGTH } : {})}
      />

      {kindsFailed ? (
        <LoadFailed
          inline
          message="Could not load the community kinds."
          query={kinds}
        />
      ) : (
        <Select
          label="Kind"
          value={kindId}
          options={kindOptions}
          onChange={setKindId}
          placeholder="Choose a kind"
          isLoading={stillLoading(kinds)}
          takesFocus={kindsRecovered}
        />
      )}

      <TextArea
        label="Description"
        value={description}
        onChange={setDescription}
      />

      <Button type="submit" isDisabled={disabled}>
        {editing ? "Save" : "Create"}
      </Button>

      <FormError error={hasError} />
    </form>
  );
}
