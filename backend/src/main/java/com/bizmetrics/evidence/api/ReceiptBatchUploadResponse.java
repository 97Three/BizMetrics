package com.bizmetrics.evidence.api;

import com.bizmetrics.evidence.application.ReceiptBatchUploadResult;
import com.bizmetrics.evidence.application.ReceiptUploadFailure;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import java.util.List;

/** Batch upload response with the same JSON shape as the legacy map response. */
public record ReceiptBatchUploadResponse(
    int successCount,
    int failureCount,
    List<ReceiptVoucher> vouchers,
    List<ReceiptUploadFailure> failures
) {
    public static ReceiptBatchUploadResponse from(ReceiptBatchUploadResult result) {
        return new ReceiptBatchUploadResponse(
            result.successCount(),
            result.failureCount(),
            result.vouchers(),
            result.failures()
        );
    }
}
