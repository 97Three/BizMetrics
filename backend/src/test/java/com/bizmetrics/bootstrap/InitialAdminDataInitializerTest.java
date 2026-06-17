package com.bizmetrics.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bizmetrics.common.Permissions;
import com.bizmetrics.common.Roles;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.account.application.LocalUserAccountRepository;
import com.bizmetrics.service.support.PasswordHasher;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InitialAdminDataInitializerTest {
    @Test
    void leavesExistingDemoUserAccountsUntouched() {
        LocalUserAccountRepository users = mock(LocalUserAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        when(users.count()).thenReturn(1L);

        initializer(users, passwordHasher, "ops@example.com", "Strong-pass-123!").initialize();

        verify(users, never()).insert(any());
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void seedsAndNormalizesTheConfiguredDemoAdministrator() {
        LocalUserAccountRepository users = mock(LocalUserAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        when(passwordHasher.hash("123456")).thenReturn("demo-hash");

        initializer(users, passwordHasher, " Demo@BizMetrics.COM ", "123456").initialize();

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).insert(user.capture());
        User inserted = user.getValue();
        assertEquals("demo@bizmetrics.com", inserted.email);
        assertEquals("Administrator", inserted.nickname);
        assertEquals("demo-hash", inserted.passwordHash);
        assertEquals(Roles.ADMIN, inserted.role);
        assertEquals(Permissions.ALL, inserted.permissions);
        assertEquals(inserted.createdAt, inserted.updatedAt);
        OffsetDateTime.parse(inserted.createdAt);
    }

    private InitialAdminDataInitializer initializer(
        LocalUserAccountRepository users,
        PasswordHasher passwordHasher,
        String email,
        String password
    ) {
        return new InitialAdminDataInitializer(
            users,
            passwordHasher,
            email,
            password,
            "Administrator"
        );
    }
}
