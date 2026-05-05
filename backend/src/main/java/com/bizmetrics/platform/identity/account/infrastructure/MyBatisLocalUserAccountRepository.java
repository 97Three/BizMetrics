package com.bizmetrics.platform.identity.account.infrastructure;

import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.account.application.LocalUserAccountRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLocalUserAccountRepository implements LocalUserAccountRepository {
    private final LocalUserAccountMapper mapper;

    public MyBatisLocalUserAccountRepository(LocalUserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long count() {
        return mapper.count();
    }

    @Override
    public User insert(User user) {
        Long id = mapper.insert(user, timestamp(user.createdAt), timestamp(user.updatedAt));
        if (id == null) {
            throw new IllegalStateException("User account insert returned no identity");
        }
        user.id = id;
        return user;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(mapper.findByEmail(email)).map(this::normalizeDates);
    }

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::normalizeDates);
    }

    @Override
    public Optional<User> findByIdForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id)).map(this::normalizeDates);
    }

    @Override
    public void update(User user) {
        int updated = mapper.update(user, timestamp(user.createdAt), timestamp(user.updatedAt));
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                "User account changed during update: " + user.id
            );
        }
    }

    @Override
    public boolean updatePasswordHashIfCurrent(
        long userId,
        String currentPasswordHash,
        String nextPasswordHash,
        String updatedAt
    ) {
        return mapper.updatePasswordHashIfCurrent(userId, currentPasswordHash,
            nextPasswordHash, timestamp(updatedAt)) == 1;
    }

    private User normalizeDates(User user) {
        user.createdAt = OffsetDateTime.parse(user.createdAt.replace(' ', 'T')).toString();
        user.updatedAt = OffsetDateTime.parse(user.updatedAt.replace(' ', 'T')).toString();
        return user;
    }

    private OffsetDateTime timestamp(String value) {
        return OffsetDateTime.parse(value);
    }
}
