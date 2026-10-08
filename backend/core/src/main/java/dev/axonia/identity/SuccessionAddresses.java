package dev.axonia.identity;

import java.util.Optional;

/**
 * Port: the address a running connection succession says an arriving directory address used to be.
 *
 * <p>
 * A customer replacing their identity provider generally replaces the directory behind it too, and a succession they
 * declared may rename everybody's domain on the way. {@link SuccessionAdoption} asks this before it provisions a SCIM
 * push as somebody new.
 */
public interface SuccessionAddresses {

    /**
     * The address a returning member would be holding, or empty when no succession of this Organization is open right
     * now or the address cannot be judged. An address on a domain nobody declared a rewrite for comes back unchanged
     * while a succession is open: a customer may replace the provider without moving anybody's address.
     */
    Optional<String> formerAddressOf(String organizationId, String arrivingAddress);
}
