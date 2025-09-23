package com.example.demo.controller;

import com.example.demo.entity.Order;
import com.example.demo.repository.OrderRepository;
import com.example.demo.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 결제 관련 API를 처리하는 컨트롤러
 *
 * 주문 생성, 결제 처리, 환불, 망취소 등의 결제 관련 모든 기능을 제공합니다.
 * Inicis 및 NicePay 결제 게이트웨이와 연동됩니다.
 *
 * @author Generated with Claude Code
 * @version 1.0
 */
@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * 새로운 주문 생성
     *
     * 사용자 ID, 총 금액, 적립금 사용량, 카드 결제 금액을 받아 새로운 주문을 생성합니다.
     *
     * @param request 주문 생성 요청 정보 (userId, totalAmount, pointsUsed, cardAmount)
     * @return 생성된 주문 정보 또는 오류 메시지
     */
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

    /**
     * 결제 요청 로깅
     *
     * Inicis 결제 요청 정보를 로그 테이블에 저장합니다.
     *
     * @param request 로깅할 요청 정보 (orderNo, requestType, requestUrl, requestData)
     * @return 로깅 성공/실패 응답
     */
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

    /**
     * 결제 응답 처리
     *
     * PG사에서 전송되는 결제 응답을 처리합니다.
     * 결제 성공/실패에 따른 후속 처리를 수행합니다.
     *
     * @param params PG사에서 전송된 결제 응답 파라미터
     * @return 결제 처리 결과
     */
    @PostMapping("/response")
    public ResponseEntity<?> handlePaymentResponse(@RequestBody Map<String, Object> params) {
        System.out.println("=== /response 엔드포인트 호출됨 ===");
        System.out.println("호출 시간: " + java.time.LocalDateTime.now());
        System.out.println("요청 파라미터: " + params);

        Map<String, Object> result = paymentService.processPaymentResponse(params);
        System.out.println("결과: " + result);
        return ResponseEntity.ok(result);
    }

    /**
     * 주문번호로 결제 상태 조회
     *
     * 주문번호를 기반으로 해당 주문의 상태와 정보를 조회합니다.
     *
     * @param orderNo 조회할 주문번호
     * @return 주문 정보 (주문 ID, 상태, 금액 등) 또는 오류 메시지
     */
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

    /**
     * 사용자별 주문 목록 조회
     *
     * 특정 사용자의 모든 주문 내역을 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 주문 목록 또는 오류 메시지
     */
    @GetMapping("/orders/{userId}")
    public ResponseEntity<?> getOrdersByUserId(@PathVariable Long userId) {
        try {
            Map<String, Object> result = paymentService.getUserPaymentHistory(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 목록 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 주문 상세 정보 조회
     *
     * 주문번호를 기반으로 주문의 상세 정보와 결제 내역을 조회합니다.
     *
     * @param orderNo 조회할 주문번호
     * @return 주문 상세 정보 (결제 내역 포함) 또는 오류 메시지
     */
    @GetMapping("/order-detail/{orderNo}")
    public ResponseEntity<?> getOrderDetail(@PathVariable String orderNo) {
        try {
            Map<String, Object> result = paymentService.getOrderDetailWithPayments(orderNo);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("주문 상세 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 주문 전체 환불 처리
     *
     * 주문번호를 기반으로 해당 주문의 모든 결제를 환불 처리합니다.
     *
     * @param orderNo 환불할 주문번호
     * @param request 환불 요청 정보 (reason 포함)
     * @return 환불 처리 결과 또는 오류 메시지
     */
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

    /**
     * 적립금 환불 처리
     *
     * 주문번호를 기반으로 해당 주문에서 사용된 적립금을 환불 처리합니다.
     *
     * @param orderNo 적립금 환불할 주문번호
     * @param request 환불 요청 정보 (reason 포함)
     * @return 적립금 환불 처리 결과 또는 오류 메시지
     */
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

    /**
     * NicePay 결제 승인 처리
     *
     * NicePay 결제 게이트웨이를 통한 결제 승인을 처리합니다.
     *
     * @param request NicePay 승인 요청 정보
     * @return 승인 처리 결과 또는 오류 메시지
     */
    @PostMapping("/nicepay/approve")
    public ResponseEntity<?> approveNicePayPayment(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> result = paymentService.approveNicePayPayment(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("NicePay 승인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 망취소 처리
     *
     * 주문번호를 기반으로 망취소(Network Cancel)를 수행합니다.
     * PG사와의 통신 장애를 시뮬레이션하여 별도의 취소 기록을 생성하고
     * 주문 상태를 NETWORK_CANCELLED로 변경합니다.
     *
     * @param orderNo 망취소할 주문번호
     * @param request 망취소 요청 정보 (reason, clientIp)
     * @return 망취소 처리 결과 (성공/실패, 취소 금액, PG사 정보 등)
     */
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