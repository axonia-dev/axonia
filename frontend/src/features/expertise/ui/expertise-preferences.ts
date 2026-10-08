export type ExpertiseViewMode = "search" | "map";

const VIEW_KEY = "axonia-expertise-view";

export function parseViewMode(raw: string | null): ExpertiseViewMode | null {
  return raw === "search" || raw === "map" ? raw : null;
}

export function storedViewMode(): ExpertiseViewMode | null {
  return parseViewMode(localStorage.getItem(VIEW_KEY));
}

export function storeViewMode(view: ExpertiseViewMode): void {
  localStorage.setItem(VIEW_KEY, view);
}
