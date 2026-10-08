import { useState } from "react";

interface TagSelection {
  selected: Set<string>;
  selectedList: string[];
  survivorOverride: string | null;
  toggleSelect: (tag: string) => void;
  setSurvivorOverride: (tag: string) => void;
  clearSelection: () => void;
}

/** Local state for the multi-select tag merge flow in TagCurationView. */
export function useTagSelection(): TagSelection {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [survivorOverride, setSurvivorOverride] = useState<string | null>(null);

  function toggleSelect(tag: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(tag)) {
        next.delete(tag);
      } else {
        next.add(tag);
      }
      return next;
    });
  }

  function clearSelection() {
    setSelected(new Set());
    setSurvivorOverride(null);
  }

  return {
    selected,
    selectedList: [...selected],
    survivorOverride,
    toggleSelect,
    setSurvivorOverride,
    clearSelection,
  };
}
