package dev.axonia.selfhosted.adapter.out.identity;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.SuccessionAddresses;

/** No address is declared to succeed another: a member who changes address is a new member to the company directory. */
@Component
class NoSuccessionAddresses implements SuccessionAddresses {

    @Override
    public Optional<String> formerAddressOf(String organizationId, String arrivingAddress) {
        return Optional.empty();
    }
}
