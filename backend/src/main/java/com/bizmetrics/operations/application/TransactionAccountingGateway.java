package com.bizmetrics.operations.application;

import com.bizmetrics.finance.domain.Account;
import com.bizmetrics.finance.domain.Ledger;
import com.bizmetrics.operations.domain.Category;
import java.util.List;
import java.util.Optional;

/** Accounting collaboration port required by transaction write use cases. */
public interface TransactionAccountingGateway {
    Optional<Account> findAccountForUpdate(long id);

    Optional<Category> findCategoryForUpdate(long id);

    Optional<Ledger> findLedger(long id);

    List<Ledger> findLedgers(long userId, long companyId);

    boolean ledgerMemberExists(long ledgerId, long userId);

    Ledger ensureCompanyAccountingWorkspace(long ownerId, long companyId, String currency, String subjectName);

    void updateAccount(Account account);
}
