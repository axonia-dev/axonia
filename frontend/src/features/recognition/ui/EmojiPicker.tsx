import { Plus } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { SearchField } from "@/components/ui/SearchField";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useReactionDefinitions,
  useReactionEligibility,
} from "@/features/recognition/api/use-reactions";
import {
  type EmojiGroup,
  loadEmojiCatalog,
} from "@/features/recognition/domain/emoji-search";
import {
  type PickerEntry,
  pickerGroups,
} from "@/features/recognition/domain/picker-groups";
import type { ReactionRef } from "@/features/recognition/domain/reaction";
import { EmojiGroupGrid } from "@/features/recognition/ui/EmojiGroupGrid";
import {
  rememberRecent,
  storedRecents,
} from "@/features/recognition/ui/reaction-recents";
import { ReactionGlyph } from "@/features/recognition/ui/ReactionGlyph";

const LIST_MAX_HEIGHT = 320;

function EntryButton({
  entry,
  onPick,
}: Readonly<{ entry: PickerEntry; onPick: (entry: PickerEntry) => void }>) {
  return (
    <button
      type="button"
      aria-label={entry.name}
      onClick={() => onPick(entry)}
      className="hover:bg-surface-sunken flex size-8 items-center justify-center rounded"
    >
      <ReactionGlyph
        emoji={entry.emoji}
        imageUrl={entry.imageUrl}
        size="picker"
      />
    </button>
  );
}

/** The word behind whichever entry the pointer or the focus ring is on. Read off the button's
 *  own accessible name rather than tracked per button: the catalog is close to two thousand
 *  entries, and a pair of handlers on each of them is two thousand closures rebuilt per
 *  keystroke. One delegated listener costs nothing and cannot drift from the label. */
function hoveredName(target: EventTarget): string | null {
  if (!(target instanceof HTMLElement)) {
    return null;
  }
  return target.closest("button")?.getAttribute("aria-label") ?? null;
}

interface FooterProps {
  mayPropose: boolean;
  name: string | null;
  onPropose: () => void;
}

/**
 * The entry point to a reaction of the organization's own on the left, and the word behind
 * the entry under the pointer on the right. The word is `aria-hidden`: every button in the
 * grid already carries it as its accessible name, and announcing it twice reads as a stutter.
 *
 * The height is fixed rather than left to the content. A member who may not propose has
 * nothing in this row but the word, so the row measured zero until the pointer reached a
 * glyph, and the popover grew a line the moment it did - the panel resizing under a cursor
 * that had only moved across it.
 */
function PickerFooter({ mayPropose, name, onPropose }: Readonly<FooterProps>) {
  return (
    <div className="border-border flex h-10 flex-none items-center gap-2 border-t pt-2">
      {mayPropose && (
        <Button
          variant="ghost"
          size="sm"
          onPress={onPropose}
          className="gap-1.5 px-2"
        >
          <Plus aria-hidden size={15} />
          Add a reaction
        </Button>
      )}
      <span
        aria-hidden
        className="text-muted-foreground ml-auto min-w-0 truncate pr-1 text-xs"
      >
        {name}
      </span>
    </div>
  );
}

export interface EmojiPickerProps {
  onPick: (ref: ReactionRef) => void;
  onPropose: () => void;
}

/**
 * The catalog of reactions, organized ones first, then the common set and this member's own
 * recents, then every standard group. Renders inside a popover owned by `ReactionBar`; it
 * never opens its own trigger. The standard catalog loads lazily on mount so it never reaches
 * the main bundle, and `pickerGroups` decides everything about order and naming, leaving this
 * component nothing to do but draw.
 */
export function EmojiPicker({ onPick, onPropose }: Readonly<EmojiPickerProps>) {
  const [term, setTerm] = useState("");
  const [catalog, setCatalog] = useState<EmojiGroup[]>([]);
  const [name, setName] = useState<string | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  // Read once, on open: the picker closes the moment something is picked, so a list that
  // changed underneath it would never be drawn anyway.
  const [recents] = useState(storedRecents);

  const definitions = useReactionDefinitions();
  const eligibility = useReactionEligibility();
  const organization = useCurrentOrganization();

  useEffect(() => {
    searchRef.current?.focus();
  }, []);

  useEffect(() => {
    let active = true;
    void loadEmojiCatalog().then((groups) => {
      if (active) {
        setCatalog(groups);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  // The standard catalog is nine groups and close to two thousand entries: assembling the
  // groups again on every render, of which each keystroke causes one, is what makes the picker
  // stutter on a phone. The derivation is pure, so a keystroke costs exactly one pass.
  const definitionData = definitions.data;
  const organizationName = organization.data?.name;
  const groups = useMemo(
    () =>
      pickerGroups({
        definitions: definitionData ?? [],
        catalog,
        recents,
        term,
        organizationName,
      }),
    [definitionData, catalog, recents, term, organizationName],
  );

  function pick(entry: PickerEntry) {
    if (entry.emoji !== null) {
      rememberRecent(entry.emoji);
    }
    onPick(entry.ref);
  }

  return (
    <div className="flex w-80 flex-col gap-2 p-2">
      <SearchField
        label="Search reactions"
        value={term}
        onChange={setTerm}
        placeholder="Search reactions"
        inputRef={searchRef}
        onFocus={(event) => event.currentTarget.select()}
      />
      <div
        className="relative flex flex-col gap-3 overflow-y-auto"
        style={{ maxHeight: LIST_MAX_HEIGHT }}
        onMouseOver={(event) => setName(hoveredName(event.target))}
        onFocus={(event) => setName(hoveredName(event.target))}
        onMouseOut={() => setName(null)}
        onBlur={() => setName(null)}
      >
        {groups.map((group) => (
          <EmojiGroupGrid key={group.heading} heading={group.heading}>
            {group.entries.map((entry) => (
              <EntryButton key={entry.key} entry={entry} onPick={pick} />
            ))}
          </EmojiGroupGrid>
        ))}
      </div>
      <PickerFooter
        mayPropose={eligibility.data?.mayPropose === true}
        name={name}
        onPropose={onPropose}
      />
    </div>
  );
}
