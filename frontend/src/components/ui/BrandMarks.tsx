interface MarkProps {
  className?: string | undefined;
}

/**
 * The vendor logos the product draws, in one place.
 *
 * Shared rather than owned by a feature because two unrelated slices need the same three marks:
 * identity names the provider a member signed in with, and the schedule names the calendar a feed
 * can be subscribed from. Copied instead, the paths themselves would be the duplication - a brand
 * mark is a long literal that no amount of renaming makes different.
 *
 * Each mark is `aria-hidden`: a logo beside its own name is decoration, and every caller here
 * writes that name out.
 */
export function GoogleMark({ className }: Readonly<MarkProps>) {
  return (
    <svg viewBox="0 0 48 48" className={className} aria-hidden>
      <path
        fill="#4285F4"
        d="M45.1 24.5c0-1.6-.1-3.1-.4-4.6H24v8.7h11.9c-.5 2.8-2.1 5.1-4.4 6.7v5.5h7.1c4.2-3.9 6.5-9.6 6.5-16.3Z"
      />
      <path
        fill="#34A853"
        d="M24 46c5.9 0 10.9-2 14.6-5.2l-7.1-5.5c-2 1.3-4.5 2.1-7.5 2.1-5.8 0-10.7-3.9-12.4-9.1H4.3v5.7C8 41.4 15.4 46 24 46Z"
      />
      <path
        fill="#FBBC05"
        d="M11.6 28.3a13.4 13.4 0 0 1 0-8.6v-5.7H4.3a22 22 0 0 0 0 20l7.3-5.7Z"
      />
      <path
        fill="#EA4335"
        d="M24 10.5c3.2 0 6.1 1.1 8.4 3.3l6.3-6.3C34.9 3.9 29.9 2 24 2 15.4 2 8 6.6 4.3 13.9l7.3 5.7c1.7-5.1 6.6-9.1 12.4-9.1Z"
      />
    </svg>
  );
}

export function GitHubMark({ className }: Readonly<MarkProps>) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="currentColor"
      aria-hidden
    >
      <path d="M12 .5C5.7.5.7 5.6.7 12c0 5.1 3.3 9.4 7.9 11 .6.1.8-.3.8-.6v-2.2c-3.2.7-3.9-1.4-3.9-1.4-.5-1.3-1.3-1.7-1.3-1.7-1.1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1 1.8 2.7 1.3 3.4 1 .1-.8.4-1.3.8-1.6-2.6-.3-5.3-1.3-5.3-5.7 0-1.3.5-2.3 1.2-3.1-.1-.3-.5-1.5.1-3.1 0 0 1-.3 3.3 1.2a11.3 11.3 0 0 1 6 0c2.3-1.5 3.3-1.2 3.3-1.2.6 1.6.2 2.8.1 3.1.8.8 1.2 1.9 1.2 3.1 0 4.4-2.7 5.4-5.3 5.7.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6 4.6-1.6 7.9-5.9 7.9-11C23.3 5.6 18.3.5 12 .5Z" />
    </svg>
  );
}

export function MicrosoftMark({ className }: Readonly<MarkProps>) {
  return (
    <svg viewBox="0 0 23 23" className={className} aria-hidden>
      <rect x="1" y="1" width="10" height="10" fill="#F25022" />
      <rect x="12" y="1" width="10" height="10" fill="#7FBA00" />
      <rect x="1" y="12" width="10" height="10" fill="#00A4EF" />
      <rect x="12" y="12" width="10" height="10" fill="#FFB900" />
    </svg>
  );
}

/** Drawn in `currentColor`, which is the only way one asset reads on both themes: the mark is a
 *  solid silhouette, and a black one on the dark surface is a hole rather than a logo. */
export function AppleMark({ className }: Readonly<MarkProps>) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="currentColor"
      aria-hidden
    >
      <path d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z" />
    </svg>
  );
}
