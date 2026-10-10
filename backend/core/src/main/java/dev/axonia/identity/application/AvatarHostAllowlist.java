package dev.axonia.identity.application;

import java.util.List;

/** The hosts avatar bytes may be fetched from, as configured. */
public interface AvatarHostAllowlist {

    List<String> allowedHosts();
}
