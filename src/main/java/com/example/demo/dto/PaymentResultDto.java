package com.example.demo.dto;

import java.time.LocalDateTime;

public class PaymentResultDto {
    
    private String orderNo;
    private String tid;
    private Long amount;
    private String status;
    private String message;
    private LocalDateTime paymentDate;
    private String cardName;
    private String applNum;
    private String redirectUrl;
    
    public PaymentResultDto() {}
    
    public PaymentResultDto(String orderNo, String tid, Long amount, String status, 
                          String message, LocalDateTime paymentDate, String cardName, 
                          String applNum, String redirectUrl) {
        this.orderNo = orderNo;
        this.tid = tid;
        this.amount = amount;
        this.status = status;
        this.message = message;
        this.paymentDate = paymentDate;
        this.cardName = cardName;
        this.applNum = applNum;
        this.redirectUrl = redirectUrl;
    }
    
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    
    public String getTid() { return tid; }
    public void setTid(String tid) { this.tid = tid; }
    
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    
    public String getApplNum() { return applNum; }
    public void setApplNum(String applNum) { this.applNum = applNum; }
    
    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }
}