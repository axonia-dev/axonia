import { SearchField } from "@/components/ui/SearchField";

/** The box that picks things out of the drawing. What it found is said by the toolbar, in the hint's place. */
export function MapSearchField({
  term,
  onTermChange,
}: Readonly<{
  term: string;
  onTermChange: (next: string) => void;
}>) {
  return (
    <SearchField
      label="Find on the map"
      value={term}
      onChange={onTermChange}
      placeholder="Find on the map"
      className="w-44"
    />
  );
}
