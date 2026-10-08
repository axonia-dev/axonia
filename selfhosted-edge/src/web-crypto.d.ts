import type { webcrypto } from "node:crypto";

// edge/src names a Web Crypto key by the global type the Workers runtime declares. Node's types for
// version 24 declare only its constructor; from version 26 they declare the type as below.
declare global {
  interface CryptoKey extends webcrypto.CryptoKey {}
}
