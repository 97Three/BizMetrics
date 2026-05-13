package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.operations.application.TransactionLinkTarget;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransactionLinkMapper {
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "user_id", javaType = long.class)
    })
    @Select("SELECT id, company_id, user_id FROM transactions WHERE id = #{transactionId}")
    TransactionLinkTarget findById(@Param("transactionId") long transactionId);
}
