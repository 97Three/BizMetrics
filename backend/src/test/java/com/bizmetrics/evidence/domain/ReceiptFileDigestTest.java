package com.bizmetrics.evidence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReceiptFileDigestTest {
    @Test
    void createsCanonicalSha256Digest() {
        ReceiptFileDigest digest = ReceiptFileDigest.sha256("bizmetrics".getBytes(StandardCharsets.UTF_8));

        assertEquals("26aa5742e1da461d99505db1016991c9e1fdfddd76dac03c499905e8647dfc44", digest.value());
    }

    @Test
    void rejectsNonCanonicalDigestValues() {
        assertThrows(IllegalArgumentException.class, () -> new ReceiptFileDigest("a".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptFileDigest("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptFileDigest("not-a-digest"));
    }
}
