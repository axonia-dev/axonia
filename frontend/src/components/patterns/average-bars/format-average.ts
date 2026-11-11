/** An average on the 1 to 10 scale, always to one decimal so 8 and 8.0 never read as different. */
export function formatAverage(value: number): string {
  return value.toFixed(1);
}
