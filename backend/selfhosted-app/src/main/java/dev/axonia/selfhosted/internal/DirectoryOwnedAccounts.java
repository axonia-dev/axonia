package dev.axonia.selfhosted.internal;

import org.springframework.stereotype.Component;

import dev.axonia.identity.IdentityAccounts;

/** The account a member signs in with is the company directory's, which axonia never writes to. */
@Component
class DirectoryOwnedAccounts implements IdentityAccounts {

    @Override
    public void deleteAccount(String subject) {
        // the company's directory decides when an account ends
    }
}
