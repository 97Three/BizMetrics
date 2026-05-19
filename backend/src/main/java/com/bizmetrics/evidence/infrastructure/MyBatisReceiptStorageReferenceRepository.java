package com.bizmetrics.evidence.infrastructure;

import com.bizmetrics.evidence.application.ReceiptStorageReferenceRepository;
import com.bizmetrics.evidence.application.ReceiptStorageReferenceSnapshot;
import com.bizmetrics.evidence.domain.ReceiptObjectLocation;
import java.util.ArrayList;
import java.util.List;
import com.bizmetrics.evidence.infrastructure.ReceiptStorageReferenceMapper.ReferenceRow;
import org.springframework.stereotype.Repository;

/** Reads the durable database side of the MinIO receipt object relationship. */
@Repository
public class MyBatisReceiptStorageReferenceRepository implements ReceiptStorageReferenceRepository {
    private final ReceiptStorageReferenceMapper mapper;

    public MyBatisReceiptStorageReferenceRepository(ReceiptStorageReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReceiptStorageReferenceSnapshot findAll(String defaultBucket, int maximumReferences) {
        String normalizedDefaultBucket = requireBucket(defaultBucket);
        if (maximumReferences <= 0 || maximumReferences >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum receipt references must be positive and bounded");
        }
        List<ReferenceRow> rows = mapper.findAll(maximumReferences + 1);
        if (rows.size() > maximumReferences) {
            throw new IllegalStateException("Receipt reference inventory exceeded configured maximum");
        }

        List<ReceiptObjectLocation> references = new ArrayList<>(rows.size());
        long invalidReferenceCount = 0L;
        for (ReferenceRow row : rows) {
            String bucket = row.bucket() == null || row.bucket().isBlank()
                ? normalizedDefaultBucket
                : row.bucket().strip();
            try {
                references.add(new ReceiptObjectLocation(bucket, row.objectKey()));
            } catch (IllegalArgumentException ex) {
                invalidReferenceCount++;
            }
        }
        return new ReceiptStorageReferenceSnapshot(references, invalidReferenceCount);
    }

    private String requireBucket(String value) {
        String bucket = value == null ? "" : value.strip();
        if (bucket.isEmpty()) {
            throw new IllegalArgumentException("Default receipt bucket is required");
        }
        return bucket;
    }

}
