package com.catalyst.infrastructure.persistence;

import com.catalyst.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repositories, grouped for readability.
 */
public final class Repositories {
    private Repositories() {
    }

    public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {
    }

    public interface AccountRepository extends JpaRepository<AccountEntity, String> {
        List<AccountEntity> findByCustomerId(String customerId);

        Optional<AccountEntity> findByCustomerIdAndType(String customerId, AccountType type);

        /**
         * Atomic guarded debit: safe under concurrent executions (no lost updates).
         */
        @Transactional
        @Modifying(clearAutomatically = true)
        @Query("update AccountEntity a set a.balance = a.balance - :amt where a.id = :id and a.balance >= :amt")
        int debit(@Param("id") String id, @Param("amt") java.math.BigDecimal amt);

        @Transactional
        @Modifying(clearAutomatically = true)
        @Query("update AccountEntity a set a.balance = a.balance + :amt where a.id = :id")
        int credit(@Param("id") String id, @Param("amt") java.math.BigDecimal amt);
    }

    public interface ProductRepository extends JpaRepository<ProductEntity, String> {
    }

    public interface LiquidityEventRepository extends JpaRepository<LiquidityEventEntity, String> {
        Optional<LiquidityEventEntity> findByIdempotencyKey(String key);

        List<LiquidityEventEntity> findTop10ByCustomerIdOrderByReceivedAtDesc(String customerId);

        /**
         * Atomic compare-and-set used as the idempotency guard for consumers.
         */
        @Transactional
        @Modifying(clearAutomatically = true)
        @Query("update LiquidityEventEntity e set e.status = :to where e.id = :id and e.status = :from")
        int transition(@Param("id") String id, @Param("from") EventStatus from, @Param("to") EventStatus to);
    }

    public interface FinancialContextRepository extends JpaRepository<FinancialContextEntity, Long> {
        Optional<FinancialContextEntity> findFirstByEventIdOrderByIdDesc(String eventId);
    }

    public interface RecommendationRepository extends JpaRepository<RecommendationEntity, String> {
        Optional<RecommendationEntity> findByEventId(String eventId);

        @Transactional
        @Modifying(clearAutomatically = true)
        @Query("update RecommendationEntity r set r.status = :to, r.decidedAt = :now where r.id = :id and r.status in :from")
        int decide(@Param("id") String id, @Param("from") List<RecommendationStatus> from, @Param("to") RecommendationStatus to,
                   @Param("now") java.time.Instant now);

        default int decide(String id, List<RecommendationStatus> from, RecommendationStatus to) {
            return decide(id, from, to, java.time.Instant.now());
        }
    }

    public interface FinancialActionRepository extends JpaRepository<FinancialActionEntity, String> {
        List<FinancialActionEntity> findByRecommendationIdOrderByPriorityAsc(String recommendationId);

        @Transactional
        @Modifying(clearAutomatically = true)
        @Query("update FinancialActionEntity a set a.status = :to where a.id = :id and a.status = :from")
        int transition(@Param("id") String id, @Param("from") ActionStatus from, @Param("to") ActionStatus to);
    }

    public interface ConsentRepository extends JpaRepository<ConsentEntity, Long> {
        List<ConsentEntity> findByActionId(String actionId);
    }

    public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
        List<AuditEventEntity> findByEventIdOrderBySeqAsc(String eventId);
    }
}
