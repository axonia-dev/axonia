/**
 * What a mark is drawn in.
 *
 * Apart from `terrain-shading.ts`, which paints the ground, because this answers the opposite question: not what
 * colour a place is, but what stays readable standing on it.
 */
import {
  ACCENT_DIM,
  cast,
  type GroundPalette,
  mix,
  type Rgb,
  toChannels,
  toRgb,
} from "@/features/expertise/ui/terrain-shading";
import { relativeLuminance } from "@/lib/accent";

/** A mark's two colours: the glyph itself, and the rim drawn behind it in the opposite value. */
export interface MarkInk {
  ink: Rgb;
  halo: Rgb;
}

/**
 * What every mark on the drawing is painted in.
 *
 * A mark used to carry its own neighbourhood's accent, which stopped working the moment the ground became that
 * accent rather than a slate ramp with a wash of it: a mark was then painted the colour of the thing it stands on,
 * and measured against its own ground it came back at about 1.2 to 1, where the floor for something a reader has to
 * find and click is 3.
 *
 * The rim is what makes it legible, not the choice of ink - a word over this drawing has never had a background to
 * sit on either, and the names solved it the same way. So the pair is fixed rather than chosen against the ground:
 * chosen, it inverts between two areas standing on ground the reader cannot tell apart, and a map whose marks are
 * black here and white there reads as a fault rather than as a choice. Which way round the pair goes is the theme's,
 * because the two themes run the ramp in opposite directions and land is bright on one and deep on the other.
 */
export function markInk(palette: GroundPalette): MarkInk {
  return {
    ink: toRgb(cast(palette.glyph[0])),
    halo: toRgb(cast(palette.glyph[1])),
  };
}

/**
 * What the one mark a reader chose is painted in.
 *
 * The inside changes and the rim does not. A rim in the opposite value is what holds any glyph off any ground, so
 * with it kept the inside is free to carry a colour that means something - here, that this is the mark the strip
 * below the drawing is describing. A ring drawn around it instead says the same thing twice the size, and only for
 * the marks dense enough ground did not cost them their target.
 *
 * The colour is the community's own accent rather than one the map keeps: on a page already drawn in that hue, the
 * chosen mark reads as this community's, and it is the one hue on the drawing that can never be mistaken for a
 * neighbourhood's accent.
 *
 * Which rim it takes is therefore measured against the accent rather than copied from the theme's pair. An accent
 * is one hue for both themes, so a dark one - most of the palette - rimmed in the dark theme's own near-black, on
 * that theme's near-black ocean, is a hole where the chosen mark should be.
 */
export function chosenInk(palette: GroundPalette): MarkInk {
  const lit = relativeLuminance(palette.chosen);
  const rim = palette.glyph.reduce((furthest, hex) =>
    Math.abs(relativeLuminance(hex) - lit) >
    Math.abs(relativeLuminance(furthest) - lit)
      ? hex
      : furthest,
  );
  return { ink: toRgb(cast(palette.chosen)), halo: toRgb(cast(rim)) };
}

/** A mark the reading is not about: set back towards the muted colour, never erased, so the reader still sees that
 *  there is something there. */
export function setBack(colour: Rgb, palette: GroundPalette): Rgb {
  return toRgb(mix(toChannels(colour), cast(palette.muted), ACCENT_DIM));
}
