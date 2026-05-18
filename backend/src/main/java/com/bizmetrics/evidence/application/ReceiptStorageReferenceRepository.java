package com.bizmetrics.evidence.application;

public interface ReceiptStorageReferenceRepository {
    ReceiptStorageReferenceSnapshot findAll(String defaultBucket, int maximumReferences);
}
