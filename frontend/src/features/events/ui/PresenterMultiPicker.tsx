import { MemberSelectField } from "@/components/patterns/MemberSelectField";
import { Button } from "@/components/ui/Button";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import { useUsers } from "@/features/identity/api/use-users";

interface PresenterMultiPickerProps {
  slug: string;
  /** Selected userIds, in pick order. */
  value: string[];
  onChange: (userIds: string[]) => void;
}

/**
 * A backfilled talk's presenters: a run of removable chips, plus a member field that
 * appends. `MemberSelectField` has no way to exclude options it has already offered, so
 * this is the one place that decides an already-picked id is a no-op rather than a
 * duplicate - the field itself stays unaware of who is already on the list.
 */
export function PresenterMultiPicker({
  slug,
  value,
  onChange,
}: Readonly<PresenterMultiPickerProps>) {
  const directory = useUsers(value).data ?? {};

  function pick(userId: string) {
    if (value.includes(userId)) return;
    onChange([...value, userId]);
  }

  function remove(userId: string) {
    onChange(value.filter((id) => id !== userId));
  }

  return (
    <div className="flex flex-col gap-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5">
          {value.map((userId) => {
            const name = displayNameOf(userId, directory);
            return (
              <li key={userId}>
                <span className="bg-surface-sunken text-foreground border-border inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium">
                  {name}
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Remove ${name}`}
                    onPress={() => remove(userId)}
                    className="text-muted-foreground hover:text-foreground ml-0.5 h-4 w-4 rounded-full p-0"
                  >
                    ×
                  </Button>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      <MemberSelectField
        slug={slug}
        label="Presenters"
        placeholder="Add a member (optional)"
        picked={null}
        onPick={(member) => pick(member.userId)}
      />
    </div>
  );
}
