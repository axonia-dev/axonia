// Stable avatar colour for a user, hashed from a durable key (the JWT `sub` /
// user id). Mirrors kindHue's deterministic approach with a cooler palette so
// people read distinctly from community kinds.
const USER_HUES = [
  "#6e3ceb",
  "#de3b78",
  "#3b82de",
  "#1aa6a0",
  "#9b3cc4",
  "#1f9d63",
] as const;

export function userHue(key: string): string {
  let sum = 0;
  for (let i = 0; i < key.length; i += 1) {
    sum += key.charCodeAt(i);
  }
  return USER_HUES[sum % USER_HUES.length] ?? USER_HUES[0];
}
