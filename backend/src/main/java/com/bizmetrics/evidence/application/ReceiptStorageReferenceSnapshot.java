package com.bizmetrics.evidence.application;

import com.bizmetrics.evidence.domain.ReceiptObjectLocation;
import java.util.List;

public record ReceiptStorageReferenceSnapshot(
    List<ReceiptObjectLocation> references,
    long invalidReferenceCount
) {
    public ReceiptStorageReferenceSnapshot {
        references = List.copyOf(references);
        if (invalidReferenceCount < 0) {
            throw new IllegalArgumentException("Invalid receipt reference count must not be negative");
        }
    }
}
