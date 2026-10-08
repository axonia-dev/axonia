import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { MapPerson } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  avatarColour,
  drawFace,
  faceSprites,
  initialsOf,
} from "@/features/expertise/ui/face-sprites";

describe("initialsOf", () => {
  it("takes the first and last word, not the first two", () => {
    expect(initialsOf("Sanne de Vries")).toBe("SV");
  });

  it("handles a single name", () => {
    expect(initialsOf("Prince")).toBe("P");
  });

  it("uppercases", () => {
    expect(initialsOf("ada lovelace")).toBe("AL");
  });

  it("survives extra whitespace", () => {
    expect(initialsOf("  Grace   Hopper  ")).toBe("GH");
  });

  it("gives an empty name something rather than throwing", () => {
    expect(initialsOf("")).toBe("?");
  });
});

describe("avatarColour", () => {
  it("is stable for a name", () => {
    expect(avatarColour("Ada Lovelace")).toBe(avatarColour("Ada Lovelace"));
  });

  it("does not give every name the same colour", () => {
    const names = ["Ada", "Grace", "Alan", "Katherine", "Edsger", "Barbara"];

    expect(new Set(names.map(avatarColour)).size).toBeGreaterThan(1);
  });
});

const person: MapPerson = {
  userId: "u-1",
  displayName: "Ada Lovelace",
  avatarUrl: null,
};

/** jsdom has no 2D context, so the drawing is checked against a recorder rather than against pixels. */
function recordingCanvas() {
  const calls: string[] = [];
  const ctx = {
    fillStyle: "",
    font: "",
    textAlign: "",
    textBaseline: "",
    clearRect: () => calls.push("clearRect"),
    save: () => calls.push("save"),
    restore: () => calls.push("restore"),
    beginPath: () => calls.push("beginPath"),
    arc: () => calls.push("arc"),
    clip: () => calls.push("clip"),
    fillRect: () => calls.push("fillRect"),
    fillText: (text: string) => calls.push(`fillText:${text}`),
    drawImage: () => calls.push("drawImage"),
  } as unknown as CanvasRenderingContext2D;

  const canvas = document.createElement("canvas");
  canvas.width = 64;
  vi.spyOn(canvas, "getContext").mockReturnValue(ctx);
  return { canvas, calls };
}

describe("drawFace", () => {
  it("draws the initials when the directory has no photo", () => {
    const { canvas, calls } = recordingCanvas();

    drawFace(canvas, person, null);

    expect(calls).toContain("fillText:AL");
    expect(calls).not.toContain("drawImage");
  });

  it("draws the photo when there is one", () => {
    const { canvas, calls } = recordingCanvas();

    drawFace(canvas, person, new Image());

    expect(calls).toContain("drawImage");
    expect(calls.some((call) => call.startsWith("fillText"))).toBe(false);
  });

  it("does nothing rather than throwing where there is no 2D context", () => {
    const canvas = document.createElement("canvas");
    vi.spyOn(canvas, "getContext").mockReturnValue(null);

    expect(() => drawFace(canvas, person, null)).not.toThrow();
  });
});

const photographed: MapPerson = {
  userId: "u-2",
  displayName: "Grace Hopper",
  avatarUrl: "https://example.test/grace.png",
};

function mark(signalId: number, userId: string): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x: 120,
    y: 90,
    slot: 0,
    sourceType: "SLOT",
    userId,
    occurredAt: 0,
    reach: 40,
  };
}

const place = (x: number, y: number) => new THREE.Vector3(x, 0, y);

describe("faceSprites", () => {
  const tracked: FakeImage[] = [];
  const repaint = vi.fn();

  class FakeImage {
    crossOrigin: string | null = null;
    src = "";
    readonly listeners: (() => void)[] = [];
    constructor() {
      tracked.push(this);
    }
    addEventListener(_type: string, handler: () => void) {
      this.listeners.push(handler);
    }
    arrive() {
      for (const handler of this.listeners) handler();
    }
  }

  beforeEach(() => {
    tracked.length = 0;
    repaint.mockClear();
    vi.stubGlobal("Image", FakeImage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function layerFor(faced: number[], people: MapPerson[]) {
    return faceSprites({
      marks: [mark(1, "u-1"), mark(2, "u-2")],
      faced: new Set(faced),
      people: new Map(people.map((p) => [p.userId, p])),
      place,
      repaint,
    });
  }

  it("draws nothing for a contribution that did not clear the gate", () => {
    expect(layerFor([], [person, photographed]).sprites).toHaveLength(0);
  });

  it("draws nothing for a contributor the payload never named", () => {
    expect(layerFor([1, 2], []).sprites).toHaveLength(0);
  });

  it("places a face on the mark", () => {
    const [sprite] = layerFor([1], [person]).sprites;

    expect(sprite!.position.x).toBe(120);
    expect(sprite!.position.z).toBe(90);
  });

  /** A face stands among marks that are sized in pixels, so attenuating it would grow it past them on every zoom
   *  click while they held still. The camera sets the scale that delivers those pixels. */
  it("does not let the distance to a face change how big it draws", () => {
    const [sprite] = layerFor([1], [person]).sprites;

    expect(sprite!.material.sizeAttenuation).toBe(false);
  });

  it("repaints the same texture once the photo arrives", () => {
    const [sprite] = layerFor([2], [photographed]).sprites;
    const texture = sprite!.material.map!;
    const before = texture.version;

    tracked[0]!.arrive();

    expect(tracked[0]!.crossOrigin).toBe("anonymous");
    expect(texture.version).toBeGreaterThan(before);
  });

  /** The map draws only when asked, so a photo landing after the frame is drawn would otherwise never show. */
  it("asks for a frame once the photo arrives, and not before", () => {
    layerFor([2], [photographed]);
    expect(repaint).not.toHaveBeenCalled();

    tracked[0]!.arrive();

    expect(repaint).toHaveBeenCalledOnce();
  });

  it("leaves a torn-down texture alone when the photo arrives late", () => {
    const layer = layerFor([2], [photographed]);
    const texture = layer.sprites[0]!.material.map!;
    layer.dispose();
    const after = texture.version;

    tracked[0]!.arrive();

    expect(texture.version).toBe(after);
    expect(repaint).not.toHaveBeenCalled();
  });
});
