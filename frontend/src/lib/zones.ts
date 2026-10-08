/**
 * Where a reader sits in time, and how to say it.
 *
 * In `lib` rather than under `events` because both features need it: the account page reads the
 * browser's own zone to report it, and the spread note names a city per zone. No re-export from
 * the old path - two import paths for one function is how two copies of it start.
 */

/**
 * The zone the browser is set to: the clock a reader has in front of them.
 *
 * Named once so a caller reading it says what it is rather than restating the incantation. It is
 * the right answer only where no other clock governs - what a release is picked on, and the
 * fallback for a session that belongs to no series - so a caller that could take it for a
 * governed time has to go through `governingZone` instead.
 */
export function viewerZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/**
 * A zone named for a reader rather than for a database: the place out of an IANA identifier, with
 * the underscores it separates words with turned back into spaces.
 */
export function cityOf(timeZone: string): string {
  const last = timeZone.split("/").at(-1) ?? timeZone;
  return last.replaceAll("_", " ");
}

// The full IANA list, computed once: ComboBox already runs its own contains-filter against
// whatever the field displays, the same way CountryComboBox hands it every country. A second,
// hand-rolled filter here previously narrowed the *options* by a separate query string while
// ComboBox filtered by the *displayed* text, so on an untouched field the two never agreed and
// the popover opened empty.
const IANA_ZONES = Intl.supportedValuesOf("timeZone");

// That list is what the engine chooses to enumerate, not what the backend stores. V8 leaves out
// every `Etc/*` zone and "UTC" itself, and the migration that added the column backfilled every
// pre-existing series to exactly "UTC" - so the control filtered on a value it did not offer and
// opened with nothing in it on the whole backfilled population, the one place the field exists to
// be corrected from. The stored value joins the list whatever it is, so no zone a migration or a
// different engine produces can go unrepresented again.
export function zoneOptions(stored: string) {
  const zones = new Set(IANA_ZONES);
  zones.add("UTC");
  if (stored.trim() !== "") {
    zones.add(stored);
  }
  return [...zones]
    .sort((a, b) => a.localeCompare(b))
    .map((zone) => ({ id: zone, label: zone }));
}

export function ignoreTypedZoneText() {
  // The field only ever commits a zone through onSelect (a required, backend-validated value
  // can't be left as arbitrary typed text); ComboBox's own inputValue state already renders each
  // keystroke, so there is nothing for the caller to do with it here.
}
