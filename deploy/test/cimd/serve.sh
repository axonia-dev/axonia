#!/usr/bin/env bash
# Serves the agent's client metadata document at https://cimd.test/client.json over a certificate made here, and
# leaves the trust store the backend reads it with in /certs. Test only: every password here is public.
set -euo pipefail

# A new certificate at every start, so a stack started again on a kept volume never meets an expired one. The backend
# waits for this service to be healthy, which it is only once the new trust store is in place.
rm -f /certs/trust.p12 /certs/trust.p12.partial /certs/cimd.p12 /tmp/cimd.p12 /tmp/cimd.cer
keytool -genkeypair -alias cimd -keyalg EC -groupname secp256r1 -validity 2 -dname CN=cimd.test \
	-ext SAN=dns:cimd.test -keystore /tmp/cimd.p12 -storetype PKCS12 -storepass e2e-only-key
keytool -exportcert -alias cimd -keystore /tmp/cimd.p12 -storepass e2e-only-key -file /tmp/cimd.cer
keytool -importcert -noprompt -alias cimd -file /tmp/cimd.cer -keystore /certs/trust.p12.partial \
	-storetype PKCS12 -storepass e2e-only-trust
cp /tmp/cimd.p12 /certs/cimd.p12
mv /certs/trust.p12.partial /certs/trust.p12
exec java /srv/cimd/CimdServer.java /certs/cimd.p12 /srv/cimd/client.json
