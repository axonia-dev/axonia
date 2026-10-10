package dev.axonia.privacy.application.port.out;

import dev.axonia.privacy.domain.ErasureReceipt;

public interface ErasureReceipts {

    void record(ErasureReceipt receipt);
}
