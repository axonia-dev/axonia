import { Button } from "@/components/ui/Button";
import { useSetListing } from "@/features/mentoring/api/use-mentor-listing";
import type { Listing } from "@/features/mentoring/domain/mentoring";
import { Ask } from "@/features/mentoring/ui/Ask";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

/** The one-time ask of a mentor an ended pairing would list: Show lists them, Keep private lists nobody. */
export function ListingAsk({
  slug,
  listing,
}: Readonly<{ slug: string; listing: Listing }>) {
  return listing === "ASKED" ? <AskedOnce slug={slug} /> : null;
}

function AskedOnce({ slug }: Readonly<{ slug: string }>) {
  const set = useSetListing(slug);
  const answer = (listed: boolean) =>
    set.mutate(listed, { onError: onFailure });
  return (
    <Ask
      title="List among the mentors?"
      lines={[
        "Members of the community would see this mentor, and whether mentoring is under way or ended.",
        "A past pairing stays private either way.",
      ]}
    >
      <Button
        variant="outline"
        size="sm"
        isDisabled={set.isPending}
        onPress={() => answer(false)}
      >
        Keep private
      </Button>
      <Button size="sm" isDisabled={set.isPending} onPress={() => answer(true)}>
        Show
      </Button>
    </Ask>
  );
}
