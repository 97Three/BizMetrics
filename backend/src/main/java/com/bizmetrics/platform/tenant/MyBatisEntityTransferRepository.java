package com.bizmetrics.platform.tenant;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisEntityTransferRepository implements EntityTransferRepository {
    private final EntityTransferMapper mapper;

    public MyBatisEntityTransferRepository(EntityTransferMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<EntityTransfer> findAccessible(List<Long> accessibleEntityIds, Long entityId) {
        List<Long> ids = accessibleEntityIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return mapper.findAccessible(ids, entityId).stream().map(this::normalizeDates).toList();
    }

    @Override
    public boolean existsBetween(long firstEntityId, long secondEntityId) {
        return mapper.existsBetween(firstEntityId, secondEntityId);
    }

    @Override
    public EntityTransfer append(EntityTransfer transfer) {
        EntityTransferPolicy.normalizeAndValidate(transfer);
        String now = OffsetDateTime.now().toString();
        transfer.createdAt = now;
        transfer.updatedAt = now;
        Long id = mapper.insert(transfer, LocalDate.parse(transfer.transferDate),
            OffsetDateTime.parse(transfer.createdAt), OffsetDateTime.parse(transfer.updatedAt));
        if (id == null) throw new IllegalStateException("Database did not return a generated entity transfer id");
        transfer.id = id;
        return findNames(transfer);
    }

    private EntityTransfer findNames(EntityTransfer transfer) {
        Map<String, Object> names = mapper.findNames(transfer.fromEntityId, transfer.toEntityId);
        transfer.fromEntityName = String.valueOf(names.get("from_entity_name"));
        transfer.toEntityName = String.valueOf(names.get("to_entity_name"));
        return transfer;
    }

    private EntityTransfer normalizeDates(EntityTransfer transfer) {
        transfer.createdAt = OffsetDateTime.parse(transfer.createdAt.replace(' ', 'T')).toString();
        transfer.updatedAt = OffsetDateTime.parse(transfer.updatedAt.replace(' ', 'T')).toString();
        return transfer;
    }
}
