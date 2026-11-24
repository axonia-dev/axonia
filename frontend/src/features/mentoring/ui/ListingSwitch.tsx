import { Switch } from "@/components/ui/Switch";
import { notify } from "@/components/ui/toast";
import { useSetListing } from "@/features/mentoring/api/use-mentor-listing";
import type { Listing } from "@/features/mentoring/domain/mentoring";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

/** Whether the member is among the mentors the community sees; present only once they are listed or would be but for the switch. */
export function ListingSwitch({
  slug,
  listing,
  isDisabled,
}: Readonly<{ slug: string; listing: Listing; isDisabled: boolean }>) {
  return listing === "LISTED" || listing === "OFF" ? (
    <ListedChoice
      slug={slug}
      listed={listing === "LISTED"}
      isDisabled={isDisabled}
    />
  ) : null;
}

function ListedChoice({
  slug,
  listed,
  isDisabled,
}: Readonly<{ slug: string; listed: boolean; isDisabled: boolean }>) {
  const set = useSetListing(slug);
  return (
    <Switch
      className="mt-4"
      label="Listed among the mentors"
      isSelected={listed}
      isDisabled={isDisabled}
      onChange={(next) =>
        set.mutate(next, {
          onSuccess: () => notify("Saved"),
          onError: onFailure,
        })
      }
    />
  );
}
