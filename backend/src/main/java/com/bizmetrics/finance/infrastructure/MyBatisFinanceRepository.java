package com.bizmetrics.finance.infrastructure;

import com.bizmetrics.finance.application.FinanceRepository;
import com.bizmetrics.finance.domain.Account;
import com.bizmetrics.finance.domain.AccountReconciliation;
import com.bizmetrics.finance.domain.Ledger;
import com.bizmetrics.finance.domain.LedgerMember;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis adapter for finance-owned account and ledger persistence. */
@Repository
public class MyBatisFinanceRepository implements FinanceRepository {
    private final FinanceMapper mapper;

    public MyBatisFinanceRepository(FinanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Account> findAccounts(long userId, long companyId) {
        return mapper.findAccounts(userId, companyId).stream().map(account -> normalize(account, false)).toList();
    }

    @Override
    public List<Account> findAccountsWithMetrics(long userId, long companyId,
        LocalDate periodStart, LocalDate periodEndExclusive) {
        return mapper.findAccountsWithMetrics(userId, companyId, periodStart, periodEndExclusive)
            .stream().map(account -> normalize(account, true)).toList();
    }

    @Override
    public Optional<Account> findAccountWithMetrics(long id, LocalDate periodStart,
        LocalDate periodEndExclusive) {
        return Optional.ofNullable(mapper.findAccountWithMetrics(id, periodStart, periodEndExclusive))
            .map(account -> normalize(account, true));
    }

    @Override
    public Optional<Account> findAccount(long id) {
        return Optional.ofNullable(mapper.findAccount(id)).map(account -> normalize(account, false));
    }

    @Override
    public Optional<Account> findAccountForUpdate(long id) {
        return Optional.ofNullable(mapper.findAccountForUpdate(id)).map(account -> normalize(account, false));
    }

    @Override
    public Account insertAccount(Account account) {
        account.version = 0;
        Long id = mapper.insertAccount(account);
        if (id == null) throw new IllegalStateException("Database did not return a generated account id");
        account.id = id;
        return account;
    }

    @Override
    public void updateAccount(Account account) {
        if (mapper.updateAccount(account) != 1) {
            throw new OptimisticLockingFailureException("Account was changed by another request: " + account.id);
        }
        account.version++;
    }

    @Override
    public boolean accountHasTransactions(long accountId) {
        return mapper.countAccountTransactions(accountId) > 0;
    }

    @Override
    public void deleteAccount(Account account) {
        if (mapper.deleteAccount(account) != 1) {
            throw new OptimisticLockingFailureException("Account was changed by another request: " + account.id);
        }
    }

    @Override
    public List<AccountReconciliation> findAccountReconciliations(long accountId,
        long companyId, long userId, int limit) {
        return mapper.findAccountReconciliations(accountId, companyId, userId, limit);
    }

    @Override
    public AccountReconciliation insertAccountReconciliation(AccountReconciliation reconciliation) {
        return mapper.insertAccountReconciliation(reconciliation);
    }

    @Override
    public Optional<Ledger> findLedger(long id) {
        return Optional.ofNullable(mapper.findLedger(id));
    }

    @Override
    public Optional<Ledger> findLedgerForUpdate(long id) {
        return Optional.ofNullable(mapper.findLedgerForUpdate(id));
    }

    @Override
    public List<Ledger> findAccessibleLedgers(long userId, long companyId) {
        return mapper.findAccessibleLedgers(userId, companyId);
    }

    @Override
    public Ledger insertLedger(Ledger ledger) {
        Long id = mapper.insertLedger(ledger);
        if (id == null) throw new IllegalStateException("Database did not return a generated ledger id");
        ledger.id = id;
        return ledger;
    }

    @Override
    @Transactional
    public Ledger ensureAccountingLedger(long ownerId, long companyId, String currency, String subjectName) {
        List<Ledger> ledgers = mapper.findCompanyLedgers(companyId);
        Ledger ledger = ledgers.stream()
            .filter(candidate -> candidate.isDefault)
            .min(Comparator.comparingLong(candidate -> candidate.id))
            .or(() -> ledgers.stream().min(Comparator.comparingLong(candidate -> candidate.id)))
            .orElseGet(() -> Optional.ofNullable(mapper.insertDefaultLedgerIfAbsent(
                newLedger(ownerId, companyId, currency, subjectName, true)))
                .orElseGet(() -> mapper.findCompanyLedgers(companyId).stream()
                    .min(Comparator.comparingLong(candidate -> candidate.id))
                    .orElseThrow(() -> new IllegalStateException("Default ledger conflict did not return a ledger"))));
        if (!ledgerMemberExists(ledger.id, ownerId)) {
            MemberProfile profile = findMemberProfile(ownerId).orElse(new MemberProfile(ownerId, null, null));
            String role = ledger.ownerId == ownerId ? "owner" : "admin";
            mapper.ensureLedgerMember(newMember(companyId, ledger.id, profile, role));
        }
        return ledger;
    }

    @Override
    public List<LedgerMember> findLedgerMembers(long ledgerId) {
        return mapper.findLedgerMembers(ledgerId);
    }

    @Override
    public boolean ledgerMemberExists(long ledgerId, long userId) {
        return mapper.countLedgerMembers(ledgerId, userId) > 0;
    }

    @Override
    public Optional<MemberProfile> findMemberProfile(long userId) {
        return Optional.ofNullable(mapper.findMemberProfile(userId));
    }

    @Override
    public LedgerMember insertLedgerMember(LedgerMember member) {
        Long id = mapper.insertLedgerMember(member);
        if (id == null) throw new IllegalStateException("Database did not return a generated ledger member id");
        member.id = id;
        return member;
    }

    @Override
    public boolean deleteLedgerMember(long ledgerId, long userId) {
        return mapper.deleteLedgerMember(ledgerId, userId) == 1;
    }

    private Account normalize(Account account, boolean withMetrics) {
        account.currency = textOr(account.currency, "CNY");
        account.reconciliationStatus = textOr(account.reconciliationStatus, "pending");
        account.riskLevel = textOr(account.riskLevel, "low");
        if (!withMetrics) {
            account.monthlyIncome = BigDecimal.ZERO;
            account.monthlyExpense = BigDecimal.ZERO;
            account.currentMonthNetFlow = BigDecimal.ZERO;
        } else {
            account.currentMonthNetFlow = account.monthlyIncome.subtract(account.monthlyExpense);
        }
        return account;
    }

    private Ledger newLedger(long ownerId, long companyId, String currency, String subjectName,
        boolean isDefault) {
        Ledger ledger = new Ledger();
        ledger.ownerId = ownerId;
        ledger.companyId = companyId;
        ledger.name = limited(textOr(subjectName, "经营主体") + "账本", 120);
        ledger.description = "主体默认经营账本";
        ledger.currency = textOr(currency, "CNY");
        ledger.isDefault = isDefault;
        ledger.status = 1;
        ledger.createdAt = OffsetDateTime.now().toString();
        ledger.updatedAt = ledger.createdAt;
        return ledger;
    }

    private LedgerMember newMember(long companyId, long ledgerId, MemberProfile profile, String role) {
        LedgerMember member = new LedgerMember();
        member.companyId = companyId;
        member.ledgerId = ledgerId;
        member.userId = profile.userId();
        member.nickname = profile.nickname();
        member.avatar = profile.avatar();
        member.role = role;
        member.joinedAt = OffsetDateTime.now().toString();
        return member;
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String limited(String value, int maxLength) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxLength) return value;
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}
