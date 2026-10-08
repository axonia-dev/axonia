/** The one visual definition of a segmented control, shared by the toggle-button form
 *  ({@link SegmentedControl}), the link form ({@link SegmentedNav}) and the tab form
 *  ({@link Tabs}) so the three cannot drift apart.
 *
 *  Sizing belongs in that shared definition rather than in each consumer, because a track of
 *  four tabs does not fit a phone and getting it wrong costs more than the track: the tabs
 *  cannot shrink (a tab is a word, and `whitespace-nowrap` keeps it one line rather than
 *  stacking "Action items" into two), so a track that may not wrap widens the page instead,
 *  and every row on that page then scrolls sideways - including the control sitting beside the
 *  track, which ends up off screen. Wrapping is what keeps it in: it costs a second row of
 *  pills on a narrow screen and hides nothing, where scrolling the track would cut the last
 *  tab off mid-word with no hint that it is reachable. A wide screen fits one row and looks
 *  exactly as it did. `max-w-full` is the cap that makes the wrap happen (`w-fit` hugs the
 *  tabs but does not stop at the container, and `w-fit` is what keeps the sunken track from
 *  stretching past its last tab in a flex column), and `overflow-x-auto` is the floor under
 *  both: a single label too wide for the whole track scrolls inside it rather than out. */

export const segmentedGroupClass =
  "inline-flex w-fit max-w-full flex-wrap gap-0.5 overflow-x-auto rounded-md bg-surface-sunken p-0.5";

export const segmentedItemClass =
  "group cursor-pointer rounded-sm px-3 py-1.5 text-xs font-semibold whitespace-nowrap text-muted-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-focus-ring";

export const segmentedSelectedClass = "bg-surface text-foreground shadow-sm";
