import { Plus } from "lucide-react";
import { useState } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Button } from "@/components/ui/Button";
import { ComboBox, type ComboBoxOption } from "@/components/ui/ComboBox";
import { Select } from "@/components/ui/Select";
import { useBindGroup } from "@/features/admin/api/use-bind-group";
import type { useRoles } from "@/features/admin/api/use-roles";
import type { GroupSource } from "@/features/admin/domain/admin-user";
import { SOURCE_ICON } from "@/features/admin/ui/group-source-icon";
import { SOURCE_LABEL } from "@/features/admin/ui/group-source-label";
import { roleOptions } from "@/features/admin/ui/role-options";
import { useCeiling } from "@/features/admin/ui/use-ceiling";
import { ApiError } from "@/lib/api-client";
import { failedToLoad, stillLoading } from "@/lib/failed-to-load";
import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

interface AddBindingPanelProps {
  source: GroupSource;
  suggestions: ComboBoxOption[];
  /** The roles read itself, not its data: the picker says while it loads and when it failed. */
  roles: ReturnType<typeof useRoles>;
}

function descriptionFor(source: GroupSource): string | undefined {
  if (source === "NONE") {
    return "The value as it appears in the token's groups claim. Read it off a member's first sign-in.";
  }
  return undefined;
}

function sourceIndicatorText(source: GroupSource): string {
  if (source === "NONE") {
    return "No directory connected";
  }
  // These are the real groups synced from the connected directory, not mere
  // suggestions, so the chip names the source directly.
  return SOURCE_LABEL[source];
}

export function AddBindingPanel({
  source,
  suggestions,
  roles,
}: Readonly<AddBindingPanelProps>) {
  const [text, setText] = useState("");
  const [value, setValue] = useState("");
  const [label, setLabel] = useState("");
  const [roleId, setRoleId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const bindGroup = useBindGroup();
  const ceiling = useCeiling();
  const description = descriptionFor(source);
  const rolesFailed = failedToLoad(roles);
  const rolesRecovered = useAnsweredAfterFailing(rolesFailed);

  function handleBind() {
    const trimmedValue = value.trim();
    if (trimmedValue === "" || roleId === null) return;
    const trimmedLabel = label.trim();
    setError(null);
    bindGroup.mutate(
      {
        groupId: trimmedValue,
        displayName: trimmedLabel === "" ? trimmedValue : trimmedLabel,
        roleId,
      },
      {
        onSuccess: () => {
          setText("");
          setValue("");
          setLabel("");
          setRoleId(null);
        },
        onError: (err) => {
          setError(
            err instanceof ApiError ? err.message : "Failed to bind group.",
          );
        },
      },
    );
  }

  return (
    <section className="border-border bg-surface flex flex-col gap-3 rounded-md border p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-foreground text-sm font-semibold">Add a binding</h2>
        {/* A connected directory feeds authoritative suggestions, so its chip
            carries the secondary accent to stand apart from the muted 'Direct
            entry' (manual) state. */}
        <span
          className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
            source === "NONE"
              ? "text-muted-foreground border-border bg-surface-sunken"
              : "text-secondary border-secondary/40 bg-secondary/10"
          }`}
        >
          {SOURCE_ICON[source]}
          {sourceIndicatorText(source)}
        </span>
      </div>
      <ComboBox
        label="Group"
        value={text}
        options={suggestions}
        placeholder="Object ID or group name"
        hintStyle="code"
        {...(description !== undefined ? { description } : {})}
        onInputChange={(t) => {
          setText(t);
          setValue(t);
          setLabel(t);
        }}
        onSelect={(o) => {
          setText(o.label);
          setValue(o.id);
          setLabel(o.label);
        }}
      />
      {rolesFailed ? (
        <LoadFailed inline message="Could not load the roles." query={roles} />
      ) : (
        <Select
          label="Role"
          value={roleId}
          options={roleOptions(roles.data ?? [], ceiling)}
          onChange={setRoleId}
          placeholder="Choose a role…"
          isLoading={stillLoading(roles)}
          takesFocus={rolesRecovered}
        />
      )}
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      <Button
        onPress={handleBind}
        isDisabled={
          value.trim() === "" || roleId === null || bindGroup.isPending
        }
      >
        <Plus size={16} aria-hidden />
        Bind
      </Button>
    </section>
  );
}
