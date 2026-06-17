package com.bizmetrics.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bizmetrics.people.application.EmploymentEventRepository;
import com.bizmetrics.platform.tenant.EntityTransferRepository;
import com.bizmetrics.platform.audit.application.AuditLogRepository;
import com.bizmetrics.platform.identity.account.application.LocalUserAccountRepository;
import com.bizmetrics.platform.identity.account.application.UserDirectory;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LegacyStoreBoundaryTest {
    @Test
    void inMemoryStoreCompatibilityClassCannotReturn() {
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.bizmetrics.repository.InMemoryStore")
        );
    }

    @Test
    void enterpriseStoreCompatibilityClassCannotReturn() {
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.bizmetrics.repository.EnterpriseStore")
        );
    }

    @Test
    void auditPersistenceBoundaryExposesNoMutationOrDeletionOperations() {
        Set<String> forbiddenPrefixes = Set.of("delete", "remove", "update", "replace", "save");
        Set<String> exposedMethods = Arrays.stream(AuditLogRepository.class.getDeclaredMethods())
            .map(method -> method.getName().toLowerCase())
            .filter(name -> forbiddenPrefixes.stream().anyMatch(name::startsWith))
            .collect(Collectors.toSet());

        assertTrue(exposedMethods.isEmpty(), () ->
            "Audit logs must remain append-only at the persistence boundary: " + exposedMethods
        );
    }

    @Test
    void employmentEventPersistenceBoundaryExposesNoUpdateOperation() {
        Set<String> forbiddenPrefixes = Set.of("update", "replace", "save");
        Set<String> exposedMethods = Arrays.stream(EmploymentEventRepository.class.getDeclaredMethods())
            .map(method -> method.getName().toLowerCase())
            .filter(name -> forbiddenPrefixes.stream().anyMatch(name::startsWith))
            .collect(Collectors.toSet());

        assertTrue(exposedMethods.isEmpty(), () ->
            "Employment events must remain append-only outside the explicit demo-reset deletion: " + exposedMethods
        );
    }

    @Test
    void entityTransferPersistenceBoundaryExposesNoMutationOrDeletionOperations() {
        Set<String> forbiddenPrefixes = Set.of("delete", "remove", "update", "replace", "save");
        Set<String> exposedMethods = Arrays.stream(EntityTransferRepository.class.getDeclaredMethods())
            .map(method -> method.getName().toLowerCase())
            .filter(name -> forbiddenPrefixes.stream().anyMatch(name::startsWith))
            .collect(Collectors.toSet());

        assertTrue(exposedMethods.isEmpty(), () ->
            "Entity transfers must remain append-only at the persistence boundary: " + exposedMethods
        );
    }

    @Test
    void crossModuleUserDirectoryCannotExposeCredentials() {
        assertTrue(Arrays.stream(UserDirectory.Entry.class.getRecordComponents())
            .noneMatch(component -> component.getName().toLowerCase().contains("password")),
            "Cross-module user projections must remain password-free");
        assertTrue(Arrays.stream(LocalUserAccountRepository.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("findAll")),
            "Credential-bearing account repositories must not provide bulk cross-module reads");
    }

}
