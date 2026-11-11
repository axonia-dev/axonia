import { Switch, SWITCH_CONTROL_INDENT } from "@/components/ui/Switch";
import { useSetPaused } from "@/features/expertise/api/use-expertise-prefs";
import { cn } from "@/lib/cn";

/** On while the member takes questions; the choice in flight shows at once, before the server answers.
 *  The pause reaches every community, which a community page has to say. */
export function TakingQuestionsSwitch({
  paused,
  inCommunity,
}: Readonly<{ paused: boolean; inCommunity: boolean }>) {
  const setPaused = useSetPaused();
  const shownPaused = setPaused.isPending
    ? (setPaused.variables ?? paused)
    : paused;
  return (
    <div className="flex flex-col gap-1">
      <Switch
        label="Taking questions"
        description="Paused, the ring disappears and nothing is lost"
        isSelected={!shownPaused}
        onChange={(taking) => setPaused.mutate(!taking)}
      />
      {inCommunity && (
        <p
          className={cn("text-muted-foreground text-xs", SWITCH_CONTROL_INDENT)}
        >
          Applies in every community
        </p>
      )}
    </div>
  );
}
