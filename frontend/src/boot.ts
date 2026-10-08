import "@fontsource-variable/inter";
import "@fontsource-variable/space-grotesk";
import "@/styles/globals.css";
// Load-bearing order: a side-effect import runs where it stands, and App's graph builds Zod
// schemas at module scope while Zod caches its eval probe on the first one. Configuring Zod here,
// before an entry reaches its @/App import, is what keeps that probe from tripping the page's
// content security policy. Each entry imports this module first, as a side effect, which
// simple-import-sort leaves where it is; a named import could be relocated below @/App.
import "@/zod-config";
