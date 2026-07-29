package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentErrorType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        val entity = if (payment.id == 0L) {
            PaymentEntity.from(payment)
        } else {
            paymentJpaRepository.findById(payment.id)
                .orElseThrow { CoreException(PaymentErrorType.PAYMENT_NOT_FOUND) }
                .apply { syncFrom(payment) }
        }
        return paymentJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Payment? =
        paymentJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByIdForUpdate(id: Long): Payment? =
        paymentJpaRepository.findByIdForUpdate(id)?.toDomain()

    override fun findLatestByOrderId(orderId: Long): Payment? =
        paymentJpaRepository.findFirstByOrderIdOrderByIdDesc(orderId)?.toDomain()

    override fun findByTransactionId(transactionId: String): Payment? =
        paymentJpaRepository.findByTransactionId(transactionId)?.toDomain()

    override fun findByTransactionIdForUpdate(transactionId: String): Payment? =
        paymentJpaRepository.findByTransactionIdForUpdate(transactionId)?.toDomain()

    override fun findAllByStatus(status: PaymentStatus): List<Payment> =
        paymentJpaRepository.findAllByStatus(status).map { it.toDomain() }
}
