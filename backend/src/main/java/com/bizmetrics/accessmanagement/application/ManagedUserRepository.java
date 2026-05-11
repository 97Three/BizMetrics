package com.bizmetrics.accessmanagement.application;

import com.bizmetrics.accessmanagement.domain.ManagedUser;
import com.bizmetrics.common.PageRequest;
import java.util.List;
import java.util.Optional;

/** Persistence port for the access-management user projection. */
public interface ManagedUserRepository {
    ManagedUserPage search(String keyword, PageRequest pageRequest);

    void lockAccessMutations();

    Optional<ManagedUser> findForAccessMutation(long id);

    long countUsers();

    long countAdministrators();

    ManagedUser updateAccess(ManagedUser user);

    void delete(long id);

    record ManagedUserPage(List<ManagedUser> content, long totalElements) {
    }

    final class UserDeletionConflictException extends IllegalStateException {
        public UserDeletionConflictException(Throwable cause) {
            super("User is referenced by retained business or audit records", cause);
        }
    }
}
