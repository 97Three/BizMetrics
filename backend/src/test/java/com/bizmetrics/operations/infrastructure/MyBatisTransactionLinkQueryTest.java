package com.bizmetrics.operations.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizmetrics.operations.application.TransactionLinkTarget;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class MyBatisTransactionLinkQueryTest {
    @Test
    void readsOnlyTheTransactionIdentityNeededForLinking() {
        TransactionLinkMapper mapper = mock(TransactionLinkMapper.class);
        TransactionLinkTarget target = new TransactionLinkTarget(42L, 7L, 9L);
        when(mapper.findById(42L)).thenReturn(target);
        MyBatisTransactionLinkQuery query = new MyBatisTransactionLinkQuery(mapper);

        assertEquals(target, query.findById(42L).orElseThrow());

        verify(mapper).findById(42L);
        String sql = mapperSql();
        assertTrue(sql.contains("SELECT id, company_id, user_id"));
        assertTrue(!sql.contains("JOIN") && !sql.contains("SELECT *"));
    }

    @Test
    void returnsEmptyWhenTheTransactionDoesNotExist() {
        TransactionLinkMapper mapper = mock(TransactionLinkMapper.class);
        MyBatisTransactionLinkQuery query = new MyBatisTransactionLinkQuery(mapper);

        assertTrue(query.findById(404L).isEmpty());
        verify(mapper).findById(404L);
    }

    private String mapperSql() {
        try {
            return String.join(" ", TransactionLinkMapper.class.getMethod("findById", long.class).getAnnotation(Select.class).value());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
