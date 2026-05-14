package com.bizmetrics.budget.infrastructure;

import com.bizmetrics.budget.domain.BudgetCapacity;
import com.bizmetrics.budget.domain.BudgetReservation;
import com.bizmetrics.budget.domain.BudgetReservationCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class BudgetReservationRepository {
    private final BudgetReservationMapper mapper;

    public BudgetReservationRepository(BudgetReservationMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<BudgetReservation> reserveMatching(BudgetReservationCommand command) {
        validate(command);
        BudgetReservationMapper.Candidate budget = mapper.findCandidate(command);
        if (budget == null) return Optional.empty();
        BigDecimal committed = mapper.committed(command, budget);
        BigDecimal reserved = mapper.reserved(budget.id());
        new BudgetCapacity(budget.amount(), nonNegative(committed), nonNegative(reserved))
            .reserve(command.amount());
        return Optional.ofNullable(mapper.insert(command, budget.id(), OffsetDateTime.now())).map(this::reservation);
    }

    public BudgetReservation confirm(long reservationId, long transactionId) {
        BudgetReservation current = requireForUpdate(reservationId);
        validateConfirmationTarget(current, transactionId);
        return update(current.confirm(transactionId, OffsetDateTime.now()));
    }

    public BudgetReservation release(long reservationId, String reason) {
        BudgetReservation current = requireForUpdate(reservationId);
        return update(current.release(reason, OffsetDateTime.now()));
    }

    public Optional<BudgetReservation> releaseByTransaction(long transactionId, String reason) {
        return Optional.ofNullable(mapper.findConfirmedByTransaction(transactionId))
            .map(this::reservation)
            .map(current -> update(current.release(reason, OffsetDateTime.now())));
    }

    public Optional<BudgetReservation> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::reservation);
    }

    private BudgetReservation requireForUpdate(long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id)).map(this::reservation)
            .orElseThrow(() -> new IllegalArgumentException("Budget reservation not found: " + id));
    }

    private void validateConfirmationTarget(BudgetReservation reservation, long transactionId) {
        BudgetReservationMapper.Target target = mapper.findConfirmationTarget(transactionId);
        if (target == null) {
            throw new IllegalArgumentException("Transaction not found for budget confirmation: " + transactionId);
        }
        if (target.companyId() != reservation.companyId()
            || !Objects.equals(target.budgetId(), reservation.budgetId())
            || target.type() != 2
            || target.amount().compareTo(reservation.amount()) != 0) {
            throw new IllegalStateException("Transaction does not match budget reservation: " + reservation.id());
        }
    }

    private BudgetReservation update(BudgetReservation reservation) {
        if (mapper.update(reservation, reservation.status().value()) != 1) {
            throw new OptimisticLockingFailureException(
                "Budget reservation was changed by another request: " + reservation.id());
        }
        return new BudgetReservation(reservation.id(), reservation.version() + 1,
            reservation.companyId(), reservation.budgetId(), reservation.transactionId(),
            reservation.referenceKey(), reservation.amount(), reservation.status(),
            reservation.createdByUserId(), reservation.confirmedAt(), reservation.releasedAt(),
            reservation.releaseReason(), reservation.createdAt(), reservation.updatedAt());
    }

    private BudgetReservation reservation(BudgetReservationMapper.Row row) {
        return new BudgetReservation(row.id(), row.version(), row.companyId(), row.budgetId(),
            row.transactionId(), row.referenceKey(), row.amount(),
            BudgetReservation.Status.fromStored(row.status()), row.createdByUserId(),
            row.confirmedAt(), row.releasedAt(), row.releaseReason(), row.createdAt(), row.updatedAt());
    }

    private void validate(BudgetReservationCommand command) {
        if (command.companyId() <= 0 || command.userId() <= 0 || command.categoryId() <= 0) {
            throw new IllegalArgumentException("Company, user, and category are required for a budget reservation");
        }
        if (command.transactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required for a budget reservation");
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("Budget reservation amount must be positive");
        }
        if (command.referenceKey() == null || command.referenceKey().isBlank() || command.referenceKey().length() > 160) {
            throw new IllegalArgumentException("Budget reservation reference key is invalid");
        }
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
