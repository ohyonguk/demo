package com.example.demo.controller;

import com.example.demo.entity.Order;
import com.example.demo.entity.PaymentLog;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentLogRepository;
import com.example.demo.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class PaymentController {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentLogRepository paymentLogRepository;
    
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long totalAmount = Long.valueOf(request.get("totalAmount").toString());
            Integer pointsUsed = Integer.valueOf(request.get("pointsUsed").toString());
            Long cardAmount = Long.valueOf(request.get("cardAmount").toString());
            
            Map<String, Object> result = paymentService.createOrder(userId, totalAmount, pointsUsed, cardAmount);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @PostMapping("/notify") 
    public ResponseEntity<String> handlePaymentNotify(@RequestBody Map<String, Object> params) {
        System.out.println("Payment Notify Params: " + params);
        String result = paymentService.processPaymentNotify(params);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/response")
    public ResponseEntity<?> handlePaymentResponse(@RequestBody Map<String, Object> params) {
        System.out.println("=== /response 엔드포인트 호출됨 ===");
        System.out.println("호출 시간: " + java.time.LocalDateTime.now());
        System.out.println("요청 파라미터: " + params);
        System.out.println("파라미터 키들: " + params.keySet());
        System.out.println("파라미터 개수: " + params.size());
        
        // Stack trace로 호출 경로 확인
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        System.out.println("호출 스택:");
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            System.out.println("  " + stackTrace[i]);
        }
        
        Map<String, Object> result = paymentService.processPaymentResponse(params);
        System.out.println("결과: " + result);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/approve/{orderId}")
    public ResponseEntity<?> approvePayment(@PathVariable Long orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("주문을 찾을 수 없습니다.");
            }
            
            Order order = orderOpt.get();
            
            if (order.getStatus() != Order.OrderStatus.PENDING_APPROVAL) {
                return ResponseEntity.badRequest().body("승인 대기 상태가 아닙니다. 현재 상태: " + order.getStatus());
            }
            
            order.setStatus(Order.OrderStatus.APPROVED);
            orderRepository.save(order);
            
            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "결제가 승인되었습니다.",
                "orderNo", order.getOrderNo(),
                "orderId", order.getId()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("승인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @PostMapping("/complete/{orderId}")
    public ResponseEntity<?> completeOrder(@PathVariable Long orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("주문을 찾을 수 없습니다.");
            }
            
            Order order = orderOpt.get();
            
            if (order.getStatus() != Order.OrderStatus.APPROVED) {
                return ResponseEntity.badRequest().body("승인된 상태가 아닙니다. 현재 상태: " + order.getStatus());
            }
            
            order.setStatus(Order.OrderStatus.COMPLETED);
            orderRepository.save(order);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "주문이 완료되었습니다.",
                "orderNo", order.getOrderNo(),
                "orderId", order.getId()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 완료 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @GetMapping("/status/{orderId}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable Long orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("주문을 찾을 수 없습니다.");
            }
            
            Order order = orderOpt.get();
            
            return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "status", order.getStatus().toString(),
                "totalAmount", order.getTotalAmount(),
                "cardAmount", order.getCardAmount(),
                "pointsUsed", order.getPointsUsed(),
                "createdAt", order.getCreatedAt(),
                "updatedAt", order.getUpdatedAt()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 상태 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @GetMapping("/status/order/{orderNo}")
    public ResponseEntity<?> getPaymentStatusByOrderNo(@PathVariable String orderNo) {
        try {
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "주문을 찾을 수 없습니다: " + orderNo
                ));
            }
            
            Order order = orderOpt.get();
            
            // PaymentLog 정보도 함께 조회
            Optional<PaymentLog> paymentLogOpt = paymentLogRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("status", order.getStatus().toString());
            result.put("statusMessage", getStatusMessage(order.getStatus()));
            result.put("totalAmount", order.getTotalAmount());
            result.put("cardAmount", order.getCardAmount());
            result.put("pointsUsed", order.getPointsUsed());
            result.put("createdAt", order.getCreatedAt());
            result.put("updatedAt", order.getUpdatedAt());
            result.put("paymentInfo", paymentLogOpt.map(log -> {
                Map<String, Object> paymentInfo = new HashMap<>();
                paymentInfo.put("tid", log.getTransactionId() != null ? log.getTransactionId() : "");
                paymentInfo.put("resultCode", log.getResultCode() != null ? log.getResultCode() : "");
                paymentInfo.put("resultMessage", log.getResultMessage() != null ? log.getResultMessage() : "");
                paymentInfo.put("approvedAt", log.getApprovedAt() != null ? log.getApprovedAt() : null);
                return paymentInfo;
            }).orElse(new HashMap<>()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "주문 상태 조회 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/history/{userId}")
    public ResponseEntity<?> getPaymentHistory(@PathVariable Long userId) {
        try {
            Map<String, Object> result = paymentService.getUserPaymentHistory(userId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "결제 내역 조회 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/orders/{userId}")
    public ResponseEntity<?> getUserOrders(@PathVariable Long userId) {
        try {
            Map<String, Object> result = paymentService.getUserOrdersWithPayments(userId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "주문 내역 조회 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/order-detail/{orderNo}")
    public ResponseEntity<?> getOrderDetail(@PathVariable String orderNo) {
        try {
            Map<String, Object> result = paymentService.getOrderDetailWithPayments(orderNo);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "주문 상세 조회 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/refund")
    public ResponseEntity<?> refundPayment(@RequestBody Map<String, Object> request) {
        try {
            String tid = (String) request.get("tid");
            String refundReason = (String) request.get("reason");
            String clientIp = (String) request.get("clientIp");
            
            if (tid == null || tid.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "취소할 거래 ID(TID)가 필요합니다."
                ));
            }
            
            if (refundReason == null || refundReason.trim().isEmpty()) {
                refundReason = "고객 요청";
            }
            
            if (clientIp == null || clientIp.trim().isEmpty()) {
                clientIp = "127.0.0.1"; // 기본값
            }
            
            Map<String, Object> result = paymentService.refundPayment(tid, refundReason, clientIp);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "결제 취소 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/refund/order/{orderNo}")
    public ResponseEntity<?> refundPaymentByOrder(@PathVariable String orderNo, @RequestBody Map<String, Object> request) {
        try {
            String refundReason = (String) request.get("reason");
            String clientIp = (String) request.get("clientIp");
            
            if (refundReason == null || refundReason.trim().isEmpty()) {
                refundReason = "고객 요청";
            }
            
            if (clientIp == null || clientIp.trim().isEmpty()) {
                clientIp = "127.0.0.1"; // 기본값
            }
            
            Map<String, Object> result = paymentService.refundPaymentByOrderNo(orderNo, refundReason, clientIp);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "결제 취소 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/debug/counts")
    public ResponseEntity<?> getDebugCounts() {
        try {
            Map<String, Object> counts = new HashMap<>();
            counts.put("orders", orderRepository.count());
            counts.put("paymentLogs", paymentLogRepository.count());
            
            // Get latest orders
            counts.put("latestOrders", orderRepository.findAll().stream()
                .limit(5)
                .map(order -> Map.of(
                    "orderId", order.getId(),
                    "orderNo", order.getOrderNo(),
                    "status", order.getStatus().toString(),
                    "userId", order.getUserId(),
                    "totalAmount", order.getTotalAmount()
                ))
                .toList());
                
            // Get latest payment logs
            counts.put("latestPaymentLogs", paymentLogRepository.findAll().stream()
                .limit(5)
                .map(log -> Map.of(
                    "id", log.getId(),
                    "orderId", log.getOrderId(),
                    "tid", log.getTransactionId(),
                    "resultCode", log.getResultCode(),
                    "status", log.getStatus().toString()
                ))
                .toList());
                
            return ResponseEntity.ok(counts);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("디버그 정보 조회 중 오류: " + e.getMessage());
        }
    }

    @PostMapping("/refund/points/{orderNo}")
    public ResponseEntity<?> refundPointsByOrder(@PathVariable String orderNo, @RequestBody Map<String, Object> request) {
        try {
            String refundReason = (String) request.get("reason");

            if (refundReason == null || refundReason.trim().isEmpty()) {
                refundReason = "고객 요청";
            }

            Map<String, Object> result = paymentService.refundPointsByOrderNo(orderNo, refundReason);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "적립금 취소 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }

    private String getStatusMessage(Order.OrderStatus status) {
        switch (status) {
            case PENDING: return "결제 대기 중";
            case PENDING_APPROVAL: return "승인 대기 중";
            case APPROVED: return "승인 완료";
            case COMPLETED: return "결제 완료";
            case CANCELLED: return "결제 취소";
            case FAILED: return "결제 실패";
            default: return "알 수 없는 상태";
        }
    }
}