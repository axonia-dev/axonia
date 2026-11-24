import { Button } from "@/components/ui/Button";
import { useSetListing } from "@/features/mentoring/api/use-mentor-listing";
import type { Listing } from "@/features/mentoring/domain/mentoring";
import { Ask } from "@/features/mentoring/ui/Ask";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

/** The one-time ask of a mentor an ended pairing would list: Show lists them, Keep private lists nobody. */
export function ListingAsk({
  slug,
  listing,
  isDisabled = false,
}: Readonly<{ slug: string; listing: Listing; isDisabled?: boolean }>) {
  return listing === "ASKED" ? (
    <AskedOnce slug={slug} isDisabled={isDisabled} />
  ) : null;
}

function AskedOnce({
  slug,
  isDisabled,
}: Readonly<{ slug: string; isDisabled: boolean }>) {
  const set = useSetListing(slug);
  const answer = (listed: boolean) =>
    set.mutate(listed, { onError: onFailure });
  return (
    <div className="mt-4">
      <Ask
        title="Show among the mentors?"
        lines={["Only the mentor shows, never a mentee"]}
      >
        <Button
          variant="outline"
          size="sm"
          isDisabled={isDisabled || set.isPending}
          onPress={() => answer(false)}
        >
          Keep private
        </Button>
        <Button
          size="sm"
          isDisabled={isDisabled || set.isPending}
          onPress={() => answer(true)}
        >
          Show
        </Button>
      </Ask>
    </div>
  );
}
