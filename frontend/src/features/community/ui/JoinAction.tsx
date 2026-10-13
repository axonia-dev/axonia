import { Button } from "@/components/ui/Button";
import { useJoin } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";

interface JoinActionProps {
  slug: string;
  onJoined?: () => void;
}

/** The community's front door: the one call to action a visitor is offered. */
export function JoinAction({ slug, onJoined }: Readonly<JoinActionProps>) {
  const join = useJoin(slug);

  // A promise rather than mutate's callbacks: the join swaps this button for the member's chip
  // as soon as the membership refetches, and a mutate callback never fires once unmounted.
  const onPress = () => {
    void join.mutateAsync().then(onJoined, () => undefined);
  };

  return (
    <div className="flex flex-col gap-1">
      <Button isDisabled={join.isPending} onPress={onPress}>
        Join
      </Button>
      {join.isError && (
        <p role="alert" className="text-danger text-sm">
          {communityErrorMessage(join.error)}
        </p>
      )}
    </div>
  );
}
