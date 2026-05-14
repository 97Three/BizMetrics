package com.bizmetrics.evidence.api;

import com.bizmetrics.evidence.domain.ReceiptVoucher;

/** Successful single-file upload response; field names preserve the public JSON contract. */
public record ReceiptUploadResponse(boolean success, ReceiptVoucher voucher, String message) {
    public static ReceiptUploadResponse uploaded(ReceiptVoucher voucher) {
        return new ReceiptUploadResponse(true, voucher, "Receipt uploaded");
    }
}
