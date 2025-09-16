package com.example.demo.repository;

import com.example.demo.entity.IfInisisLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IfInisisLogRepository extends JpaRepository<IfInisisLog, Long> {
    
    List<IfInisisLog> findByOrderNoOrderByCreatedAtDesc(String orderNo);
    
    List<IfInisisLog> findByTransactionIdOrderByCreatedAtDesc(String transactionId);
    
    Optional<IfInisisLog> findTopByOrderNoAndRequestTypeOrderByCreatedAtDesc(String orderNo, String requestType);
}