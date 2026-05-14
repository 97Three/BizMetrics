package com.bizmetrics.evidence.api;

import jakarta.validation.constraints.Positive;

/** Validated company scope for the receipt summary endpoint. */
public record ReceiptSummaryRequest(@Positive Long companyId) {
}
