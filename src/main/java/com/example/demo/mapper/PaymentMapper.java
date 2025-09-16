package com.example.demo.mapper;

import com.example.demo.dto.InicisResponseDto;
import com.example.demo.dto.PaymentResultDto;
import com.example.demo.entity.Payment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PaymentMapper {
    
    public PaymentResultDto toPaymentResultDto(InicisResponseDto inicisResponse) {
        PaymentResultDto result = new PaymentResultDto();
        
        result.setOrderNo(inicisResponse.getOid());
        result.setTid(inicisResponse.getTid());
        result.setAmount(parseAmount(inicisResponse.getPrice()));
        result.setStatus(inicisResponse.isSuccess() ? "SUCCESS" : "FAILED");
        result.setMessage(inicisResponse.getResultMsg());
        result.setPaymentDate(parsePaymentDate(inicisResponse.getApplDate(), inicisResponse.getApplTime()));
        result.setCardName(inicisResponse.getCardName());
        result.setApplNum(inicisResponse.getApplNum());
        
        String redirectUrl = inicisResponse.isSuccess() 
            ? "/order/complete?orderNo=" + inicisResponse.getOid()
            : "/order/failed?orderNo=" + inicisResponse.getOid();
        result.setRedirectUrl(redirectUrl);
        
        return result;
    }
    
    public Payment toPaymentEntity(InicisResponseDto inicisResponse) {
        Payment payment = new Payment();
        
        payment.setOrderNo(inicisResponse.getOid());
        payment.setTid(inicisResponse.getTid());
        payment.setAmount(parseAmount(inicisResponse.getPrice()));
        payment.setStatus(inicisResponse.isSuccess() ? "SUCCESS" : "FAILED");
        payment.setResultCode(inicisResponse.getResultCode());
        payment.setResultMsg(inicisResponse.getResultMsg());
        payment.setPaymentDate(parsePaymentDate(inicisResponse.getApplDate(), inicisResponse.getApplTime()));
        payment.setCardName(inicisResponse.getCardName());
        payment.setCardCode(inicisResponse.getCardCode());
        payment.setApplNum(inicisResponse.getApplNum());
        payment.setCreatedAt(LocalDateTime.now());
        
        return payment;
    }
    
    private Long parseAmount(String price) {
        try {
            return price != null ? Long.parseLong(price) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    public LocalDateTime parsePaymentDate(String applDate, String applTime) {
        try {
            if (applDate != null && applTime != null) {
                String dateTimeStr = applDate + applTime;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                return LocalDateTime.parse(dateTimeStr, formatter);
            }
        } catch (Exception e) {
            // 파싱 실패시 현재 시간 반환
        }
        return LocalDateTime.now();
    }
}