package com.example.demo.controller;

import com.example.demo.entity.Payment;
import com.example.demo.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/order")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    @Autowired
    private PaymentService paymentService;
    
    @GetMapping("/complete")
    public String orderComplete(@RequestParam String orderNo, Model model) {
        logger.info("Order complete page requested for order: {}", orderNo);
        
        try {
            Optional<Payment> payment = paymentService.getPaymentByOrderNo(orderNo);
            
            if (payment.isPresent() && "SUCCESS".equals(payment.get().getStatus())) {
                model.addAttribute("payment", payment.get());
                model.addAttribute("orderNo", orderNo);
                model.addAttribute("message", "주문이 성공적으로 완료되었습니다.");
                logger.info("Order completion confirmed for order: {}", orderNo);
                return "order/complete";
            } else {
                logger.warn("Payment not found or not successful for order: {}", orderNo);
                model.addAttribute("orderNo", orderNo);
                model.addAttribute("message", "결제 정보를 찾을 수 없거나 결제가 완료되지 않았습니다.");
                return "order/failed";
            }
            
        } catch (Exception e) {
            logger.error("Error loading order complete page for order: {}", orderNo, e);
            model.addAttribute("orderNo", orderNo);
            model.addAttribute("message", "주문 정보를 불러오는 중 오류가 발생했습니다.");
            return "order/failed";
        }
    }
    
    @GetMapping("/failed")
    public String orderFailed(@RequestParam(required = false) String orderNo, 
                             @RequestParam(required = false) String message, 
                             Model model) {
        logger.info("Order failed page requested for order: {}", orderNo);
        
        model.addAttribute("orderNo", orderNo);
        model.addAttribute("message", 
            message != null ? message : "결제 처리 중 오류가 발생했습니다.");
        
        return "order/failed";
    }
    
    @GetMapping("/status")
    public String orderStatus(@RequestParam String orderNo, Model model) {
        logger.info("Order status page requested for order: {}", orderNo);
        
        try {
            Optional<Payment> payment = paymentService.getPaymentByOrderNo(orderNo);
            
            if (payment.isPresent()) {
                model.addAttribute("payment", payment.get());
                model.addAttribute("orderNo", orderNo);
                
                if ("SUCCESS".equals(payment.get().getStatus())) {
                    return "order/complete";
                } else {
                    model.addAttribute("message", payment.get().getResultMsg());
                    return "order/failed";
                }
            } else {
                model.addAttribute("orderNo", orderNo);
                model.addAttribute("message", "주문 정보를 찾을 수 없습니다.");
                return "order/failed";
            }
            
        } catch (Exception e) {
            logger.error("Error loading order status for order: {}", orderNo, e);
            model.addAttribute("orderNo", orderNo);
            model.addAttribute("message", "주문 상태를 확인하는 중 오류가 발생했습니다.");
            return "order/failed";
        }
    }

}