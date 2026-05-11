package com.bizmetrics.bootstrap;

import com.bizmetrics.common.Permissions;
import com.bizmetrics.common.Roles;
import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.account.application.LocalUserAccountRepository;
import com.bizmetrics.service.support.PasswordHasher;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates the local demo administrator; production identity is owned by the atomic bootstrap command. */
@Component
@ConditionalOnProperty(name = "bizmetrics.bootstrap.mode", havingValue = "demo", matchIfMissing = true)
public class InitialAdminDataInitializer {
    private final LocalUserAccountRepository userAccounts;
    private final PasswordHasher passwordHasher;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminNickname;

    public InitialAdminDataInitializer(
        LocalUserAccountRepository userAccounts,
        PasswordHasher passwordHasher,
        @Value("${bizmetrics.bootstrap.admin-email:test@bizmetrics.com}") String adminEmail,
        @Value("${bizmetrics.bootstrap.admin-password:123456}") String adminPassword,
        @Value("${bizmetrics.bootstrap.admin-nickname:BizMetrics 管理员}") String adminNickname
    ) {
        this.userAccounts = userAccounts;
        this.passwordHasher = passwordHasher;
        this.adminEmail = defaultIfBlank(adminEmail, "test@bizmetrics.com").trim().toLowerCase(Locale.ROOT);
        this.adminPassword = defaultIfBlank(adminPassword, "123456");
        this.adminNickname = defaultIfBlank(adminNickname, "BizMetrics 管理员");
    }

    @PostConstruct
    void initialize() {
        if (userAccounts.count() != 0) {
            return;
        }
        userAccounts.insert(initialAdmin());
    }

    private User initialAdmin() {
        User user = new User();
        user.email = adminEmail;
        user.nickname = adminNickname;
        user.avatar = "😊|#3370ff";
        user.role = Roles.ADMIN;
        user.permissions = Permissions.ALL;
        user.passwordHash = passwordHasher.hash(adminPassword);
        String now = OffsetDateTime.now().toString();
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
