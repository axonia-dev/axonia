package dev.axonia.identity.application;

import java.time.Duration;

/** How long each secret and each stage of an agent connection lives, as configured. */
public interface ConnectionLifetimes {

    Duration codeTtl();

    Duration accessTokenTtl();

    Duration refreshTokenIdleTtl();

    Duration connectionMaxLifetime();

    Duration refreshReuseGrace();

    Duration retiredRetention();
}
