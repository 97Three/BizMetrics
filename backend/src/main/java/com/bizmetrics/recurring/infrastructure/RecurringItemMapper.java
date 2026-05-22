package com.bizmetrics.recurring.infrastructure;

import com.bizmetrics.recurring.domain.RecurringItem;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecurringItemMapper {
    @Results(id = "recurringItem", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "user_id", property = "userId"),
        @Result(column = "company_id", property = "companyId"),
        @Result(column = "name", property = "name"),
        @Result(column = "type", property = "type"),
        @Result(column = "amount", property = "amount"),
        @Result(column = "frequency", property = "frequency"),
        @Result(column = "interval_value", property = "interval"),
        @Result(column = "day_of_week", property = "dayOfWeek"),
        @Result(column = "day_of_month", property = "dayOfMonth"),
        @Result(column = "month_of_year", property = "monthOfYear"),
        @Result(column = "start_date", property = "startDate"),
        @Result(column = "end_date", property = "endDate"),
        @Result(column = "last_executed", property = "lastExecuted"),
        @Result(column = "next_execution", property = "nextExecution"),
        @Result(column = "status", property = "status"),
        @Result(column = "execution_count", property = "executionCount"),
        @Result(column = "note", property = "note")
    })
    @Select("""
        SELECT id, user_id, company_id, name, type, amount, frequency, interval_value,
            day_of_week, day_of_month, month_of_year, start_date::text AS start_date,
            end_date::text AS end_date, last_executed::text AS last_executed,
            next_execution::text AS next_execution, status, execution_count, note
        FROM recurring_items WHERE user_id = #{userId} AND company_id = #{companyId}
        ORDER BY next_execution, id
        """)
    List<RecurringItem> findByOwnerAndCompany(@Param("userId") long userId, @Param("companyId") long companyId);

    @org.apache.ibatis.annotations.ResultMap("recurringItem")
    @Select("""
        SELECT id, user_id, company_id, name, type, amount, frequency, interval_value,
            day_of_week, day_of_month, month_of_year, start_date::text AS start_date,
            end_date::text AS end_date, last_executed::text AS last_executed,
            next_execution::text AS next_execution, status, execution_count, note
        FROM recurring_items WHERE id = #{id} FOR UPDATE
        """)
    RecurringItem findByIdForUpdate(@Param("id") String id);

    @Insert("""
        INSERT INTO recurring_items (id, user_id, company_id, name, type, amount, frequency,
            interval_value, day_of_week, day_of_month, month_of_year, start_date, end_date,
            last_executed, next_execution, status, execution_count, note)
        VALUES (#{item.id}, #{item.userId}, #{item.companyId}, #{item.name}, #{item.type},
            #{item.amount}, #{item.frequency}, #{item.interval}, #{item.dayOfWeek},
            #{item.dayOfMonth}, #{item.monthOfYear}, CAST(#{item.startDate} AS date),
            CAST(#{item.endDate} AS date), CAST(#{item.lastExecuted} AS date),
            CAST(#{item.nextExecution} AS date), #{item.status}, #{item.executionCount}, #{item.note})
        """)
    int insert(@Param("item") RecurringItem item);

    @Update("""
        UPDATE recurring_items SET user_id = #{item.userId}, company_id = #{item.companyId},
            name = #{item.name}, type = #{item.type}, amount = #{item.amount},
            frequency = #{item.frequency}, interval_value = #{item.interval},
            day_of_week = #{item.dayOfWeek}, day_of_month = #{item.dayOfMonth},
            month_of_year = #{item.monthOfYear}, start_date = CAST(#{item.startDate} AS date),
            end_date = CAST(#{item.endDate} AS date), last_executed = CAST(#{item.lastExecuted} AS date),
            next_execution = CAST(#{item.nextExecution} AS date), status = #{item.status},
            execution_count = #{item.executionCount}, note = #{item.note} WHERE id = #{item.id}
        """)
    int update(@Param("item") RecurringItem item);

    @Delete("DELETE FROM recurring_items WHERE id = #{id}")
    int delete(@Param("id") String id);
}
