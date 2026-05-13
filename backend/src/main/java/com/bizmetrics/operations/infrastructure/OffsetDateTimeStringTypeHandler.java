package com.bizmetrics.operations.infrastructure;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Maps PostgreSQL timestamps to the ISO strings used by transaction domain records. */
public class OffsetDateTimeStringTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType jdbcType)
        throws SQLException {
        statement.setObject(index, OffsetDateTime.parse(value));
    }

    @Override
    public String getNullableResult(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    @Override
    public String getNullableResult(ResultSet result, int index) throws SQLException {
        OffsetDateTime value = result.getObject(index, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    @Override
    public String getNullableResult(CallableStatement result, int index) throws SQLException {
        OffsetDateTime value = result.getObject(index, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }
}
