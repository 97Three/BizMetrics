package com.bizmetrics.platform.identity.session.infrastructure;

import com.bizmetrics.platform.identity.User;
import com.bizmetrics.platform.identity.session.application.LocalSessionRepository;
import com.bizmetrics.platform.identity.session.domain.LocalSession;
import com.bizmetrics.platform.identity.session.domain.SessionTokenDigest;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLocalSessionRepository implements LocalSessionRepository {
    private final LocalSessionMapper mapper;

    public MyBatisLocalSessionRepository(LocalSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(LocalSession session) {
        int inserted = mapper.insert(
            session.tokenDigest().value(),
            session.userId(),
            session.createdAt(),
            session.expiresAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Session insert did not write exactly one row");
        }
    }

    @Override
    public Optional<User> findActiveUser(SessionTokenDigest tokenDigest, OffsetDateTime now) {
        return Optional.ofNullable(mapper.findActiveUser(tokenDigest.value(), now));
    }

    @Override
    public void delete(SessionTokenDigest tokenDigest) {
        mapper.delete(tokenDigest.value());
    }

    @Override
    public int deleteExpired(OffsetDateTime now) {
        return mapper.deleteExpired(now);
    }
}
