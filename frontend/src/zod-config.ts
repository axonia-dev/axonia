import { z } from "zod";

// The content security policy carries no 'unsafe-eval', so Zod's JIT compilation path
// is never available here. Left at its default, Zod still probes for it with a guarded
// `new Function("")` on every schema use, which the policy reports as a violation and
// Zod logs as a console error even though the probe itself fails safe. Declaring
// jitless up front skips the probe instead of merely tolerating its failure.
//
// This must run before any module constructs a Zod schema: Zod caches the probe's
// result the first time a schema is built, so importing this file too late is a silent
// no-op, not an error. boot.ts imports this file as a side effect, and each entry imports
// boot.ts above its import of App: see the comment there.
z.config({ jitless: true });
