package dev.axonia.selfhosted.adapter.out.identity;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.IdentityAccounts;

/** The account a member signs in with is the company directory's, which axonia never writes to. */
@Component
class DirectoryOwnedAccounts implements IdentityAccounts {

    @Override
    public void deleteAccount(String subject) {
        // the company's directory decides when an account ends
    }
}
