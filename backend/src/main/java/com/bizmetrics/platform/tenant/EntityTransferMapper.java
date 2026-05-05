package com.bizmetrics.platform.tenant;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EntityTransferMapper {
    @Results(id = "transferResult", value = {
        @Result(column = "id", property = "id", id = true),
        @Result(column = "from_entity_id", property = "fromEntityId"),
        @Result(column = "to_entity_id", property = "toEntityId"),
        @Result(column = "from_entity_name", property = "fromEntityName"),
        @Result(column = "to_entity_name", property = "toEntityName"),
        @Result(column = "transfer_type", property = "transferType"),
        @Result(column = "amount", property = "amount"),
        @Result(column = "currency", property = "currency"),
        @Result(column = "transfer_date", property = "transferDate"),
        @Result(column = "note", property = "note"),
        @Result(column = "status", property = "status"),
        @Result(column = "operator_user_id", property = "operatorUserId"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt")
    })
    @Select("""
        <script>
        SELECT transfer.id, transfer.from_entity_id, transfer.to_entity_id,
               source.name AS from_entity_name, target.name AS to_entity_name,
               transfer.transfer_type, transfer.amount, transfer.currency, transfer.transfer_date,
               transfer.note, transfer.status, transfer.operator_user_id,
               transfer.created_at, transfer.updated_at
        FROM entity_transfers transfer
        JOIN companies source ON source.id = transfer.from_entity_id
        JOIN companies target ON target.id = transfer.to_entity_id
        WHERE (transfer.from_entity_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
          OR transfer.to_entity_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>)
        <if test="entityId != null">
          AND (transfer.from_entity_id = #{entityId} OR transfer.to_entity_id = #{entityId})
        </if>
        ORDER BY transfer.transfer_date DESC, transfer.id
        </script>
        """)
    List<EntityTransfer> findAccessible(@Param("ids") List<Long> ids, @Param("entityId") Long entityId);

    @Select("""
        SELECT EXISTS(SELECT 1 FROM entity_transfers
          WHERE (from_entity_id = #{first} AND to_entity_id = #{second})
             OR (from_entity_id = #{second} AND to_entity_id = #{first}))
        """)
    boolean existsBetween(@Param("first") long first, @Param("second") long second);

    @Select(value = """
        INSERT INTO entity_transfers (from_entity_id, to_entity_id, transfer_type, amount,
            currency, transfer_date, note, status, operator_user_id, created_at, updated_at)
        VALUES (#{transfer.fromEntityId}, #{transfer.toEntityId}, #{transfer.transferType},
            #{transfer.amount}, #{transfer.currency}, #{transferDate}, #{transfer.note},
            #{transfer.status}, #{transfer.operatorUserId}, #{createdAt}, #{updatedAt})
        RETURNING id
        """, affectData = true)
    Long insert(@Param("transfer") EntityTransfer transfer, @Param("transferDate") LocalDate transferDate,
                @Param("createdAt") OffsetDateTime createdAt, @Param("updatedAt") OffsetDateTime updatedAt);

    @Select("""
        SELECT source.name AS from_entity_name, target.name AS to_entity_name
        FROM companies source CROSS JOIN companies target
        WHERE source.id = #{fromId} AND target.id = #{toId}
        """)
    Map<String, Object> findNames(@Param("fromId") long fromId, @Param("toId") long toId);
}
