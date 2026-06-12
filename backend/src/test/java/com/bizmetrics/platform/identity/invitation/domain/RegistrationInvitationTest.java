package com.bizmetrics.platform.identity.invitation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bizmetrics.common.Permissions;
import com.bizmetrics.common.Roles;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RegistrationInvitationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-01T10:00:00Z");

    @Test
    void pendingInvitationCanBePersistedAndAcceptedOnceByItsBoundEmail() {
        RegistrationInvitation pending = pending().withId(9);

        assertTrue(pending.canBeAcceptedBy("member@bizmetrics.test", NOW.plusHours(1)));
        assertFalse(pending.canBeAcceptedBy("other@bizmetrics.test", NOW.plusHours(1)));

        RegistrationInvitation accepted = pending.accept(17, NOW.plusHours(1));

        assertEquals(17, accepted.acceptedUserId());
        assertEquals(NOW.plusHours(1), accepted.acceptedAt());
        assertFalse(accepted.canBeAcceptedBy("member@bizmetrics.test", NOW.plusHours(2)));
        assertThrows(IllegalStateException.class, () -> accepted.accept(18, NOW.plusHours(2)));
    }

    @Test
    void expirationIsExclusiveAndInvalidDomainStateIsRejected() {
        RegistrationInvitation persisted = pending().withId(9);

        assertFalse(persisted.canBeAcceptedBy("member@bizmetrics.test", NOW.plusDays(1)));
        assertThrows(IllegalStateException.class, () ->
            persisted.accept(17, NOW.plusDays(1))
        );
        assertThrows(IllegalArgumentException.class, () -> new RegistrationInvitation(
            0,
            InvitationTokenDigest.fromRawToken("a".repeat(64)),
            "Member@BizMetrics.test",
            Roles.USER,
            Permissions.USER,
            NOW.plusDays(1),
            null,
            null,
            1L,
            NOW,
            NOW
        ));
    }

    private RegistrationInvitation pending() {
        return RegistrationInvitation.pending(
            InvitationTokenDigest.fromRawToken("a".repeat(64)),
            "member@bizmetrics.test",
            Roles.USER,
            Permissions.USER,
            NOW.plusDays(1),
            1,
            NOW
        );
    }
}
