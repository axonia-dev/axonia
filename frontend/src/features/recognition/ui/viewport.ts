/** Whether any part of the element is drawn inside the window. */
export function inViewport(element: Element): boolean {
  const r = element.getBoundingClientRect();
  return (
    r.width > 0 &&
    r.bottom > 0 &&
    r.right > 0 &&
    r.top < window.innerHeight &&
    r.left < window.innerWidth
  );
}
