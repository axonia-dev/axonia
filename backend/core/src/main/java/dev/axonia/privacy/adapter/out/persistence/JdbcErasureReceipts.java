package dev.axonia.privacy.adapter.out.persistence;

import org.springframework.stereotype.Component;

import dev.axonia.privacy.application.port.out.ErasureReceipts;
import dev.axonia.privacy.domain.ErasureReceipt;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcErasureReceipts implements ErasureReceipts {

    private final ErasureReceiptRepository repository;

    @Override
    public void record(ErasureReceipt receipt) {

        repository.save(toEntity(receipt));
    }

    static ErasureReceiptEntity toEntity(ErasureReceipt receipt) {

        return ErasureReceiptEntity.builder().organizationId(receipt.organizationId())
                .surrogateId(receipt.surrogateId()).erasedAt(receipt.erasedAt())
                .deletedRowCount(receipt.deletedRowCount()).clearedRowCount(receipt.clearedRowCount())
                .anonymisedRowCount(receipt.anonymisedRowCount()).mediaResidue(receipt.mediaResidue()).build();
    }
}
