import { ChevronDown } from "lucide-react";
import {
  type ReactNode,
  type RefObject,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  Button as AriaButton,
  ComboBox as AriaComboBox,
  ComboBoxStateContext,
  Group,
  Input,
  Label,
  Popover,
  Text,
} from "react-aria-components";

import { useLoadMoreOnView } from "@/components/patterns/use-load-more-on-view";
import type {
  ComboBoxOption,
  HintStyle,
} from "@/components/ui/ComboBoxSuggestions";
import { ComboBoxSuggestions } from "@/components/ui/ComboBoxSuggestions";
import { useReselectAfterPick } from "@/components/ui/use-reselect-after-pick";
import { cn } from "@/lib/cn";

interface ComboBoxProps {
  label: string;
  value: string;
  options: ReadonlyArray<ComboBoxOption>;
  onInputChange: (text: string) => void;
  onSelect: (option: ComboBoxOption) => void;
  placeholder?: string;
  description?: string;
  hideLabel?: boolean;
  errorMessage?: string;
  /** `options` already reflects the query (e.g. a server search): skip react-aria's own contains
   *  re-filter, which would otherwise hide a match that does not literally contain the typed text. */
  serverFiltered?: boolean;
  /** Shown in the list in place of items, e.g. while a search is in flight or found nothing. */
  statusMessage?: string;
  /** "code" sets option hints in monospace, for identifiers such as group ids. */
  hintStyle?: HintStyle;
  /** Called when the foot of the list comes into view. Pair with `hasMore`. */
  onLoadMore?: () => void;
  /** Whether another page exists. False tears the observer down for good. */
  hasMore?: boolean;
  /** True while a page is on the wire. The sentinel stays on screen once the list is scrolled to
   *  its foot, so without this the next render asks for the page that is already coming. */
  isLoadingMore?: boolean;
}

const fieldClass =
  "flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-2 text-sm text-foreground outline-none transition-colors focus-within:ring-2 focus-within:ring-focus-ring data-[invalid]:border-danger";
const popoverClass =
  "relative min-w-[var(--trigger-width)] overflow-y-auto overscroll-contain rounded-md border border-border-strong bg-surface p-1 shadow-lg outline-none";
// react-aria sizes the popover to the space left on screen, which for a long list is the whole
// viewport: a 229-country list rendered 8000px of options through a box with nothing to clip them,
// so the page grew a second scrollbar and the wheel moved the page instead of the list. This is the
// cap the overlay's own measurement is taken as the minimum of, so the list scrolls inside itself.
const POPOVER_MAX_HEIGHT = 288;

// A stable reference so the underlying AriaComboBox selection stays controlled across renders.
const selectionValue = null;
// Passed as `defaultFilter` for a server-filtered caller, so react-stately's own filter is a no-op.
const matchesEverything = () => true;
// Default for `onLoadMore` when a caller has no more pages to offer at all.
const noop = () => {};
// Another page exists and none is already on the wire.
const wantsAnotherPage = (hasMore?: boolean, isLoadingMore?: boolean) =>
  hasMore === true && isLoadingMore !== true;

interface ComboBoxImperativeHandles {
  open: () => void;
  close: () => void;
}

function wantsEmptyResults(
  serverFiltered: boolean | undefined,
  statusMessage: string | undefined,
): boolean {
  // Only a live search may open with nothing to show yet (e.g. a "Searching" status).
  return serverFiltered === true || statusMessage !== undefined;
}

// `menuTrigger="focus"` would reopen the popover the instant a pick's resulting value flows back
// in as a new `value` prop: react-stately treats any inputValue change while focused as "show
// matching suggestions", with no way to tell a resync from real typing. Driving open/close
// ourselves (menuTrigger="manual" below) sidesteps that entirely. Both live only on the
// ComboBoxState reached via context from inside AriaComboBox's subtree, so this stashes them in a
// ref the outer JSX (Input's onFocus, the onChange selection handler) can call synchronously.
function ComboBoxImperativeSync({
  handlesRef,
}: Readonly<{ handlesRef: RefObject<ComboBoxImperativeHandles | null> }>) {
  const state = useContext(ComboBoxStateContext);
  useEffect(() => {
    handlesRef.current = state
      ? { open: () => state.open(), close: () => state.close() }
      : null;
  });
  return null;
}

/**
 * A labelled combobox that always accepts a typed value and additionally offers suggestions. Selecting a suggestion
 * reports its underlying id via onSelect; typing, and the revert of unpicked text on blur, report the literal text
 * via onInputChange. This is the baseline-plus-enhancement control for authoring group bindings: it never blocks a
 * typed value even with no options.
 */
export function ComboBox({
  label,
  value,
  options,
  onInputChange,
  onSelect,
  placeholder,
  description,
  hideLabel,
  errorMessage,
  serverFiltered,
  statusMessage,
  hintStyle,
  onLoadMore,
  hasMore,
  isLoadingMore,
}: Readonly<ComboBoxProps>): ReactNode {
  // Echo typed text locally and resync only when the `value` prop itself changes: a plain
  // `inputValue={value}` freezes the field for callers that do not round-trip every keystroke.
  const [inputValue, setInputValue] = useState(value);
  const [lastValueProp, setLastValueProp] = useState(value);
  if (value !== lastValueProp) {
    setLastValueProp(value);
    setInputValue(value);
  }
  const handlesRef = useRef<ComboBoxImperativeHandles | null>(null);
  // Real DOM focus only: react-aria re-dispatches focus without a blur after a pick or arrow keys.
  const focusedRef = useRef(false);
  const reselect = useReselectAfterPick(value);
  const hasList = options.length > 0 || statusMessage !== undefined;
  const allowsEmpty = wantsEmptyResults(serverFiltered, statusMessage);
  const { rootRef, sentinelRef } = useLoadMoreOnView(
    wantsAnotherPage(hasMore, isLoadingMore),
    onLoadMore ?? noop,
  );

  // menuTrigger="manual" means nothing else calls open()/close() once results land; this re-asserts it.
  useEffect(() => {
    if (!focusedRef.current) {
      return;
    }
    if (hasList) {
      handlesRef.current?.open();
    } else {
      handlesRef.current?.close();
    }
  }, [hasList]);

  return (
    <AriaComboBox
      inputValue={inputValue}
      onInputChange={(text) => {
        setInputValue(text);
        reselect.disarm();
        onInputChange(text);
        if (hasList) {
          handlesRef.current?.open();
        } else {
          handlesRef.current?.close();
        }
      }}
      onChange={(key) => {
        if (key == null) {
          return;
        }
        const picked = options.find((o) => o.id === String(key));
        if (picked) {
          reselect.notePick();
          onSelect(picked);
        }
        handlesRef.current?.close();
      }}
      // No selection of its own: the parent owns the text. Pinning the AriaComboBox selection to a
      // stable `null` keeps it controlled, so react-stately never runs its post-selection
      // resetInputValue(), which would overwrite the input with the label and fire a spurious
      // onInputChange right after onSelect.
      value={selectionValue}
      allowsCustomValue
      allowsEmptyCollection={allowsEmpty}
      menuTrigger="manual"
      {...(serverFiltered ? { defaultFilter: matchesEverything } : {})}
      isInvalid={errorMessage !== undefined}
      className="flex flex-col gap-1"
    >
      <ComboBoxImperativeSync handlesRef={handlesRef} />
      <Label
        className={cn(
          "text-foreground text-sm font-medium",
          hideLabel && "sr-only",
        )}
      >
        {label}
      </Label>
      <Group className={fieldClass}>
        <Input
          className="w-full bg-transparent text-ellipsis outline-none"
          onFocus={(event) => {
            if (focusedRef.current) {
              return;
            }
            focusedRef.current = true;
            // Typing into a field that already holds a value would otherwise append to it, and a
            // combobox filters on what the field holds: "Switzerland" + "ger" matches nothing at
            // all, which reads as a control that has stopped working. Selecting the text makes the
            // first keystroke replace it, the way picking a new value is meant to feel.
            event.currentTarget.select();
            if (hasList) {
              handlesRef.current?.open();
            }
          }}
          onClick={reselect.selectOnClick}
          onBlur={() => {
            focusedRef.current = false;
            reselect.disarm();
            // Typing without picking a suggestion leaves `inputValue` echoing the keystrokes while
            // the caller's `value` - what actually gets submitted - never moved. Leaving that on
            // screen reads as committed when it is not; reverting to `value` on blur makes the
            // field always show what will be sent. A no-op for callers (like the free-text group
            // picker) whose `value` already tracks every keystroke.
            if (inputValue !== value) {
              setInputValue(value);
              onInputChange(value);
            }
          }}
          {...(placeholder !== undefined ? { placeholder } : {})}
        />
        <AriaButton
          aria-hidden
          className="text-muted-foreground shrink-0 cursor-pointer"
        >
          <ChevronDown size={16} />
        </AriaButton>
      </Group>
      {description !== undefined && (
        <Text slot="description" className="text-muted-foreground text-xs">
          {description}
        </Text>
      )}
      {errorMessage !== undefined && (
        <Text slot="errorMessage" role="alert" className="text-danger text-sm">
          {errorMessage}
        </Text>
      )}
      {hasList && (
        <Popover
          ref={rootRef}
          maxHeight={POPOVER_MAX_HEIGHT}
          className={popoverClass}
        >
          <ComboBoxSuggestions
            options={options}
            statusMessage={statusMessage}
            hintStyle={hintStyle}
            hasMore={hasMore === true}
            sentinelRef={sentinelRef}
          />
        </Popover>
      )}
    </AriaComboBox>
  );
}

export type { ComboBoxOption } from "@/components/ui/ComboBoxSuggestions";
