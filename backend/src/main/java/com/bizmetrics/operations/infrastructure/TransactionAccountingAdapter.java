package com.bizmetrics.operations.infrastructure;

import com.bizmetrics.finance.application.FinanceRepository;
import com.bizmetrics.finance.domain.Account;
import com.bizmetrics.finance.domain.Ledger;
import com.bizmetrics.operations.application.CategoryRepository;
import com.bizmetrics.operations.application.TransactionAccountingGateway;
import com.bizmetrics.operations.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapter coordinating transaction writes with finance and operations-owned persistence. */
@Repository
public class TransactionAccountingAdapter implements TransactionAccountingGateway {
    private final FinanceRepository financeRepository;
    private final CategoryRepository categoryRepository;

    public TransactionAccountingAdapter(
        FinanceRepository financeRepository,
        CategoryRepository categoryRepository
    ) {
        this.financeRepository = financeRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Optional<Account> findAccountForUpdate(long id) {
        return financeRepository.findAccountForUpdate(id);
    }

    @Override
    public Optional<Category> findCategoryForUpdate(long id) {
        return categoryRepository.findForUpdate(id);
    }

    @Override
    public Optional<Ledger> findLedger(long id) {
        return financeRepository.findLedger(id);
    }

    @Override
    public List<Ledger> findLedgers(long userId, long companyId) {
        return financeRepository.findAccessibleLedgers(userId, companyId);
    }

    @Override
    public boolean ledgerMemberExists(long ledgerId, long userId) {
        return financeRepository.ledgerMemberExists(ledgerId, userId);
    }

    @Override
    public Ledger ensureCompanyAccountingWorkspace(
        long ownerId,
        long companyId,
        String currency,
        String subjectName
    ) {
        return financeRepository.ensureAccountingLedger(ownerId, companyId, currency, subjectName);
    }

    @Override
    public void updateAccount(Account account) {
        financeRepository.updateAccount(account);
    }
}
