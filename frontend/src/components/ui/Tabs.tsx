import type { ReactNode } from "react";
import {
  Tab,
  TabList,
  TabPanel,
  Tabs as AriaTabs,
} from "react-aria-components";

import {
  segmentedGroupClass,
  segmentedItemClass,
} from "@/components/ui/segmented-styles";
import { cn } from "@/lib/cn";

interface TabItem {
  id: string;
  label: string;
}

interface TabsProps {
  /** Accessible name for the tab list. Never rendered: the tabs name themselves. */
  label: string;
  items: readonly TabItem[];
  selected: string;
  onSelect: (id: string) => void;
  /** Drawn at the far end of the tab row, for a fact belonging to the whole panel. */
  aside?: ReactNode;
  /** The open tab's panel. Only that one is ever rendered. */
  children: ReactNode;
}

// `data-[selected]`, the attribute react-aria hangs on the open tab, exactly as
// {@link SegmentedControl} styles its own selected toggle.
const tabClass = cn(
  segmentedItemClass,
  "data-[selected]:bg-surface data-[selected]:text-foreground data-[selected]:shadow-sm",
);

/**
 * One control switching what is shown below it, with the roles that actually say so.
 *
 * The third member of the segmented family, and the one that reveals rather than chooses.
 * {@link SegmentedControl} is a radio group - it picks a value, and a value is something the
 * surrounding form goes on to use - and {@link SegmentedNav} is a set of links, which put the
 * choice in the URL. This is neither: the sections of one page, switched in place, which is what
 * `tablist`/`tab`/`tabpanel` exist for. A screen reader gets "tab, 1 of 4, selected" and the panel
 * it controls, and the arrow keys walk the row.
 *
 * It borrows the segmented classes rather than growing a look of its own, so a page that switches
 * sections and a page that switches views cannot end up drawing one gesture two ways.
 */
export function Tabs({
  label,
  items,
  selected,
  onSelect,
  aside,
  children,
}: Readonly<TabsProps>) {
  return (
    <AriaTabs
      selectedKey={selected}
      onSelectionChange={(key) => {
        onSelect(String(key));
      }}
      className="flex flex-col gap-4"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        {/* A tab row that does not fit a phone is ordinary - six sections of one connection
            already do not - so it wraps onto a second row rather than widening the page and
            taking the aside beside it off screen. That comes with segmentedGroupClass. */}
        <TabList aria-label={label} className={segmentedGroupClass}>
          {items.map((item) => (
            <Tab key={item.id} id={item.id} className={tabClass}>
              {item.label}
            </Tab>
          ))}
        </TabList>
        {aside}
      </div>
      <TabPanel id={selected} className="outline-none">
        {children}
      </TabPanel>
    </AriaTabs>
  );
}
