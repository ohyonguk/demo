package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_no", nullable = false)
    private String orderNo;
    
    @Column(name = "tid")
    private String tid;
    
    @Column(name = "amount", nullable = false)
    private Long amount;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "result_code")
    private String resultCode;
    
    @Column(name = "result_msg")
    private String resultMsg;
    
    @Column(name = "payment_date")
    private LocalDateTime paymentDate;
    
    @Column(name = "card_name")
    private String cardName;
    
    @Column(name = "card_code")
    private String cardCode;
    
    @Column(name = "appl_num")
    private String applNum;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "payment_type", nullable = false)
    private String paymentType; // CARD, POINT, CARD_REFUND, POINT_REFUND

    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }

    public enum PaymentType {
        CARD("카드결제"),
        POINT("적립금"),
        CARD_REFUND("카드취소"),
        POINT_REFUND("적립금취소");

        private final String description;

        PaymentType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public Payment() {}
    
    public Payment(String orderNo, String tid, Long amount, String status, String resultCode, String resultMsg, String paymentType) {
        this.orderNo = orderNo;
        this.tid = tid;
        this.amount = amount;
        this.status = status;
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
        this.paymentType = paymentType;
        this.paymentDate = LocalDateTime.now();
    }

    public Payment(String orderNo, String tid, Long amount, String status, String resultCode, String resultMsg) {
        this(orderNo, tid, amount, status, resultCode, resultMsg, PaymentType.CARD.name());
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    
    public String getTid() { return tid; }
    public void setTid(String tid) { this.tid = tid; }
    
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    
    public String getResultMsg() { return resultMsg; }
    public void setResultMsg(String resultMsg) { this.resultMsg = resultMsg; }
    
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    
    public String getCardCode() { return cardCode; }
    public void setCardCode(String cardCode) { this.cardCode = cardCode; }
    
    public String getApplNum() { return applNum; }
    public void setApplNum(String applNum) { this.applNum = applNum; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean getCanRefund() {
        return "COMPLETED".equals(this.status) && this.amount > 0;
    }
    
    public void setCanRefund(boolean canRefund) {
        // This is a computed property, setter for compatibility only
    }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
}