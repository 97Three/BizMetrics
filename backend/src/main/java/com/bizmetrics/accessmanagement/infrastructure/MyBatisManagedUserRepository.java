package com.bizmetrics.accessmanagement.infrastructure;

import com.bizmetrics.accessmanagement.application.ManagedUserRepository;
import com.bizmetrics.accessmanagement.domain.ManagedUser;
import com.bizmetrics.common.PageRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisManagedUserRepository implements ManagedUserRepository {
    private final ManagedUserMapper mapper;

    public MyBatisManagedUserRepository(ManagedUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ManagedUserPage search(String keyword, PageRequest pageRequest) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        long total = mapper.searchCount(normalizedKeyword);
        long offset = (long) pageRequest.page() * pageRequest.size();
        List<ManagedUser> content = mapper.search(normalizedKeyword, pageRequest.size(), offset)
            .stream().map(this::normalizeDates).toList();
        return new ManagedUserPage(content, total);
    }

    @Override
    public void lockAccessMutations() {
        mapper.lockAccessMutations();
    }

    @Override
    public Optional<ManagedUser> findForAccessMutation(long id) {
        return Optional.ofNullable(mapper.findForAccessMutation(id)).map(this::normalizeDates);
    }

    @Override
    public long countUsers() {
        return mapper.countUsers();
    }

    @Override
    public long countAdministrators() {
        return mapper.countAdministrators();
    }

    @Override
    public ManagedUser updateAccess(ManagedUser user) {
        int updated = mapper.updateAccess(user, OffsetDateTime.parse(user.updatedAt()));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Managed user changed during access update: " + user.id());
        }
        return user;
    }

    @Override
    public void delete(long id) {
        final int deleted;
        try {
            deleted = mapper.delete(id);
        } catch (DataIntegrityViolationException ex) {
            throw new UserDeletionConflictException(ex);
        }
        if (deleted != 1) {
            throw new OptimisticLockingFailureException("Managed user changed during deletion: " + id);
        }
    }

    private ManagedUser normalizeDates(ManagedUser user) {
        return new ManagedUser(user.id(), user.email(), user.nickname(), user.avatar(),
            user.familyId(), user.role(), user.permissions(),
            OffsetDateTime.parse(user.createdAt().replace(' ', 'T')).toString(),
            OffsetDateTime.parse(user.updatedAt().replace(' ', 'T')).toString());
    }

}
