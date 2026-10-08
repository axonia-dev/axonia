import { useEffect } from "react";
import { useLocation } from "react-router-dom";

import { reportView } from "./faro";

// Route changes are reported from a router effect rather than by Faro's own
// NavigationInstrumentation. That instrumentation is removed from the set unless
// experimental.trackNavigation is on, and even then it only fires when a pointerdown or keydown is
// followed by both a URL change and a DOM mutation inside one activity window - so a back-button
// navigation, a redirect, or any programmatic navigate() raises nothing at all. An effect on the
// location fires on every one of those, and it reports the route rather than the address.
//
// Rendering null rather than wrapping the tree: this has to sit inside the router to read the
// location, and nothing below it should have to know that telemetry exists.
export function TelemetryView() {
  const { pathname } = useLocation();

  useEffect(() => {
    reportView(pathname);
  }, [pathname]);

  return null;
}
