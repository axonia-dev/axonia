package dev.axonia.privacy;

import java.io.IOException;
import java.io.OutputStream;

import dev.axonia.ExportScope;

/**
 * A data-subject's export package, streamed straight into the caller's {@link OutputStream} as it is produced - never
 * staged in storage first. There is deliberately no package sitting around to leak, no expiry policy to get wrong and
 * no signed-link surface to defend; the tradeoff is that a failure partway through leaves the caller with a truncated
 * stream rather than a retryable link, which for a one-shot data-subject request is the safer failure mode.
 */
public interface DataExport {

    void writeTo(ExportScope scope, OutputStream out) throws IOException;
}
