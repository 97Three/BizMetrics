package com.bizmetrics.platform.identity.security.infrastructure;

import com.bizmetrics.platform.identity.security.application.LoginFailureRepository;
import com.bizmetrics.platform.identity.security.domain.LoginFailureState;
import com.bizmetrics.platform.identity.security.domain.LoginThrottleSubject;
import com.bizmetrics.platform.identity.security.domain.LoginThrottleSubject.Type;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLoginFailureRepository implements LoginFailureRepository {
    private final LoginFailureMapper mapper;

    public MyBatisLoginFailureRepository(LoginFailureMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LoginFailureState lockOrCreate(LoginThrottleSubject subject, OffsetDateTime now) {
        mapper.insertIfAbsent(subject.keyHash(), subject.type().databaseValue(), now);
        LoginFailureMapper.Row row = mapper.lock(subject.keyHash());
        if (row == null) throw new IllegalStateException("Login failure state disappeared while locked");
        return map(row);
    }

    @Override
    public void update(LoginFailureState state) {
        int updated = mapper.update(
            state.subject().keyHash(),
            state.subject().type().databaseValue(),
            state.failedAttempts(),
            state.windowStartedAt(),
            state.lockedUntil(),
            state.updatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Login failure state changed while locked");
        }
    }

    @Override
    public Optional<OffsetDateTime> findLatestActiveLock(
        Collection<LoginThrottleSubject> subjects,
        OffsetDateTime now
    ) {
        List<String> keys = subjects.stream().map(LoginThrottleSubject::keyHash).distinct().toList();
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findLatestActiveLock(keys, now));
    }

    @Override
    public void delete(LoginThrottleSubject subject) {
        mapper.delete(subject.keyHash(), subject.type().databaseValue());
    }

    @Override
    public int deleteInactiveBefore(OffsetDateTime updatedBefore, OffsetDateTime now) {
        return mapper.deleteInactiveBefore(updatedBefore, now);
    }

    private LoginFailureState map(LoginFailureMapper.Row row) {
        LoginThrottleSubject subject = new LoginThrottleSubject(
            Type.fromDatabase(row.subjectType()),
            row.subjectKey()
        );
        return new LoginFailureState(
            subject,
            row.failedAttempts(),
            row.windowStartedAt(),
            row.lockedUntil(),
            row.updatedAt()
        );
    }
}
