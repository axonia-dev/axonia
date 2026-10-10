package dev.axonia.shared.adapter.in.schedule;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClusteredTaskRecoveryTest {

    @Test
    void eachTickAsksTheRegistrarToRecover() {

        var registrar = mock(ClusteredTaskRegistrar.class);

        new ClusteredTaskRecovery(registrar).recover();

        verify(registrar).recover();
    }
}
