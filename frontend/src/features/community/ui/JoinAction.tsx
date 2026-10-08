import { Button } from "@/components/ui/Button";
import { useJoin } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";

/** The community's front door: the one call to action a visitor is offered. */
export function JoinAction({ slug }: Readonly<{ slug: string }>) {
  const join = useJoin(slug);

  return (
    <div className="flex flex-col gap-1">
      <Button isDisabled={join.isPending} onPress={() => join.mutate()}>
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
