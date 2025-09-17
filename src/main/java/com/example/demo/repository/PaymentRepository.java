package com.example.demo.repository;

import com.example.demo.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    List<Payment> findByOrderNo(String orderNo);

    // 주문번호로 가장 최신 결제 내역 조회 (최신순)
    @Query("SELECT p FROM Payment p WHERE p.orderNo = :orderNo " +
           "ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findByOrderNoOrderByPaymentDateDesc(@Param("orderNo") String orderNo);

    // 주문번호로 완료된 결제만 조회
    @Query("SELECT p FROM Payment p WHERE p.orderNo = :orderNo AND p.status = 'COMPLETED' " +
           "ORDER BY p.paymentDate DESC")
    List<Payment> findByOrderNoAndStatusCompleted(@Param("orderNo") String orderNo);

    // TID로 가장 최신 결제 내역 조회
    @Query("SELECT p FROM Payment p WHERE p.tid = :tid ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findByTidOrderByPaymentDateDesc(@Param("tid") String tid);

    // 기존 메서드는 deprecated, 대신 findByTidOrderByPaymentDateDesc 사용 권장
    @Deprecated
    default Optional<Payment> findByTid(String tid) {
        List<Payment> payments = findByTidOrderByPaymentDateDesc(tid);
        return payments.isEmpty() ? Optional.empty() : Optional.of(payments.get(0));
    }

    boolean existsByOrderNo(String orderNo);
    
    // 사용자별 결제 내역 조회 (Order 테이블과 조인)
    @Query("SELECT p FROM Payment p " +
           "JOIN Order o ON p.orderNo = o.orderNo " +
           "WHERE o.userId = :userId " +
           "ORDER BY p.paymentDate DESC")
    List<Payment> findByUserIdOrderByPaymentDateDesc(@Param("userId") Long userId);
    
    // TID와 상태로 가장 최신 결제 내역 조회 (취소용)
    @Query("SELECT p FROM Payment p WHERE p.tid = :tid AND p.status = :status " +
           "ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findByTidAndStatusOrderByPaymentDateDesc(@Param("tid") String tid, @Param("status") String status);

    // 기존 메서드는 deprecated, 대신 findByTidAndStatusOrderByPaymentDateDesc 사용 권장
    @Deprecated
    default Optional<Payment> findByTidAndStatus(String tid, String status) {
        List<Payment> payments = findByTidAndStatusOrderByPaymentDateDesc(tid, status);
        return payments.isEmpty() ? Optional.empty() : Optional.of(payments.get(0));
    }

    // 주문번호와 TID로 결제 내역 조회 (중복 결제 확인용)
    @Query("SELECT p FROM Payment p WHERE p.orderNo = :orderNo AND p.tid = :tid " +
           "ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findByOrderNoAndTidOrderByPaymentDateDesc(@Param("orderNo") String orderNo, @Param("tid") String tid);

    default Optional<Payment> findByOrderNoAndTid(String orderNo, String tid) {
        List<Payment> payments = findByOrderNoAndTidOrderByPaymentDateDesc(orderNo, tid);
        return payments.isEmpty() ? Optional.empty() : Optional.of(payments.get(0));
    }
}