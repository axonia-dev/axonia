import { cn } from "@/lib/cn";

/**
 * One box, both kinds of reaction. A standard emoji is text and a custom reaction is an
 * image, and left to themselves they sit differently: text on a baseline at whatever size it
 * inherits, an image at its own. Centring both in a box of a fixed size is what makes a chip
 * strip mixing the two read as one row.
 *
 * The character is set a couple of pixels under the box so its own ascent lands on the box's
 * height, which is what makes an emoji look the same size as an image beside it rather than
 * measurably larger.
 */
const SIZES = {
  chip: "size-[18px] text-[15px]",
  picker: "size-5 text-[17px]",
} as const;

export type GlyphSize = keyof typeof SIZES;

export function ReactionGlyph({
  emoji,
  imageUrl,
  size,
}: Readonly<{
  emoji: string | null;
  imageUrl: string | null;
  size: GlyphSize;
}>) {
  return (
    <span
      aria-hidden
      className={cn(
        "inline-flex flex-none items-center justify-center leading-none",
        SIZES[size],
      )}
    >
      {imageUrl === null ? (
        emoji
      ) : (
        <img src={imageUrl} alt="" className="size-full object-contain" />
      )}
    </span>
  );
}
