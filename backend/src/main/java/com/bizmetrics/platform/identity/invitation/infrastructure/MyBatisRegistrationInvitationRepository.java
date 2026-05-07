package com.bizmetrics.platform.identity.invitation.infrastructure;

import com.bizmetrics.platform.identity.invitation.application.RegistrationInvitationRepository;
import com.bizmetrics.platform.identity.invitation.domain.InvitationTokenDigest;
import com.bizmetrics.platform.identity.invitation.domain.RegistrationInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisRegistrationInvitationRepository implements RegistrationInvitationRepository {
    private final RegistrationInvitationMapper mapper;

    public MyBatisRegistrationInvitationRepository(RegistrationInvitationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RegistrationInvitation insert(RegistrationInvitation invitation) {
        Long id = mapper.insert(
            invitation.tokenDigest().value(),
            invitation.email(),
            invitation.role(),
            invitation.permissions(),
            invitation.expiresAt(),
            invitation.invitedByUserId(),
            invitation.createdAt(),
            invitation.updatedAt()
        );
        if (id == null) {
            throw new IllegalStateException("Invitation insert returned no identity");
        }
        return invitation.withId(id);
    }

    @Override
    public List<RegistrationInvitation> findAll() {
        return mapper.findAll().stream().map(this::map).toList();
    }

    @Override
    public Optional<RegistrationInvitation> findByTokenForUpdate(InvitationTokenDigest tokenDigest) {
        return Optional.ofNullable(mapper.findByTokenForUpdate(tokenDigest.value())).map(this::map);
    }

    @Override
    public void updateAcceptance(RegistrationInvitation acceptedInvitation) {
        int updated = mapper.updateAcceptance(
            acceptedInvitation.id(),
            acceptedInvitation.acceptedAt(),
            acceptedInvitation.acceptedUserId(),
            acceptedInvitation.updatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Invitation acceptance did not update exactly one pending row");
        }
    }

    private RegistrationInvitation map(RegistrationInvitationMapper.Row row) {
        return new RegistrationInvitation(
            row.id(),
            new InvitationTokenDigest(row.token()),
            row.email(), row.role(), row.permissions(), row.expiresAt(), row.acceptedAt(),
            row.acceptedUserId(), row.invitedByUserId(), row.createdAt(), row.updatedAt()
        );
    }
}
