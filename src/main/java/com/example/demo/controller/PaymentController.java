package com.example.demo.controller;

import com.example.demo.entity.Order;
import com.example.demo.repository.OrderRepository;
import com.example.demo.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long totalAmount = Long.valueOf(request.get("totalAmount").toString());
            Integer pointsUsed = Integer.valueOf(request.get("pointsUsed").toString());
            Long cardAmount = Long.valueOf(request.get("cardAmount").toString());
            Boolean isNetworkCancelTest = Boolean.valueOf(request.getOrDefault("isNetworkCancelTest", false).toString());

            Map<String, Object> result = paymentService.createOrder(userId, totalAmount, pointsUsed, cardAmount, isNetworkCancelTest);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/log-request")
    public ResponseEntity<?> logPaymentRequest(@RequestBody Map<String, Object> request) {
        try {
            String orderNo = (String) request.get("orderNo");
            String requestType = (String) request.get("requestType");
            String requestUrl = (String) request.get("requestUrl");
            Map<String, Object> requestData = (Map<String, Object>) request.get("requestData");

            paymentService.logInicisRequest(orderNo, requestType, requestUrl, requestData);

            return ResponseEntity.ok(Map.of("success", true, "message", "요청 로깅 완료"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "로깅 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/response")
    public ResponseEntity<?> handlePaymentResponse(@RequestBody Map<String, Object> params) {
        System.out.println("=== /response 엔드포인트 호출됨 ===");
        System.out.println("호출 시간: " + java.time.LocalDateTime.now());
        System.out.println("요청 파라미터: " + params);

        Map<String, Object> result = paymentService.processPaymentResponse(params);
        System.out.println("결과: " + result);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/order/{orderNo}")
    public ResponseEntity<?> getPaymentStatusByOrderNo(@PathVariable String orderNo) {
        try {
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
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

    @GetMapping("/orders/{userId}")
    public ResponseEntity<?> getOrdersByUserId(@PathVariable Long userId) {
        try {
            Map<String, Object> result = paymentService.getUserPaymentHistory(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 목록 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @GetMapping("/order-detail/{orderNo}")
    public ResponseEntity<?> getOrderDetail(@PathVariable String orderNo) {
        try {
            Map<String, Object> result = paymentService.getOrderDetailWithPayments(orderNo);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 상세 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/refund/order/{orderNo}")
    public ResponseEntity<?> refundOrder(@PathVariable String orderNo, @RequestBody Map<String, Object> request) {
        try {
            String reason = (String) request.get("reason");
            Map<String, Object> result = paymentService.refundPaymentByOrderNo(orderNo, reason, "127.0.0.1");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("환불 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/refund/points/{orderNo}")
    public ResponseEntity<?> refundPoints(@PathVariable String orderNo, @RequestBody Map<String, Object> request) {
        try {
            String reason = (String) request.get("reason");
            Map<String, Object> result = paymentService.refundPointsByOrderNo(orderNo, reason);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("적립금 환불 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/nicepay/approve")
    public ResponseEntity<?> approveNicePayPayment(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> result = paymentService.approveNicePayPayment(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("NicePay 승인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/network-cancel/{orderNo}")
    public ResponseEntity<?> networkCancel(@PathVariable String orderNo, @RequestBody Map<String, Object> request) {
        try {
            String reason = (String) request.getOrDefault("reason", "고객 요청");
            String clientIp = (String) request.getOrDefault("clientIp", "127.0.0.1");

            Map<String, Object> result = paymentService.performNetworkCancel(orderNo, reason, clientIp);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("망취소 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}