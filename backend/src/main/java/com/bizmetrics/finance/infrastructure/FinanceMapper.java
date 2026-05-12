package com.bizmetrics.finance.infrastructure;

import com.bizmetrics.finance.application.FinanceRepository.MemberProfile;
import com.bizmetrics.finance.domain.Account;
import com.bizmetrics.finance.domain.AccountReconciliation;
import com.bizmetrics.finance.domain.Ledger;
import com.bizmetrics.finance.domain.LedgerMember;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FinanceMapper {
    String METRICS = """
        SELECT account.*,
            COALESCE(SUM(CASE WHEN transaction_record.date >= #{periodStart}
                AND transaction_record.date < #{periodEndExclusive}
                AND transaction_record.type = 1 THEN transaction_record.amount ELSE 0 END), 0) AS monthly_income,
            GREATEST(COALESCE(SUM(CASE WHEN transaction_record.date >= #{periodStart}
                AND transaction_record.date < #{periodEndExclusive}
                AND transaction_record.type = 2 THEN transaction_record.amount
                WHEN transaction_record.date >= #{periodStart}
                AND transaction_record.date < #{periodEndExclusive}
                AND transaction_record.type = 3 THEN -transaction_record.amount ELSE 0 END), 0), 0) AS monthly_expense,
            COUNT(transaction_record.id) AS transaction_count,
            MAX(transaction_record.date) AS last_transaction_date
        FROM accounts account LEFT JOIN transactions transaction_record
            ON transaction_record.account_id = account.id
            AND transaction_record.user_id = account.user_id
            AND transaction_record.company_id = account.company_id
        """;

    @Results(id = "account", value = {
        @Result(column = "created_at", property = "createdAt", typeHandler = OffsetDateTimeStringTypeHandler.class),
        @Result(column = "updated_at", property = "updatedAt", typeHandler = OffsetDateTimeStringTypeHandler.class)
    })
    @Select("SELECT * FROM accounts WHERE user_id = #{userId} AND company_id = #{companyId} ORDER BY id")
    List<Account> findAccounts(@Param("userId") long userId, @Param("companyId") long companyId);

    @ResultMap("account")
    @Select(METRICS + " WHERE account.user_id = #{userId} AND account.company_id = #{companyId} GROUP BY account.id ORDER BY account.id")
    List<Account> findAccountsWithMetrics(@Param("userId") long userId, @Param("companyId") long companyId,
        @Param("periodStart") LocalDate periodStart, @Param("periodEndExclusive") LocalDate periodEndExclusive);

    @ResultMap("account")
    @Select(METRICS + " WHERE account.id = #{id} GROUP BY account.id")
    Account findAccountWithMetrics(@Param("id") long id, @Param("periodStart") LocalDate periodStart,
        @Param("periodEndExclusive") LocalDate periodEndExclusive);

    @ResultMap("account")
    @Select("SELECT * FROM accounts WHERE id = #{id}")
    Account findAccount(@Param("id") long id);

    @ResultMap("account")
    @Select("SELECT * FROM accounts WHERE id = #{id} FOR UPDATE")
    Account findAccountForUpdate(@Param("id") long id);

    @Select(value = """
        INSERT INTO accounts (name, type, sub_type, bank, account_no, opening_bank, currency,
            balance, available_balance, credit_limit, frozen_amount, include_in_net_worth,
            user_id, ledger_id, status, opened_at, last_reconciled_at, owner_name, purpose,
            reconciliation_status, risk_level, created_at, updated_at, company_id)
        VALUES (#{account.name}, #{account.type}, #{account.subType}, #{account.bank},
            #{account.accountNo}, #{account.openingBank}, #{account.currency},
            COALESCE(#{account.balance}, 0), COALESCE(#{account.availableBalance}, 0),
            COALESCE(#{account.creditLimit}, 0), COALESCE(#{account.frozenAmount}, 0),
            #{account.includeInNetWorth}, #{account.userId}, #{account.ledgerId}, #{account.status},
            CAST(NULLIF(#{account.openedAt}, '') AS date),
            CAST(NULLIF(#{account.lastReconciledAt}, '') AS date), #{account.ownerName},
            #{account.purpose}, #{account.reconciliationStatus}, #{account.riskLevel},
            CAST(#{account.createdAt} AS timestamptz), CAST(#{account.updatedAt} AS timestamptz),
            #{account.companyId}) RETURNING id
        """, affectData = true)
    Long insertAccount(@Param("account") Account account);

    @Update("""
        UPDATE accounts SET name = #{account.name}, type = #{account.type},
            sub_type = #{account.subType}, bank = #{account.bank}, account_no = #{account.accountNo},
            opening_bank = #{account.openingBank}, currency = #{account.currency},
            balance = COALESCE(#{account.balance}, 0),
            available_balance = COALESCE(#{account.availableBalance}, 0),
            credit_limit = COALESCE(#{account.creditLimit}, 0),
            frozen_amount = COALESCE(#{account.frozenAmount}, 0),
            include_in_net_worth = #{account.includeInNetWorth}, user_id = #{account.userId},
            ledger_id = #{account.ledgerId}, status = #{account.status},
            opened_at = CAST(NULLIF(#{account.openedAt}, '') AS date),
            last_reconciled_at = CAST(NULLIF(#{account.lastReconciledAt}, '') AS date),
            owner_name = #{account.ownerName}, purpose = #{account.purpose},
            reconciliation_status = #{account.reconciliationStatus}, risk_level = #{account.riskLevel},
            created_at = CAST(#{account.createdAt} AS timestamptz),
            updated_at = CAST(#{account.updatedAt} AS timestamptz),
            company_id = #{account.companyId}, version = version + 1
        WHERE id = #{account.id} AND version = #{account.version}
        """)
    int updateAccount(@Param("account") Account account);

    @Select("SELECT COUNT(*) FROM transactions WHERE account_id = #{accountId}")
    int countAccountTransactions(@Param("accountId") long accountId);

    @Delete("DELETE FROM accounts WHERE id = #{account.id} AND version = #{account.version}")
    int deleteAccount(@Param("account") Account account);

    @Results(id = "reconciliation", value = {})
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class), @Arg(column = "company_id", javaType = long.class),
        @Arg(column = "user_id", javaType = long.class), @Arg(column = "account_id", javaType = long.class),
        @Arg(column = "statement_date", javaType = String.class),
        @Arg(column = "statement_balance", javaType = BigDecimal.class),
        @Arg(column = "system_balance", javaType = BigDecimal.class),
        @Arg(column = "difference", javaType = BigDecimal.class),
        @Arg(column = "status", javaType = String.class), @Arg(column = "note", javaType = String.class),
        @Arg(column = "created_by", javaType = long.class),
        @Arg(column = "created_at", javaType = String.class)
    })
    @Select("""
        SELECT * FROM account_reconciliations WHERE account_id = #{accountId}
            AND company_id = #{companyId} AND user_id = #{userId}
        ORDER BY statement_date DESC, id DESC LIMIT #{limit}
        """)
    List<AccountReconciliation> findAccountReconciliations(@Param("accountId") long accountId,
        @Param("companyId") long companyId, @Param("userId") long userId, @Param("limit") int limit);

    @ResultMap("reconciliation")
    @Select(value = """
        INSERT INTO account_reconciliations (company_id, user_id, account_id, statement_date,
            statement_balance, system_balance, difference, status, note, created_by, created_at)
        VALUES (#{row.companyId}, #{row.userId}, #{row.accountId},
            CAST(#{row.statementDate} AS date), COALESCE(#{row.statementBalance}, 0),
            COALESCE(#{row.systemBalance}, 0), COALESCE(#{row.difference}, 0),
            #{row.status}, #{row.note}, #{row.createdBy}, CAST(#{row.createdAt} AS timestamptz))
        RETURNING *
        """, affectData = true)
    AccountReconciliation insertAccountReconciliation(@Param("row") AccountReconciliation row);

    @Results(id = "ledger", value = {
        @Result(column = "created_at", property = "createdAt", typeHandler = OffsetDateTimeStringTypeHandler.class),
        @Result(column = "updated_at", property = "updatedAt", typeHandler = OffsetDateTimeStringTypeHandler.class)
    })
    @Select("SELECT * FROM ledgers WHERE id = #{id}")
    Ledger findLedger(@Param("id") long id);

    @ResultMap("ledger")
    @Select("SELECT * FROM ledgers WHERE id = #{id} FOR UPDATE")
    Ledger findLedgerForUpdate(@Param("id") long id);

    @ResultMap("ledger")
    @Select("""
        SELECT DISTINCT ledger.* FROM ledgers ledger LEFT JOIN ledger_members member
            ON member.ledger_id = ledger.id AND member.company_id = ledger.company_id
            AND member.user_id = #{userId}
        WHERE ledger.company_id = #{companyId}
            AND (ledger.owner_id = #{userId} OR member.user_id IS NOT NULL)
        ORDER BY ledger.id
        """)
    List<Ledger> findAccessibleLedgers(@Param("userId") long userId, @Param("companyId") long companyId);

    @Select(value = """
        INSERT INTO ledgers (name, description, currency, owner_id, is_default, status,
            created_at, updated_at, company_id)
        VALUES (#{ledger.name}, #{ledger.description}, #{ledger.currency}, #{ledger.ownerId},
            #{ledger.isDefault}, #{ledger.status}, CAST(#{ledger.createdAt} AS timestamptz),
            CAST(#{ledger.updatedAt} AS timestamptz), #{ledger.companyId}) RETURNING id
        """, affectData = true)
    Long insertLedger(@Param("ledger") Ledger ledger);

    @ResultMap("ledger")
    @Select("SELECT * FROM ledgers WHERE company_id = #{companyId} ORDER BY is_default DESC, id")
    List<Ledger> findCompanyLedgers(@Param("companyId") long companyId);

    @ResultMap("ledger")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select(value = """
        INSERT INTO ledgers (name, description, currency, owner_id, is_default, status,
            created_at, updated_at, company_id)
        VALUES (#{ledger.name}, #{ledger.description}, #{ledger.currency}, #{ledger.ownerId},
            #{ledger.isDefault}, #{ledger.status}, CAST(#{ledger.createdAt} AS timestamptz),
            CAST(#{ledger.updatedAt} AS timestamptz), #{ledger.companyId})
        ON CONFLICT (company_id) WHERE is_default DO NOTHING RETURNING *
        """, affectData = true)
    Ledger insertDefaultLedgerIfAbsent(@Param("ledger") Ledger ledger);

    @Results(id = "ledgerMember", value = {
        @Result(column = "joined_at", property = "joinedAt", typeHandler = OffsetDateTimeStringTypeHandler.class)
    })
    @Select("SELECT * FROM ledger_members WHERE ledger_id = #{ledgerId} ORDER BY id")
    List<LedgerMember> findLedgerMembers(@Param("ledgerId") long ledgerId);

    @Select("SELECT COUNT(*) FROM ledger_members WHERE ledger_id = #{ledgerId} AND user_id = #{userId}")
    int countLedgerMembers(@Param("ledgerId") long ledgerId, @Param("userId") long userId);

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "avatar", javaType = String.class)
    })
    @Select("SELECT id, nickname, avatar FROM users WHERE id = #{userId}")
    MemberProfile findMemberProfile(@Param("userId") long userId);

    @Select(value = """
        INSERT INTO ledger_members (company_id, ledger_id, user_id, role, nickname, avatar, joined_at)
        VALUES (#{member.companyId}, #{member.ledgerId}, #{member.userId}, #{member.role},
            #{member.nickname}, #{member.avatar}, CAST(#{member.joinedAt} AS timestamptz)) RETURNING id
        """, affectData = true)
    Long insertLedgerMember(@Param("member") LedgerMember member);

    @Delete("DELETE FROM ledger_members WHERE ledger_id = #{ledgerId} AND user_id = #{userId}")
    int deleteLedgerMember(@Param("ledgerId") long ledgerId, @Param("userId") long userId);

    @Insert("""
        INSERT INTO ledger_members (company_id, ledger_id, user_id, role, nickname, avatar, joined_at)
        VALUES (#{member.companyId}, #{member.ledgerId}, #{member.userId}, #{member.role},
            #{member.nickname}, #{member.avatar}, CAST(#{member.joinedAt} AS timestamptz))
        ON CONFLICT (ledger_id, user_id) DO NOTHING
        """)
    int ensureLedgerMember(@Param("member") LedgerMember member);
}
