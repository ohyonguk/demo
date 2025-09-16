package com.example.demo.repository;

import com.example.demo.entity.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {
    List<PaymentLog> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    Optional<PaymentLog> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
    Optional<PaymentLog> findByTransactionId(String transactionId);
}