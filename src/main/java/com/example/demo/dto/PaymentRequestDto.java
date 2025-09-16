package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PaymentRequestDto {
    
    @NotBlank(message = "주문번호는 필수입니다")
    private String orderNo;
    
    @NotNull(message = "결제금액은 필수입니다")
    @Positive(message = "결제금액은 0보다 커야 합니다")
    private Long amount;
    
    @NotBlank(message = "상품명은 필수입니다")
    private String goodsName;
    
    @NotBlank(message = "구매자명은 필수입니다")
    private String buyerName;
    
    @NotBlank(message = "구매자 이메일은 필수입니다")
    private String buyerEmail;
    
    @NotBlank(message = "구매자 전화번호는 필수입니다")
    private String buyerTel;
    
    private String returnUrl;
    private String closeUrl;
    
    public PaymentRequestDto() {}
    
    public PaymentRequestDto(String orderNo, Long amount, String goodsName, 
                           String buyerName, String buyerEmail, String buyerTel,
                           String returnUrl, String closeUrl) {
        this.orderNo = orderNo;
        this.amount = amount;
        this.goodsName = goodsName;
        this.buyerName = buyerName;
        this.buyerEmail = buyerEmail;
        this.buyerTel = buyerTel;
        this.returnUrl = returnUrl;
        this.closeUrl = closeUrl;
    }
    
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    
    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }
    
    public String getBuyerTel() { return buyerTel; }
    public void setBuyerTel(String buyerTel) { this.buyerTel = buyerTel; }
    
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    
    public String getCloseUrl() { return closeUrl; }
    public void setCloseUrl(String closeUrl) { this.closeUrl = closeUrl; }
}