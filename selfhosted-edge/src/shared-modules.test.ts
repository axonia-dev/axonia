// The four edge modules this package runs are tested where they live, beside them in edge/src.
// Imported here, their tests run in this package's suite too, so a tree that carries no edge
// package of its own still runs them.
import "../../edge/src/auth.test.ts";
import "../../edge/src/csp.test.ts";
