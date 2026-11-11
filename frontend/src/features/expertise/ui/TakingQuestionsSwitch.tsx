import { Switch } from "@/components/ui/Switch";
import { useSetPaused } from "@/features/expertise/api/use-expertise-prefs";

/** On while the member takes questions; the choice in flight shows at once, before the server answers. */
export function TakingQuestionsSwitch({
  paused,
}: Readonly<{ paused: boolean }>) {
  const setPaused = useSetPaused();
  const shownPaused = setPaused.isPending
    ? (setPaused.variables ?? paused)
    : paused;
  return (
    <Switch
      label="Taking questions"
      description="Paused, the ring disappears and nothing is lost"
      isSelected={!shownPaused}
      onChange={(taking) => setPaused.mutate(!taking)}
    />
  );
}
