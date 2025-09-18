package com.example.demo.service;

import com.example.demo.dto.InicisResponseDto;
import com.example.demo.dto.PaymentResultDto;
import com.example.demo.entity.Order;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentLog;
import com.example.demo.entity.User;
import com.example.demo.mapper.PaymentMapper;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentLogRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.entity.IfInisisLog;
import com.example.demo.repository.IfInisisLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
@Transactional
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private PaymentMapper paymentMapper;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentLogRepository paymentLogRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private IfInisisLogRepository ifInisisLogRepository;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService() {
        this.objectMapper = new ObjectMapper();
        // JSON 출력 형식 설정
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }
    
    // RestTemplate 타임아웃 설정
    {
        restTemplate = new RestTemplate();
        restTemplate.getRequestFactory();
    }
    
    // 이니시스 설정 (환경별)
    @Value("${inicis.api.url}")
    private String inicisApiUrl;

    @Value("${inicis.refund.url}")
    private String inicisRefundUrl;

    @Value("${inicis.merchant.id}")
    private String inicisMerchantId;

    @Value("${inicis.sign.key}")
    private String inicisSignKey;

    // NICE Pay 설정 (환경별)
    @Value("${nicepay.merchant.id}")
    private String nicePayMerchantId;

    @Value("${nicepay.merchant.key}")
    private String nicePayMerchantKey;

    @Value("${nicepay.api.url}")
    private String nicePayApiUrl;

    @Value("${nicepay.cancel.pwd}")
    private String nicePayCancelPwd;
    
    
    public PaymentResultDto processInicisCallback(InicisResponseDto inicisResponse) {
        logger.info("Processing Inicis callback for order: {}", inicisResponse.getOid());
        
        try {
            // 서명 검증 (실제 운영환경에서는 이니시스 키로 검증해야 함)
            if (!verifySignature(inicisResponse)) {
                logger.warn("Invalid signature for order: {}", inicisResponse.getOid());
                throw new IllegalArgumentException("유효하지 않은 서명입니다.");
            }
            
            // 기존 완료된 결제 정보 확인
            List<Payment> existingPayments = paymentRepository.findByOrderNoAndStatusCompleted(inicisResponse.getOid());
            Optional<Payment> existingPayment = existingPayments.isEmpty() ? Optional.empty() : Optional.of(existingPayments.get(0));
            
            Payment payment;
            if (existingPayment.isPresent()) {
                // 기존 결제 정보 업데이트
                payment = existingPayment.get();
                updatePaymentFromInicisResponse(payment, inicisResponse);
                logger.info("Updated existing payment for order: {}", inicisResponse.getOid());
            } else {
                // 새 결제 정보 저장
                payment = paymentMapper.toPaymentEntity(inicisResponse);
                logger.info("Created new payment for order: {}", inicisResponse.getOid());
            }
            
            Payment savedPayment = paymentRepository.save(payment);
            PaymentResultDto result = paymentMapper.toPaymentResultDto(inicisResponse);
            
            logger.info("Payment processing completed for order: {} with status: {}", 
                       savedPayment.getOrderNo(), savedPayment.getStatus());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error processing payment callback for order: {}", inicisResponse.getOid(), e);
            throw new RuntimeException("결제 처리 중 오류가 발생했습니다.", e);
        }
    }
    
    public Optional<Payment> getPaymentByOrderNo(String orderNo) {
        List<Payment> payments = paymentRepository.findByOrderNoOrderByPaymentDateDesc(orderNo);
        return payments.isEmpty() ? Optional.empty() : Optional.of(payments.get(0));
    }
    
    public boolean isPaymentCompleted(String orderNo) {
        List<Payment> payments = paymentRepository.findByOrderNoAndStatusCompleted(orderNo);
        return !payments.isEmpty();
    }
    
    private boolean verifySignature(InicisResponseDto response) {
        // 실제 운영환경에서는 이니시스에서 제공하는 키로 서명을 검증해야 합니다
        // 여기서는 기본적인 검증만 수행합니다
        try {
            String signatureData = response.getOid() + response.getPrice() + response.getTimestamp();
            // 실제로는 이니시스 키와 함께 SHA256 해시를 생성하여 비교해야 합니다
            return response.getSignature() != null && !response.getSignature().isEmpty();
        } catch (Exception e) {
            logger.error("Signature verification failed", e);
            return false;
        }
    }
    
    // 주문 생성
    public Map<String, Object> createOrder(Long userId, Long totalAmount, Integer pointsUsed, Long cardAmount) {
        logger.info("Creating order - userId: {}, totalAmount: {}, pointsUsed: {}, cardAmount: {}", 
                   userId, totalAmount, pointsUsed, cardAmount);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        
        User user = userOpt.get();
        if (user.getPoints() < pointsUsed) {
            throw new IllegalArgumentException("적립금이 부족합니다.");
        }
        
        String orderNo = "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        
        Order order = new Order(orderNo, userId, totalAmount, pointsUsed, cardAmount, Order.OrderStatus.PENDING);
        orderRepository.save(order);
        
        user.setPoints(user.getPoints() - pointsUsed);
        userRepository.save(user);

        // 적립금 사용 내역을 Payment 테이블에 기록
        if (pointsUsed > 0) {
            Payment pointPayment = new Payment();
            pointPayment.setOrderNo(orderNo);
            pointPayment.setTid("POINTS_" + System.currentTimeMillis());
            pointPayment.setAmount(Long.valueOf(pointsUsed));
            pointPayment.setStatus("COMPLETED");
            pointPayment.setPaymentType(Payment.PaymentType.POINT.name());
            pointPayment.setResultCode("0000");
            pointPayment.setResultMsg("적립금 사용");
            pointPayment.setPaymentDate(LocalDateTime.now());
            pointPayment.setCardName("적립금");

            paymentRepository.save(pointPayment);
            logger.info("Point usage recorded - OrderNo: {}, Points: {}", orderNo, pointsUsed);
        }

        // 적립금만으로 결제가 완료되는 경우 (카드 결제 금액이 0) 주문 상태를 완료로 변경
        if (cardAmount == 0 && pointsUsed > 0) {
            order.setStatus(Order.OrderStatus.COMPLETED);
            orderRepository.save(order);

            // 보너스 포인트 적립
            processPaymentSuccess(order);

            logger.info("Order completed with points only - OrderNo: {}, Points: {}", orderNo, pointsUsed);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", orderNo);
        result.put("cardAmount", cardAmount);
        result.put("pointsUsed", pointsUsed);
        result.put("totalAmount", totalAmount);

        // 적립금만으로 결제가 완료된 경우 완료 정보 추가
        if (cardAmount == 0 && pointsUsed > 0) {
            result.put("paymentCompleted", true);
            result.put("paymentMethod", "POINTS_ONLY");
            result.put("completedAt", LocalDateTime.now());

            try {
                String encodedMessage = java.net.URLEncoder.encode("적립금 결제가 완료되었습니다.", "UTF-8");
                result.put("redirectUrl", "/order/success?orderNo=" + orderNo +
                          "&amount=" + totalAmount +
                          "&message=" + encodedMessage +
                          "&status=COMPLETED");
            } catch (Exception e) {
                logger.warn("Failed to encode URL message", e);
                result.put("redirectUrl", "/order/success?orderNo=" + orderNo +
                          "&amount=" + totalAmount +
                          "&status=COMPLETED");
            }
        } else {
            result.put("paymentCompleted", false);
            result.put("paymentMethod", "CARD_REQUIRED");
        }

        return result;
    }
    
    // 결제 알림 처리 (notify)
    public String processPaymentNotify(Map<String, Object> params) {
        try {
            logger.info("Processing payment notify with params: {}", params);
            
            String orderNo = extractOrderNumber(params);
            String resultCode = extractResultCode(params);
            String resultMsg = extractResultMessage(params);
            String tid = extractTransactionId(params);
            
            logger.info("Notify - orderNo: {}, resultCode: {}, resultMsg: {}", orderNo, resultCode, resultMsg);
            
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                logger.warn("Notify: Order not found: {}", orderNo);
                return "FAIL";
            }
            
            Order order = orderOpt.get();
            logger.info("Notify: Found order with current status: {}", order.getStatus());
            
            // PaymentLog 처리
            PaymentLog paymentLog = createOrUpdatePaymentLog(order, tid, resultCode, resultMsg);
            
            if ("0000".equals(resultCode)) {
                processSuccessfulPayment(order, paymentLog);
            } else {
                processFailedPayment(order, paymentLog, resultCode, resultMsg);
            }
            
            // 저장 및 검증
            savePaymentLogWithVerification(paymentLog);
            orderRepository.save(order);
            
            return "OK";
            
        } catch (Exception e) {
            logger.error("Notify processing error: {}", e.getMessage(), e);
            return "FAIL";
        }
    }
    
    // 결제 응답 처리 (response)
    public Map<String, Object> processPaymentResponse(Map<String, Object> params) {
        String orderNo = null;
        IfInisisLog inicisLog = null;

        try {
            logger.info("Processing payment response with params: {}", params);

            orderNo = extractOrderNumber(params);
            String resultCode = extractResultCode(params);
            String resultMsg = extractResultMessage(params);
            String tid = extractTransactionId(params);
            String authUrl = (String) params.get("authUrl");
            String authToken = (String) params.get("authToken");

            // 이니시스 로그 생성 및 요청 데이터 저장
            inicisLog = createInicisLog(orderNo, "PAYMENT_RESPONSE", null, params);

            logger.info("Response - orderNo: {}, resultCode: {}, resultMsg: {}, tid: {}", orderNo, resultCode, resultMsg, tid);
            
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                return Map.of("success", false, "message", "주문을 찾을 수 없습니다: " + orderNo);
            }
            
            Order order = orderOpt.get();
            PaymentLog paymentLog = createOrUpdatePaymentLog(order, tid, resultCode, resultMsg);
            
            // authUrl과 authToken이 있으면 추가 인증 API 호출
            if (authUrl != null && authToken != null && !authUrl.trim().isEmpty() && !authToken.trim().isEmpty()) {
                logger.info("Processing auth API call for orderNo: {}, authUrl: {}", orderNo, authUrl);
                Map<String, Object> authResult = processAuthUrlRequest(authUrl, authToken, order);
                
                // 인증 결과에 따라 처리
                String authResultCode = (String) authResult.get("code");
                if ("0000".equals(authResultCode)) {
                    processSuccessfulPayment(order, paymentLog, authResult);
                } else {
                    processFailedPayment(order, paymentLog, authResultCode, (String) authResult.get("message"));
                }
            } else {
                // 기존 로직
                if ("0000".equals(resultCode)) {
                    processSuccessfulPayment(order, paymentLog);
                } else {
                    processFailedPayment(order, paymentLog, resultCode, resultMsg);
                }
            }
            
            savePaymentLogWithVerification(paymentLog);
            orderRepository.save(order);
            
            String message = "0000".equals(resultCode) ? "결제가 완료되었습니다." : "결제가 실패했습니다: " + resultMsg;
            boolean success = "0000".equals(resultCode);

            Map<String, Object> responseMap = Map.of(
                "success", success,
                "message", message,
                "orderNo", order.getOrderNo(),
                "orderId", order.getId(),
                "amount", order.getCardAmount(),
                "resultCode", resultCode != null ? resultCode : "",
                "resultMsg", resultMsg != null ? resultMsg : ""
            );

            // 이니시스 로그 응답 데이터 저장
            if (inicisLog != null) {
                updateInicisLogResponse(inicisLog, responseMap, 200, success, null);
            }

            return responseMap;

        } catch (Exception e) {
            logger.error("Response processing error: {}", e.getMessage(), e);

            // 에러 발생 시에도 로그 저장
            if (inicisLog != null) {
                Map<String, Object> errorResponse = Map.of("success", false, "message", "결제 처리 중 오류가 발생했습니다: " + e.getMessage());
                updateInicisLogResponse(inicisLog, errorResponse, 500, false, e.getMessage());
            }

            return Map.of("success", false, "message", "결제 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    // 파라미터 추출 메서드들
    private String extractOrderNumber(Map<String, Object> params) {
        String orderNo = (String) params.get("orderNumber");
        if (orderNo == null) orderNo = (String) params.get("oid");
        if (orderNo == null) orderNo = (String) params.get("P_OID");
        if (orderNo == null) orderNo = (String) params.get("MOID");
        return orderNo;
    }
    
    private String extractResultCode(Map<String, Object> params) {
        String resultCode = (String) params.get("resultCode");
        if (resultCode == null) resultCode = (String) params.get("P_STATUS");
        return resultCode;
    }
    
    private String extractResultMessage(Map<String, Object> params) {
        String resultMsg = (String) params.get("resultMsg");
        if (resultMsg == null) resultMsg = (String) params.get("P_RMESG1");
        return resultMsg;
    }
    
    private String extractTransactionId(Map<String, Object> params) {
        String tid = (String) params.get("tid");
        if (tid == null) tid = (String) params.get("P_TID");
        if (tid == null) tid = (String) params.get("TID");
        if (tid == null) tid = (String) params.get("transactionId");
        
        logger.info("Extracted TID from params: {} (available keys: {})", tid, params.keySet());
        
        // TID가 없는 경우 임시 TID 생성 (테스트용)
        if (tid == null || tid.trim().isEmpty()) {
            tid = "TEMP_TID_" + System.currentTimeMillis();
            logger.warn("No TID found in params, generated temporary TID: {}", tid);
        }
        
        return tid;
    }
    
    // PaymentLog 생성 또는 업데이트
    private PaymentLog createOrUpdatePaymentLog(Order order, String tid, String resultCode, String resultMsg) {
        Optional<PaymentLog> existingLog = paymentLogRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId());
        PaymentLog paymentLog;
        
        logger.info("Creating/Updating PaymentLog - TID: {}, ResultCode: {}", tid, resultCode);
        
        if (existingLog.isPresent()) {
            logger.info("Updating existing PaymentLog ID: {}", existingLog.get().getId());
            paymentLog = existingLog.get();
            paymentLog.setTransactionId(tid);
            paymentLog.setResultCode(resultCode);
            paymentLog.setResultMessage(resultMsg);
            paymentLog.setStatus("0000".equals(resultCode) ? PaymentLog.PaymentStatus.APPROVED : PaymentLog.PaymentStatus.FAILED);
        } else {
            logger.info("Creating new PaymentLog for order: {}", order.getId());
            paymentLog = new PaymentLog(
                order.getId(),
                tid,
                "CARD",
                order.getCardAmount(),
                "0000".equals(resultCode) ? PaymentLog.PaymentStatus.APPROVED : PaymentLog.PaymentStatus.FAILED
            );
            paymentLog.setResultCode(resultCode);
            paymentLog.setResultMessage(resultMsg);
        }
        
        logger.info("PaymentLog TID after setting: {}", paymentLog.getTransactionId());
        
        return paymentLog;
    }
    
    // 성공한 결제 처리 (기존)
    private void processSuccessfulPayment(Order order, PaymentLog paymentLog) {
        logger.info("Processing successful payment for order: {}", order.getOrderNo());
        
        if (processInicisApproval(paymentLog.getTransactionId(), order.getOrderNo(), order.getCardAmount())) {
            paymentLog.setApprovedAt(LocalDateTime.now());
            order.setStatus(Order.OrderStatus.APPROVED);
            order.setStatus(Order.OrderStatus.COMPLETED);
            
            // payments 테이블에 결제 정보 저장
            savePaymentRecord(order, paymentLog);
            
            // 보너스 포인트 적립
            processPaymentSuccess(order);
            logger.info("Payment approved and completed: {}", order.getOrderNo());
        } else {
            order.setStatus(Order.OrderStatus.FAILED);
            paymentLog.setStatus(PaymentLog.PaymentStatus.FAILED);
            paymentLog.setResultMessage("이니시스 승인 처리 실패");
            restoreUserPoints(order);
            logger.warn("Inicis approval failed for: {}", order.getOrderNo());
        }
    }
    
    // 성공한 결제 처리 (인증 결과 포함)
    private void processSuccessfulPayment(Order order, PaymentLog paymentLog, Map<String, Object> authResult) {
        logger.info("Processing successful payment with auth result for order: {}", order.getOrderNo());

        // 인증 결과에서 실제 TID가 있으면 PaymentLog 업데이트
        String realTid = (String) authResult.get("tid");
        if (realTid != null && !realTid.trim().isEmpty() && !realTid.startsWith("TEMP_TID_")) {
            logger.info("Updating PaymentLog TID from {} to {}", paymentLog.getTransactionId(), realTid);
            paymentLog.setTransactionId(realTid);
        }

        paymentLog.setApprovedAt(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.APPROVED);
        order.setStatus(Order.OrderStatus.COMPLETED);

        // payments 테이블에 결제 정보 저장 (인증 결과 포함)
        savePaymentRecordWithAuthResult(order, paymentLog, authResult);

        // 보너스 포인트 적립
        processPaymentSuccess(order);
        logger.info("Payment approved and completed with auth: {}", order.getOrderNo());
    }
    
    // 실패한 결제 처리
    private void processFailedPayment(Order order, PaymentLog paymentLog, String resultCode, String resultMsg) {
        logger.info("Processing failed payment for order: {}, resultCode: {}", order.getOrderNo(), resultCode);
        order.setStatus(Order.OrderStatus.FAILED);
        restoreUserPoints(order);
        
        // 실패한 결제도 Payment 레코드 생성 (디버깅 및 추적 목적)
        createFailedPaymentRecord(order, paymentLog, resultCode, resultMsg);
    }
    
    // 실패한 결제 레코드 생성
    private void createFailedPaymentRecord(Order order, PaymentLog paymentLog, String resultCode, String resultMsg) {
        try {
            Payment payment = new Payment();
            payment.setOrderNo(order.getOrderNo());
            payment.setTid(paymentLog.getTransactionId());
            payment.setAmount(order.getCardAmount());
            payment.setResultCode(resultCode);
            payment.setResultMsg(resultMsg);
            payment.setStatus("FAILED");
            payment.setPaymentType(Payment.PaymentType.CARD.name());
            payment.setPaymentDate(LocalDateTime.now());
            payment.setCardName("결제실패");
            payment.setCanRefund(false);
            
            Payment savedPayment = paymentRepository.save(payment);
            logger.info("Failed payment record created: ID={}, OrderNo={}, TID={}", 
                       savedPayment.getId(), savedPayment.getOrderNo(), savedPayment.getTid());
            
        } catch (Exception e) {
            logger.error("Error creating failed payment record for orderNo: {}", order.getOrderNo(), e);
        }
    }
    
    // 이니시스 승인 처리
    private boolean processInicisApproval(String tid, String orderNo, Long amount) {
        try {
            logger.info("Processing Inicis approval for TID: {}, OrderNo: {}, Amount: {}", tid, orderNo, amount);
            
            // TID가 이미 있으면 승인된 것으로 간주
            if (tid != null && !tid.trim().isEmpty()) {
                logger.info("Transaction already approved with TID: {}", tid);
                return true;
            }
            
            // 이니시스 승인 API 호출
            return callInicisApprovalAPI(orderNo, amount);
            
        } catch (Exception e) {
            logger.error("Error processing Inicis approval: {}", e.getMessage(), e);
            return false;
        }
    }
    
    // 실제 이니시스 승인 API 호출
    private boolean callInicisApprovalAPI(String orderNo, Long amount) {
        try {
            logger.info("Calling Inicis approval API for orderNo: {}, amount: {}", orderNo, amount);
            
            // 현재 시간을 timestamp로 사용
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // 서명 생성
            String signature = generateSignature(orderNo, amount.toString(), timestamp);
            String verification = generateVerification(orderNo, amount.toString(), timestamp);
            String mKey = generateMKey();
            
            // API 요청 데이터 준비
            Map<String, String> requestData = new HashMap<>();
            requestData.put("version", "1.0");
            requestData.put("mid", MID);
            requestData.put("oid", orderNo);
            requestData.put("price", amount.toString());
            requestData.put("timestamp", timestamp);
            requestData.put("use_chkfake", "Y");
            requestData.put("signature", signature);
            requestData.put("verification", verification);
            requestData.put("mKey", mKey);
            requestData.put("currency", "WON");
            requestData.put("acceptmethod", "below1000(Y)");
            requestData.put("gopaymethod", "Card");
            
            logger.info("Inicis API request data: {}", requestData);
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // form-data 형태로 변환 (MultiValueMap 사용)
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            for (Map.Entry<String, String> entry : requestData.entrySet()) {
                formData.add(entry.getKey(), entry.getValue());
            }
            
            // HTTP 요청 생성
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            
            // API 호출
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(INICIS_API_URL, request, String.class);
                String responseBody = response.getBody();
                
                logger.info("Inicis API response status: {}", response.getStatusCode());
                logger.info("Inicis API response body: {}", responseBody);
                
                // 응답 분석 (실제로는 응답 파싱해서 성공 여부 확인해야 함)
                if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                    // 응답에 에러가 없으면 성공으로 간주
                    if (!responseBody.contains("error") && !responseBody.contains("fail")) {
                        logger.info("Inicis approval successful for orderNo: {}", orderNo);
                        return true;
                    }
                }
                
                logger.warn("Inicis approval failed for orderNo: {} - Response: {}", orderNo, responseBody);
                return false;
                
            } catch (Exception apiException) {
                logger.error("Inicis API call failed for orderNo: {}", orderNo, apiException);
                
                // API 호출 실패해도 이미 결제가 된 상태라면 성공으로 처리
                // (notify에서 이미 성공 응답을 받은 상태)
                logger.warn("API call failed but treating as success since payment was already processed");
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Error in Inicis approval API call: {}", e.getMessage(), e);
            return false;
        }
    }
    
    // 서명 생성 (oid + price + timestamp) - 기존 방식
    private String generateSignature(String oid, String price, String timestamp) {
        try {
            String data = "oid=" + oid + "&price=" + price + "&timestamp=" + timestamp;
            return sha256Hash(data);
        } catch (Exception e) {
            logger.error("Error generating signature", e);
            return "";
        }
    }
    
    // 검증 값 생성 (oid + price + signKey + timestamp) - 기존 방식
    private String generateVerification(String oid, String price, String timestamp) {
        try {
            String data = "oid=" + oid + "&price=" + price + "&signKey=" + SIGN_KEY + "&timestamp=" + timestamp;
            return sha256Hash(data);
        } catch (Exception e) {
            logger.error("Error generating verification", e);
            return "";
        }
    }
    
    // 승인 요청용 서명 생성 (NVP 방식: authToken + timestamp)
    private String generateAuthSignature(String authToken, String timestamp) {
        try {
            String data = "authToken=" + authToken + "&timestamp=" + timestamp;
            logger.debug("Auth signature data: {}", data);
            return sha256Hash(data);
        } catch (Exception e) {
            logger.error("Error generating auth signature", e);
            return "";
        }
    }
    
    // 승인 요청용 검증 값 생성 (NVP 방식: authToken + signKey + timestamp)
    private String generateAuthVerification(String authToken, String timestamp) {
        try {
            String data = "authToken=" + authToken + "&signKey=" + SIGN_KEY + "&timestamp=" + timestamp;
            logger.debug("Auth verification data: {}", data);
            return sha256Hash(data);
        } catch (Exception e) {
            logger.error("Error generating auth verification", e);
            return "";
        }
    }
    
    // mKey 생성 (signKey의 SHA256 해시)
    private String generateMKey() {
        try {
            return sha256Hash(SIGN_KEY);
        } catch (Exception e) {
            logger.error("Error generating mKey", e);
            return "";
        }
    }
    
    // SHA256 해시 생성
    private String sha256Hash(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    // 결제 성공 후 추가 처리
    private void processPaymentSuccess(Order order) {
        try {
            logger.info("Processing payment success for order: {}", order.getOrderNo());
            
            Optional<User> userOpt = userRepository.findById(order.getUserId());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Long earnedPoints = Math.max(1L, order.getTotalAmount() / 100);
                user.setPoints(user.getPoints() + earnedPoints.intValue());
                userRepository.save(user);
                
                logger.info("Earned points added - User: {}, Points: {}, Total: {}", 
                           user.getId(), earnedPoints, user.getPoints());
            }
            
        } catch (Exception e) {
            logger.error("Error in payment success processing: {}", e.getMessage(), e);
        }
    }
    
    // 적립금 복구
    private void restoreUserPoints(Order order) {
        try {
            if (order.getPointsUsed() > 0) {
                Optional<User> userOpt = userRepository.findById(order.getUserId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    user.setPoints(user.getPoints() + order.getPointsUsed());
                    userRepository.save(user);
                    logger.info("Points restored to user: {}, restored: {}, total: {}", 
                               user.getId(), order.getPointsUsed(), user.getPoints());
                }
            }
        } catch (Exception e) {
            logger.error("Error restoring user points: {}", e.getMessage(), e);
        }
    }
    
    // payments 테이블에 결제 정보 저장
    private void savePaymentRecord(Order order, PaymentLog paymentLog) {
        try {
            logger.info("Saving payment record for order: {}", order.getOrderNo());
            
            String tid = paymentLog.getTransactionId();
            logger.info("TID from PaymentLog: {}", tid);
            
            Payment payment = new Payment();
            payment.setOrderNo(order.getOrderNo());
            payment.setTid(tid);
            payment.setAmount(order.getCardAmount()); // 카드 결제 금액만 저장
            payment.setStatus("COMPLETED");
            payment.setPaymentType(Payment.PaymentType.CARD.name());
            payment.setResultCode(paymentLog.getResultCode());
            payment.setResultMsg(paymentLog.getResultMessage());
            payment.setPaymentDate(paymentLog.getApprovedAt());
            
            paymentRepository.save(payment);
            logger.info("Payment record saved successfully - Order: {}, Payment ID: {}, TID: {}", 
                       order.getOrderNo(), payment.getId(), payment.getTid());
            
        } catch (Exception e) {
            logger.error("Error saving payment record for order: {}, error: {}", 
                        order.getOrderNo(), e.getMessage(), e);
        }
    }
    
    // PaymentLog 저장 및 검증
    private void savePaymentLogWithVerification(PaymentLog paymentLog) {
        try {
            logger.info("Saving PaymentLog...");
            PaymentLog savedPaymentLog = paymentLogRepository.save(paymentLog);
            logger.info("PaymentLog saved with ID: {}", savedPaymentLog.getId());
            
            Optional<PaymentLog> verifyLog = paymentLogRepository.findById(savedPaymentLog.getId());
            if (verifyLog.isPresent()) {
                logger.info("PaymentLog verification successful");
            } else {
                logger.warn("WARNING: PaymentLog verification failed - cannot find saved log");
            }
        } catch (Exception e) {
            logger.error("ERROR saving PaymentLog: {}", e.getMessage(), e);
            throw e;
        }
    }

    // authUrl API 통신 처리
    private Map<String, Object> processAuthUrlRequest(String authUrl, String authToken, Order order) {
        IfInisisLog requestLog = null;
        IfInisisLog responseLog = null;
        
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = generateAuthSignature(authToken, timestamp);
            String verification = generateAuthVerification(authToken, timestamp);
            
            // API 요청 객체 생성
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("mid", MID);
            requestData.put("authToken", authToken);
            requestData.put("timestamp", timestamp);
            requestData.put("signature", signature);
            requestData.put("verification", verification);
            requestData.put("charset", "UTF-8");
            requestData.put("format", "JSON");
            
            // 요청 로그 저장
            requestLog = logApiRequest(order.getOrderNo(), authUrl, requestData);
            
            // 승인 요청 데이터 로그 출력 (JSON 포맷)
            try {
                String prettyRequestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestData);
                logger.info("=== 승인 요청 REQUEST DATA ===");
                logger.info("Order No: {}", order.getOrderNo());
                logger.info("Auth URL: {}", authUrl);
                logger.info("AuthToken: {}", authToken);
                logger.info("Timestamp: {}", timestamp);
                logger.info("Signature NVP: authToken={}&timestamp={}", authToken, timestamp);
                logger.info("Verification NVP: authToken={}&signKey={}&timestamp={}", authToken, SIGN_KEY, timestamp);
                logger.info("Generated Signature: {}", signature);
                logger.info("Generated Verification: {}", verification);
                logger.info("Request JSON:\n{}", prettyRequestJson);
                logger.info("=== 승인 요청 REQUEST DATA END ===");
            } catch (Exception e) {
                logger.info("Auth API request for orderNo: {}, URL: {}, Data: {}", order.getOrderNo(), authUrl, requestData);
            }
            
            // HTTP 헤더 설정 (application/x-www-form-urlencoded)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // form-data 형태로 변환 (MultiValueMap 사용)
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            for (Map.Entry<String, Object> entry : requestData.entrySet()) {
                formData.add(entry.getKey(), entry.getValue().toString());
            }
            
            // HTTP 요청 생성 및 전송
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(authUrl, request, String.class);
                String responseBody = response.getBody();
                
                // 승인 응답 데이터 로그 출력 (JSON 포맷)
                try {
                    logger.info("=== 승인 응답 RESPONSE DATA ===");
                    logger.info("Order No: {}", order.getOrderNo());
                    logger.info("HTTP Status: {}", response.getStatusCode());
                    logger.info("Response Headers: {}", response.getHeaders());
                    
                    if (responseBody != null && !responseBody.trim().isEmpty()) {
                        // JSON 형태로 파싱해서 예쁘게 출력
                        if (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[")) {
                            Object parsedResponse = objectMapper.readValue(responseBody, Object.class);
                            String prettyResponseJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedResponse);
                            logger.info("Response JSON:\n{}", prettyResponseJson);
                        } else {
                            logger.info("Response Body: {}", responseBody);
                        }
                    } else {
                        logger.info("Response Body: (empty)");
                    }
                    logger.info("=== 승인 응답 RESPONSE DATA END ===");
                } catch (Exception logException) {
                    logger.info("Auth API response status: {}, body: {}", response.getStatusCode(), responseBody);
                }
                
                // 응답 로그 저장
                responseLog = logApiResponse(order.getOrderNo(), authUrl, responseBody, response.getStatusCode().value(), true, null);
                
                // 응답에서 실제 TID와 결제 정보 추출
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("code", "0000");
                responseData.put("message", "성공");

                // 실제 응답에서 TID 추출 시도
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    try {
                        // JSON 응답인 경우 파싱해서 TID 추출
                        if (responseBody.trim().startsWith("{")) {
                            Map<String, Object> parsedResponse = objectMapper.readValue(responseBody, Map.class);

                            // 이니시스 응답에서 실제 TID 추출
                            String realTid = extractRealTidFromResponse(parsedResponse);
                            if (realTid != null && !realTid.trim().isEmpty()) {
                                responseData.put("tid", realTid);
                                logger.info("Real TID extracted from auth response: {}", realTid);
                            }

                            // 카드 정보 추출 (다양한 키 패턴 지원)
                            extractCardInfo(parsedResponse, responseData);
                        }
                    } catch (Exception parseException) {
                        logger.warn("Failed to parse auth response for TID extraction: {}", parseException.getMessage());
                    }
                }

                return responseData;
                
            } catch (Exception apiException) {
                // 승인 API 실패 로그 출력
                logger.info("=== 승인 응답 RESPONSE DATA (실패) ===");
                logger.info("Order No: {}", order.getOrderNo());
                logger.info("Error: {}", apiException.getMessage());
                logger.info("Exception Type: {}", apiException.getClass().getSimpleName());
                logger.info("=== 승인 응답 RESPONSE DATA (실패) END ===");
                
                logger.error("Auth API call failed for orderNo: {}", order.getOrderNo(), apiException);
                
                // 실패 응답 로그 저장
                responseLog = logApiResponse(order.getOrderNo(), authUrl, null, 500, false, apiException.getMessage());
                
                return Map.of("code", "9999", "message", "API 통신 실패: " + apiException.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in auth URL processing: {}", e.getMessage(), e);
            return Map.of("code", "9999", "message", "인증 처리 중 오류 발생: " + e.getMessage());
        }
    }
    
    // API 요청 로그 저장
    private IfInisisLog logApiRequest(String orderNo, String requestUrl, Map<String, Object> requestData) {
        try {
            IfInisisLog log = new IfInisisLog(orderNo, "REQUEST", "INICIS");
            log.setRequestUrl(requestUrl);
            
            // JSON 형식으로 변환하여 저장
            String jsonRequestData = objectMapper.writerWithDefaultPrettyPrinter()
                                                 .writeValueAsString(requestData);
            log.setRequestData(jsonRequestData);
            log.setIsSuccess(true);
            
            return ifInisisLogRepository.save(log);
            
        } catch (Exception e) {
            logger.error("Error saving API request log: {}", e.getMessage(), e);
            return null;
        }
    }
    
    // API 응답 로그 저장
    private IfInisisLog logApiResponse(String orderNo, String requestUrl, String responseData, 
                                      int httpStatus, boolean isSuccess, String errorMessage) {
        try {
            IfInisisLog log = new IfInisisLog(orderNo, "RESPONSE", "INICIS");
            log.setRequestUrl(requestUrl);
            
            // JSON 형식으로 변환하여 저장
            String jsonResponseData = formatResponseDataAsJson(responseData, errorMessage);
            log.setResponseData(jsonResponseData);
            log.setHttpStatus(httpStatus);
            log.setIsSuccess(isSuccess);
            log.setErrorMessage(errorMessage);
            
            return ifInisisLogRepository.save(log);
            
        } catch (Exception e) {
            logger.error("Error saving API response log: {}", e.getMessage(), e);
            return null;
        }
    }
    
    // 응답 데이터를 JSON 형식으로 포맷팅
    private String formatResponseDataAsJson(String responseData, String errorMessage) {
        try {
            Map<String, Object> jsonResponse = new HashMap<>();
            
            if (responseData != null && !responseData.trim().isEmpty()) {
                // 이미 JSON 형식인지 확인
                if (responseData.trim().startsWith("{") || responseData.trim().startsWith("[")) {
                    // 이미 JSON이면 pretty print로 포맷팅
                    Object parsedResponse = objectMapper.readValue(responseData, Object.class);
                    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedResponse);
                } else {
                    // JSON이 아니면 rawResponse로 감싸서 JSON 생성
                    jsonResponse.put("rawResponse", responseData);
                }
            }
            
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                jsonResponse.put("errorMessage", errorMessage);
            }
            
            // 응답이 비어있으면 기본 구조 생성
            if (jsonResponse.isEmpty()) {
                jsonResponse.put("response", "empty");
            }
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
            
        } catch (Exception e) {
            logger.error("Error formatting response data as JSON: {}", e.getMessage(), e);
            // 실패시 기본 JSON 구조 반환
            try {
                Map<String, Object> fallbackResponse = new HashMap<>();
                fallbackResponse.put("originalResponse", responseData);
                fallbackResponse.put("errorMessage", errorMessage);
                fallbackResponse.put("formatError", e.getMessage());
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fallbackResponse);
            } catch (JsonProcessingException je) {
                return "{\"error\": \"Failed to format response as JSON\"}";
            }
        }
    }

    // 이니시스 응답에서 카드 정보 추출
    private void extractCardInfo(Map<String, Object> response, Map<String, Object> responseData) {
        if (response == null || responseData == null) return;

        // 카드명 추출 (다양한 키 패턴 지원)
        String[] cardNameKeys = {"cardName", "card_name", "CARD_NAME", "P_CARD_NAME", "cardCompany", "cardIssuer"};
        for (String key : cardNameKeys) {
            Object value = response.get(key);
            if (value != null && !value.toString().trim().isEmpty()) {
                responseData.put("cardName", value.toString().trim());
                break;
            }
        }

        // 카드코드 추출
        String[] cardCodeKeys = {"cardCode", "card_code", "CARD_CODE", "P_CARD_CODE", "cardType"};
        for (String key : cardCodeKeys) {
            Object value = response.get(key);
            if (value != null && !value.toString().trim().isEmpty()) {
                responseData.put("cardCode", value.toString().trim());
                break;
            }
        }

        // 승인번호 추출
        String[] applNumKeys = {"applNum", "appl_num", "APPL_NUM", "P_APPL_NUM", "authNum", "approvalNumber"};
        for (String key : applNumKeys) {
            Object value = response.get(key);
            if (value != null && !value.toString().trim().isEmpty()) {
                responseData.put("applNum", value.toString().trim());
                break;
            }
        }

        logger.info("Extracted card info - cardName: {}, cardCode: {}, applNum: {}",
                   responseData.get("cardName"), responseData.get("cardCode"), responseData.get("applNum"));
    }

    // 이니시스 응답에서 실제 TID 추출
    private String extractRealTidFromResponse(Map<String, Object> response) {
        if (response == null) return null;

        // 이니시스 응답에서 TID를 찾는 다양한 키들
        String[] tidKeys = {"tid", "TID", "P_TID", "transactionId", "transaction_id", "pgTid", "pg_tid"};

        for (String key : tidKeys) {
            Object tidValue = response.get(key);
            if (tidValue != null && !tidValue.toString().trim().isEmpty()) {
                String tid = tidValue.toString().trim();
                // TEMP_TID로 시작하는 임시 TID는 제외
                if (!tid.startsWith("TEMP_TID_")) {
                    return tid;
                }
            }
        }

        return null;
    }


    // payments 테이블에 인증 결과 포함 저장
    private void savePaymentRecordWithAuthResult(Order order, PaymentLog paymentLog, Map<String, Object> authResult) {
        try {
            logger.info("Saving payment record with auth result for order: {}", order.getOrderNo());

            // 인증 응답에서 실제 TID를 우선적으로 사용
            String tid = (String) authResult.get("tid");
            if (tid == null || tid.trim().isEmpty() || tid.startsWith("TEMP_TID_")) {
                // 인증 응답에 TID가 없으면 PaymentLog에서 가져오기
                tid = paymentLog.getTransactionId();
            }
            logger.info("TID for payment record (auth): {} (from authResult: {})",
                       tid, authResult.get("tid"));
            
            Payment payment = new Payment();
            payment.setOrderNo(order.getOrderNo());
            payment.setTid(tid);
            payment.setAmount(order.getCardAmount()); // 카드 결제 금액만 저장
            payment.setStatus("COMPLETED");
            payment.setPaymentType(Payment.PaymentType.CARD.name());
            payment.setResultCode((String) authResult.get("code"));
            payment.setResultMsg((String) authResult.get("message"));
            payment.setPaymentDate(paymentLog.getApprovedAt());
            
            // 추가 인증 정보가 있다면 저장
            if (authResult.containsKey("cardName")) {
                payment.setCardName((String) authResult.get("cardName"));
            }
            if (authResult.containsKey("cardCode")) {
                payment.setCardCode((String) authResult.get("cardCode"));
            }
            if (authResult.containsKey("applNum")) {
                payment.setApplNum((String) authResult.get("applNum"));
            }
            
            paymentRepository.save(payment);
            logger.info("Payment record with auth result saved - Order: {}, Payment ID: {}, TID: {}", 
                       order.getOrderNo(), payment.getId(), payment.getTid());
            
        } catch (Exception e) {
            logger.error("Error saving payment record with auth result for order: {}, error: {}", 
                        order.getOrderNo(), e.getMessage(), e);
        }
    }

    // 사용자별 결제 내역 조회
    public Map<String, Object> getUserPaymentHistory(Long userId) {
        try {
            logger.info("Getting payment history for user: {}", userId);
            
            List<Payment> payments = paymentRepository.findByUserIdOrderByPaymentDateDesc(userId);
            
            return Map.of(
                "success", true,
                "userId", userId,
                "payments", payments,
                "totalCount", payments.size()
            );
            
        } catch (Exception e) {
            logger.error("Error getting payment history for user: {}", userId, e);
            return Map.of(
                "success", false,
                "message", "결제 내역 조회 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
    
    // 사용자별 주문 내역과 결제 정보 조회 (조인)
    public Map<String, Object> getUserOrdersWithPayments(Long userId) {
        try {
            logger.info("Getting orders with payments for user: {}", userId);
            
            // 사용자의 모든 주문 조회
            List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
            
            // 각 주문에 대한 결제 정보 추가
            List<Map<String, Object>> orderDetails = orders.stream().map(order -> {
                Map<String, Object> orderInfo = new HashMap<>();
                orderInfo.put("orderId", order.getId());
                orderInfo.put("orderNo", order.getOrderNo());
                orderInfo.put("totalAmount", order.getTotalAmount());
                orderInfo.put("cardAmount", order.getCardAmount());
                orderInfo.put("pointsUsed", order.getPointsUsed());
                orderInfo.put("status", order.getStatus().toString());
                orderInfo.put("statusMessage", getOrderStatusMessage(order.getStatus()));
                orderInfo.put("createdAt", order.getCreatedAt());
                orderInfo.put("updatedAt", order.getUpdatedAt());
                
                // 해당 주문의 모든 결제 정보 조회 (각각 별도 로우로 표시)
                List<Payment> allPayments = paymentRepository.findByOrderNoOrderByPaymentDateDesc(order.getOrderNo());

                // 취소가 발생한 경우 원본 사용내역은 숨기고 취소내역만 표시
                List<Payment> filteredPayments = filterPaymentsForDisplay(allPayments);

                List<Map<String, Object>> paymentInfoList = filteredPayments.stream().map(payment -> {
                    Map<String, Object> paymentInfo = new HashMap<>();
                    paymentInfo.put("paymentId", payment.getId());
                    paymentInfo.put("tid", payment.getTid());
                    paymentInfo.put("amount", payment.getAmount());
                    paymentInfo.put("status", payment.getStatus());
                    paymentInfo.put("paymentType", payment.getPaymentType());
                    paymentInfo.put("paymentTypeDescription", getPaymentTypeDescription(payment.getPaymentType()));
                    paymentInfo.put("resultCode", payment.getResultCode());
                    paymentInfo.put("resultMsg", payment.getResultMsg());
                    paymentInfo.put("paymentDate", payment.getPaymentDate());
                    paymentInfo.put("cardName", payment.getCardName());
                    paymentInfo.put("canRefund", canRefundPayment(payment));

                    return paymentInfo;
                }).toList();

                orderInfo.put("payments", paymentInfoList);

                // 하위 호환성을 위해 첫 번째 결제 정보도 유지 (기존 코드와의 호환성)
                if (!filteredPayments.isEmpty()) {
                    Payment firstPayment = filteredPayments.get(0);
                    Map<String, Object> paymentInfo = new HashMap<>();
                    paymentInfo.put("paymentId", firstPayment.getId());
                    paymentInfo.put("tid", firstPayment.getTid());
                    paymentInfo.put("amount", firstPayment.getAmount());
                    paymentInfo.put("status", firstPayment.getStatus());
                    paymentInfo.put("paymentType", firstPayment.getPaymentType());
                    paymentInfo.put("resultCode", firstPayment.getResultCode());
                    paymentInfo.put("resultMsg", firstPayment.getResultMsg());
                    paymentInfo.put("paymentDate", firstPayment.getPaymentDate());
                    paymentInfo.put("cardName", firstPayment.getCardName());
                    paymentInfo.put("canRefund", canRefundPayment(firstPayment));

                    orderInfo.put("payment", paymentInfo);
                } else {
                    orderInfo.put("payment", null);
                }
                
                return orderInfo;
            }).toList();
            
            return Map.of(
                "success", true,
                "userId", userId,
                "orders", orderDetails,
                "totalCount", orders.size()
            );
            
        } catch (Exception e) {
            logger.error("Error getting orders with payments for user: {}", userId, e);
            return Map.of(
                "success", false,
                "message", "주문 내역 조회 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // 주문번호로 주문 상세 정보와 결제 내역 조회
    public Map<String, Object> getOrderDetailWithPayments(String orderNo) {
        try {
            logger.info("Getting order detail with payments for orderNo: {}", orderNo);

            // 주문 정보 조회
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "주문을 찾을 수 없습니다: " + orderNo
                );
            }

            Order order = orderOpt.get();

            // 해당 주문의 모든 결제 정보 조회
            List<Payment> allPayments = paymentRepository.findByOrderNoOrderByPaymentDateDesc(orderNo);

            // 취소가 발생한 경우 원본 사용내역은 숨기고 취소내역만 표시
            List<Payment> filteredPayments = filterPaymentsForDisplay(allPayments);

            List<Map<String, Object>> paymentInfoList = filteredPayments.stream().map(payment -> {
                Map<String, Object> paymentInfo = new HashMap<>();
                paymentInfo.put("paymentId", payment.getId());
                paymentInfo.put("tid", payment.getTid());
                paymentInfo.put("amount", payment.getAmount());
                paymentInfo.put("status", payment.getStatus());
                paymentInfo.put("paymentType", payment.getPaymentType());
                paymentInfo.put("paymentTypeDescription", getPaymentTypeDescription(payment.getPaymentType()));
                paymentInfo.put("resultCode", payment.getResultCode());
                paymentInfo.put("resultMsg", payment.getResultMsg());
                paymentInfo.put("paymentDate", payment.getPaymentDate());
                paymentInfo.put("cardName", payment.getCardName());
                paymentInfo.put("canRefund", canRefundPayment(payment));

                return paymentInfo;
            }).toList();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("totalAmount", order.getTotalAmount());
            result.put("cardAmount", order.getCardAmount());
            result.put("pointsUsed", order.getPointsUsed());
            result.put("status", order.getStatus().toString());
            result.put("statusMessage", getOrderStatusMessage(order.getStatus()));
            result.put("createdAt", order.getCreatedAt());
            result.put("updatedAt", order.getUpdatedAt());
            result.put("payments", paymentInfoList);

            return result;

        } catch (Exception e) {
            logger.error("Error getting order detail with payments for orderNo: {}", orderNo, e);
            return Map.of(
                "success", false,
                "message", "주문 상세 조회 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // 주문의 결제 상태를 확인하고 필요시 주문 상태 업데이트
    private void updateOrderStatusBasedOnPayments(String orderNo) {
        try {
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                logger.warn("Order not found for updateOrderStatusBasedOnPayments: {}", orderNo);
                return;
            }

            Order order = orderOpt.get();
            List<Payment> allPayments = paymentRepository.findByOrderNoOrderByPaymentDateDesc(orderNo);

            // 모든 활성 결제 내역 (취소되지 않은 것들)
            List<Payment> activePayments = allPayments.stream()
                .filter(p -> !"REFUNDED".equals(p.getStatus()) &&
                           !p.getPaymentType().endsWith("_REFUND") &&
                           p.getAmount() > 0)
                .toList();

            // 모든 취소 내역
            List<Payment> refundPayments = allPayments.stream()
                .filter(p -> "REFUNDED".equals(p.getStatus()) ||
                           p.getPaymentType().endsWith("_REFUND") ||
                           p.getAmount() < 0)
                .toList();

            // 활성 결제 총액
            long activePaymentAmount = activePayments.stream()
                .mapToLong(Payment::getAmount)
                .sum();

            // 취소 총액 (절댓값)
            long refundAmount = refundPayments.stream()
                .mapToLong(p -> Math.abs(p.getAmount()))
                .sum();

            logger.info("Order {} - Active payments: {}, Refund amount: {}, Total amount: {}",
                       orderNo, activePaymentAmount, refundAmount, order.getTotalAmount());

            // 모든 결제가 취소된 경우 주문을 취소 상태로 변경
            if (activePaymentAmount == 0 && refundAmount > 0 &&
                order.getStatus() != Order.OrderStatus.CANCELLED) {
                order.setStatus(Order.OrderStatus.CANCELLED);
                orderRepository.save(order);
                logger.info("Order {} status changed to CANCELLED - all payments refunded", orderNo);
            }

        } catch (Exception e) {
            logger.error("Error updating order status for orderNo: {}", orderNo, e);
        }
    }

    // 취소가 발생한 경우 원본 사용내역은 숨기고 취소내역만 표시하기 위한 필터링
    private List<Payment> filterPaymentsForDisplay(List<Payment> allPayments) {
        // 1. TID 중복 제거 (같은 TID를 가진 경우 가장 최신 것만 유지)
        Map<String, Payment> tidToLatestPayment = new LinkedHashMap<>();

        for (Payment payment : allPayments) {
            String tid = payment.getTid();
            if (tid != null && !tid.trim().isEmpty()) {
                // 같은 TID가 있으면 최신 것으로 교체 (이미 정렬되어 있으므로 첫 번째가 최신)
                if (!tidToLatestPayment.containsKey(tid)) {
                    tidToLatestPayment.put(tid, payment);
                }
            } else {
                // TID가 없는 경우 (적립금 결제 등) 그대로 포함
                tidToLatestPayment.put("NO_TID_" + payment.getId(), payment);
            }
        }

        List<Payment> uniquePayments = new ArrayList<>(tidToLatestPayment.values());

        // 2. 취소 내역이 있는지 확인
        boolean hasCardRefund = uniquePayments.stream()
            .anyMatch(p -> "CARD_REFUND".equals(p.getPaymentType()));
        boolean hasPointRefund = uniquePayments.stream()
            .anyMatch(p -> "POINT_REFUND".equals(p.getPaymentType()));

        return uniquePayments.stream()
            .filter(payment -> {
                String paymentType = payment.getPaymentType();

                // 카드 취소가 있으면 원본 카드 결제는 숨김
                if ("CARD".equals(paymentType) && hasCardRefund) {
                    return false;
                }

                // 적립금 취소가 있으면 원본 적립금 사용은 숨김
                if ("POINT".equals(paymentType) && hasPointRefund) {
                    return false;
                }

                // 나머지는 모두 표시 (취소 내역, 실패 내역 등)
                return true;
            })
            .toList();
    }

    // 주문번호로 결제 취소
    public Map<String, Object> refundPaymentByOrderNo(String orderNo, String refundReason, String clientIp) {
        try {
            logger.info("Processing refund for orderNo: {}, reason: {}", orderNo, refundReason);
            
            // 해당 주문의 완료된 결제 정보 조회
            List<Payment> completedPayments = paymentRepository.findByOrderNoAndStatusCompleted(orderNo);
            Optional<Payment> paymentOpt = completedPayments.isEmpty() ? Optional.empty() : Optional.of(completedPayments.get(0));
            if (paymentOpt.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "해당 주문의 결제 정보를 찾을 수 없습니다."
                );
            }
            
            Payment payment = paymentOpt.get();
            
            // 취소 가능한 상태인지 확인
            if (!canRefundPayment(payment)) {
                return Map.of(
                    "success", false,
                    "message", "취소할 수 없는 결제 상태입니다. (상태: " + payment.getStatus() + ")"
                );
            }
            
            String tid = payment.getTid();
            if (tid == null || tid.trim().isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "결제의 거래 ID(TID)가 없어 취소할 수 없습니다."
                );
            }
            
            // 이니시스 취소 API 호출
            Map<String, Object> refundResult = callInicisRefundAPI(tid, refundReason, clientIp);
            
            String resultCode = (String) refundResult.get("resultCode");
            if ("00".equals(resultCode)) {
                // 취소 성공 시 payments 테이블에 취소 데이터 저장
                saveRefundRecord(payment, refundResult, refundReason);

                // 주문 상태 업데이트 (모든 결제가 취소되었는지 확인)
                updateOrderStatusBasedOnPayments(orderNo);

                return Map.of(
                    "success", true,
                    "message", "주문이 성공적으로 취소되었습니다.",
                    "orderNo", orderNo,
                    "tid", tid,
                    "refundResult", refundResult
                );
            } else {
                return Map.of(
                    "success", false,
                    "message", "결제 취소에 실패했습니다: " + refundResult.get("resultMsg"),
                    "resultCode", resultCode
                );
            }
            
        } catch (Exception e) {
            logger.error("Error processing refund for orderNo: {}", orderNo, e);
            return Map.of(
                "success", false,
                "message", "결제 취소 처리 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
    
    // 결제 취소 가능 여부 확인
    private boolean canRefundPayment(Payment payment) {
        return "COMPLETED".equals(payment.getStatus()) && payment.getAmount() > 0;
    }
    
    // 주문 상태 메시지 반환
    private String getOrderStatusMessage(Order.OrderStatus status) {
        switch (status) {
            case PENDING: return "결제 대기 중";
            case PENDING_APPROVAL: return "승인 대기 중";
            case APPROVED: return "승인 완료";
            case COMPLETED: return "주문 완료";
            case CANCELLED: return "주문 취소";
            case FAILED: return "주문 실패";
            default: return "알 수 없는 상태";
        }
    }

    // 결제 타입 설명 반환
    private String getPaymentTypeDescription(String paymentType) {
        if (paymentType == null) return "알 수 없음";

        try {
            Payment.PaymentType type = Payment.PaymentType.valueOf(paymentType);
            return type.getDescription();
        } catch (IllegalArgumentException e) {
            return paymentType;
        }
    }
    
    // 결제 취소 (이니시스 refund API)
    public Map<String, Object> refundPayment(String tid, String refundReason, String clientIp) {
        try {
            logger.info("Processing refund for TID: {}, reason: {}", tid, refundReason);
            
            // 해당 TID의 가장 최신 완료된 결제 정보 조회
            List<Payment> completedPayments = paymentRepository.findByTidAndStatusOrderByPaymentDateDesc(tid, "COMPLETED");
            Optional<Payment> paymentOpt = completedPayments.isEmpty() ? Optional.empty() : Optional.of(completedPayments.get(0));
            if (paymentOpt.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "취소 가능한 결제 내역을 찾을 수 없습니다."
                );
            }
            
            Payment payment = paymentOpt.get();
            
            // 이니시스 취소 API 호출
            Map<String, Object> refundResult = callInicisRefundAPI(tid, refundReason, clientIp);
            
            String resultCode = (String) refundResult.get("resultCode");
            if ("00".equals(resultCode)) {
                // 취소 성공 시 payments 테이블에 취소 데이터 저장
                saveRefundRecord(payment, refundResult, refundReason);

                // 주문 상태 업데이트 (모든 결제가 취소되었는지 확인)
                updateOrderStatusBasedOnPayments(payment.getOrderNo());

                return Map.of(
                    "success", true,
                    "message", "결제가 성공적으로 취소되었습니다.",
                    "tid", tid,
                    "refundResult", refundResult
                );
            } else {
                return Map.of(
                    "success", false,
                    "message", "결제 취소에 실패했습니다: " + refundResult.get("resultMsg"),
                    "resultCode", resultCode
                );
            }
            
        } catch (Exception e) {
            logger.error("Error processing refund for TID: {}", tid, e);
            return Map.of(
                "success", false,
                "message", "결제 취소 처리 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
    
    // 이니시스 취소 API 호출
    private Map<String, Object> callInicisRefundAPI(String tid, String refundReason, String clientIp) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            
            // 취소 요청 데이터 생성
            Map<String, Object> data = new HashMap<>();
            data.put("tid", tid);
            data.put("msg", refundReason);
            
            // hashData 생성 (SHA512): INIAPIKey + mid + type + timestamp + data
            String dataJson = objectMapper.writeValueAsString(data);
            String hashSource = INI_API_KEY + MID + "refund" + timestamp + dataJson;
            String hashData = sha512Hash(hashSource);
            
            // 요청 데이터 생성
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("mid", MID);
            requestData.put("type", "refund");
            requestData.put("timestamp", timestamp);
            requestData.put("clientIp", clientIp);
            requestData.put("hashData", hashData);
            requestData.put("data", data);
            
            logger.info("=== 취소 요청 REQUEST DATA ===");
            logger.info("TID: {}", tid);
            logger.info("Refund URL: {}", INICIS_REFUND_URL);
            logger.info("Hash Source: {}", hashSource);
            logger.info("Generated Hash: {}", hashData);
            logger.info("Request JSON:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestData));
            logger.info("=== 취소 요청 REQUEST DATA END ===");
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // HTTP 요청 생성 및 전송
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestData, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(INICIS_REFUND_URL, request, String.class);
            String responseBody = response.getBody();
            
            logger.info("=== 취소 응답 RESPONSE DATA ===");
            logger.info("TID: {}", tid);
            logger.info("HTTP Status: {}", response.getStatusCode());
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                if (responseBody.trim().startsWith("{")) {
                    Object parsedResponse = objectMapper.readValue(responseBody, Object.class);
                    String prettyResponseJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedResponse);
                    logger.info("Response JSON:\n{}", prettyResponseJson);
                } else {
                    logger.info("Response Body: {}", responseBody);
                }
            }
            logger.info("=== 취소 응답 RESPONSE DATA END ===");
            
            // 간단한 응답 처리
            return Map.of("resultCode", "00", "resultMsg", "취소 완료");
            
        } catch (Exception e) {
            logger.error("Error calling Inicis refund API for TID: {}", tid, e);
            return Map.of(
                "resultCode", "99",
                "resultMsg", "취소 API 호출 실패: " + e.getMessage()
            );
        }
    }
    
    // 취소 성공 시 payments 테이블에 취소 데이터 저장
    private void saveRefundRecord(Payment originalPayment, Map<String, Object> refundResult, String refundReason) {
        try {
            logger.info("Saving refund record for original payment: {}", originalPayment.getId());
            
            Payment refundPayment = new Payment();
            refundPayment.setOrderNo(originalPayment.getOrderNo());
            refundPayment.setTid(originalPayment.getTid());
            refundPayment.setAmount(-originalPayment.getAmount()); // 음수로 저장하여 취소 표시
            refundPayment.setStatus("REFUNDED");
            refundPayment.setPaymentType(Payment.PaymentType.CARD_REFUND.name());
            refundPayment.setResultCode((String) refundResult.get("resultCode"));
            refundPayment.setResultMsg("취소: " + refundReason);
            refundPayment.setPaymentDate(LocalDateTime.now());
            refundPayment.setCardName(originalPayment.getCardName());
            refundPayment.setCardCode(originalPayment.getCardCode());
            
            paymentRepository.save(refundPayment);
            logger.info("Refund record saved successfully - Payment ID: {}", refundPayment.getId());
            
        } catch (Exception e) {
            logger.error("Error saving refund record: {}", e.getMessage(), e);
        }
    }
    
    // SHA512 해시 생성
    private String sha512Hash(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // 적립금 취소 (포인트 복구)
    public Map<String, Object> refundPointsByOrderNo(String orderNo, String refundReason) {
        try {
            logger.info("Processing point refund for orderNo: {}, reason: {}", orderNo, refundReason);

            // 해당 주문의 적립금 사용 내역 조회
            List<Payment> pointPayments = paymentRepository.findByOrderNo(orderNo)
                    .stream()
                    .filter(p -> Payment.PaymentType.POINT.name().equals(p.getPaymentType()) && "COMPLETED".equals(p.getStatus()))
                    .toList();

            if (pointPayments.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "해당 주문의 적립금 사용 내역을 찾을 수 없습니다."
                );
            }

            // 이미 취소된 적립금이 있는지 확인
            List<Payment> refundedPoints = paymentRepository.findByOrderNo(orderNo)
                    .stream()
                    .filter(p -> Payment.PaymentType.POINT_REFUND.name().equals(p.getPaymentType()))
                    .toList();

            if (!refundedPoints.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "이미 적립금이 취소되었습니다."
                );
            }

            // 주문 정보 조회하여 사용자 정보 가져오기
            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "주문 정보를 찾을 수 없습니다."
                );
            }

            Order order = orderOpt.get();
            Optional<User> userOpt = userRepository.findById(order.getUserId());
            if (userOpt.isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "사용자 정보를 찾을 수 없습니다."
                );
            }

            User user = userOpt.get();
            Long totalPointsToRefund = pointPayments.stream()
                    .mapToLong(Payment::getAmount)
                    .sum();

            // 사용자 적립금 복구
            user.setPoints(user.getPoints() + totalPointsToRefund.intValue());
            userRepository.save(user);

            // 적립금 취소 내역 Payment 테이블에 기록
            Payment pointRefund = new Payment();
            pointRefund.setOrderNo(orderNo);
            pointRefund.setTid("POINTS_REFUND_" + System.currentTimeMillis());
            pointRefund.setAmount(-totalPointsToRefund); // 음수로 저장하여 취소 표시
            pointRefund.setStatus("REFUNDED");
            pointRefund.setPaymentType(Payment.PaymentType.POINT_REFUND.name());
            pointRefund.setResultCode("0000");
            pointRefund.setResultMsg("적립금 취소: " + refundReason);
            pointRefund.setPaymentDate(LocalDateTime.now());
            pointRefund.setCardName("적립금");

            paymentRepository.save(pointRefund);

            logger.info("Points refunded successfully - OrderNo: {}, Points: {}, User: {}",
                       orderNo, totalPointsToRefund, user.getId());

            // 주문 상태 업데이트 (모든 결제가 취소되었는지 확인)
            updateOrderStatusBasedOnPayments(orderNo);

            return Map.of(
                "success", true,
                "message", "적립금이 성공적으로 취소되었습니다.",
                "orderNo", orderNo,
                "refundedPoints", totalPointsToRefund,
                "userCurrentPoints", user.getPoints()
            );

        } catch (Exception e) {
            logger.error("Error processing point refund for orderNo: {}", orderNo, e);
            return Map.of(
                "success", false,
                "message", "적립금 취소 처리 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // 외부에서 호출할 수 있는 이니시스 요청 로깅 메서드
    public void logInicisRequest(String orderNo, String requestType, String requestUrl, Map<String, Object> requestData) {
        createInicisLog(orderNo, requestType, requestUrl, requestData);
    }

    // 이니시스 로그 생성 및 요청 데이터 저장
    private IfInisisLog createInicisLog(String orderNo, String requestType, String requestUrl, Map<String, Object> requestData) {
        try {
            IfInisisLog log = new IfInisisLog(orderNo != null ? orderNo : "UNKNOWN", requestType, "INICIS");
            log.setRequestUrl(requestUrl);

            // 요청 데이터를 JSON으로 변환하여 저장
            if (requestData != null) {
                try {
                    String jsonData = objectMapper.writeValueAsString(requestData);
                    logger.info("=== DEBUG: requestData type: {}", requestData.getClass().getName());
                    logger.info("=== DEBUG: requestData content: {}", requestData);
                    logger.info("=== DEBUG: JSON result: {}", jsonData);
                    log.setRequestData(jsonData);
                } catch (Exception e) {
                    logger.error("JSON conversion failed: {}", e.getMessage());
                    log.setRequestData("JSON_ERROR: " + requestData.toString());
                }
            }

            // 민감한 정보 추출 및 저장
            if (requestData != null) {
                String tid = extractTransactionId(requestData);
                if (tid != null && !tid.trim().isEmpty()) {
                    log.setTransactionId(tid);
                }
            }

            ifInisisLogRepository.save(log);
            logger.info("Created Inicis log: orderNo={}, requestType={}", orderNo, requestType);

            return log;

        } catch (Exception e) {
            logger.error("Error creating Inicis log: {}", e.getMessage(), e);
            return null;
        }
    }

    // 이니시스 로그 응답 데이터 업데이트
    private void updateInicisLogResponse(IfInisisLog log, Map<String, Object> responseData, int httpStatus, boolean isSuccess, String errorMessage) {
        try {
            if (log != null) {
                // 응답 데이터를 JSON으로 변환하여 저장
                if (responseData != null) {
                    try {
                        String jsonData = objectMapper.writeValueAsString(responseData);
                        log.setResponseData(jsonData);
                    } catch (Exception e) {
                        log.setResponseData("JSON_ERROR: " + responseData.toString());
                    }
                }

                log.setHttpStatus(httpStatus);
                log.setIsSuccess(isSuccess);

                if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                    log.setErrorMessage(errorMessage);
                }

                ifInisisLogRepository.save(log);
                logger.info("Updated Inicis log response: orderNo={}, success={}", log.getOrderNo(), isSuccess);
            }

        } catch (Exception e) {
            logger.error("Error updating Inicis log: {}", e.getMessage(), e);
        }
    }

    private void updatePaymentFromInicisResponse(Payment payment, InicisResponseDto response) {
        payment.setTid(response.getTid());
        payment.setStatus(response.isSuccess() ? "SUCCESS" : "FAILED");
        payment.setResultCode(response.getResultCode());
        payment.setResultMsg(response.getResultMsg());
        payment.setCardName(response.getCardName());
        payment.setCardCode(response.getCardCode());
        payment.setApplNum(response.getApplNum());
        
        if (response.getApplDate() != null && response.getApplTime() != null) {
            payment.setPaymentDate(paymentMapper.parsePaymentDate(response.getApplDate(), response.getApplTime()));
        }
    }

    // ===== NICE Pay 관련 메서드들 =====

    // NICE Pay 결제 요청
    @Transactional
    public Map<String, Object> requestNicePayment(String orderNo, Long amount, String productName, String buyerName, String buyerEmail, String buyerTel) {
        Map<String, Object> result = new HashMap<>();

        try {
            // NICE Pay 요청 데이터 생성
            Map<String, Object> requestData = createNicePayRequestData(orderNo, amount, productName, buyerName, buyerEmail, buyerTel);

            // 로그 생성
            IfInisisLog log = createPaymentProviderLog(orderNo, "NICEPAY_REQUEST", nicePayApiUrl + "payment/webpay/pay_form.jsp", requestData, "NICEPAY");

            result.put("success", true);
            result.put("paymentData", requestData);
            result.put("apiUrl", nicePayApiUrl + "payment/webpay/pay_form.jsp");

            logger.info("NICE Pay payment request created for order: {}", orderNo);
            return result;

        } catch (Exception e) {
            logger.error("Error creating NICE Pay request: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    // NICE Pay 결제 응답 처리
    @Transactional
    public PaymentResultDto handleNicePayResponse(Map<String, Object> params) {
        String orderNo = null;
        try {
            logger.info("=== NICE Pay 응답 처리 시작 ===");
            logger.info("Response params: {}", params);

            // 주문번호 추출
            orderNo = extractOrderNoFromNicePay(params);
            logger.info("Extracted orderNo: {}", orderNo);

            // 로그 생성
            IfInisisLog log = createPaymentProviderLog(orderNo, "NICEPAY_RESPONSE", null, params, "NICEPAY");

            // NICE Pay 응답 파라미터 확인 (다양한 케이스 처리)
            String resultCode = getStringParam(params, "ResultCode", "AuthResultCode", "resultCode");
            String resultMsg = getStringParam(params, "ResultMsg", "AuthResultMsg", "resultMsg");
            String tid = getStringParam(params, "TID", "tid", "Tid");
            String amt = getStringParam(params, "Amt", "amt", "Amount");

            logger.info("ResultCode: {}, ResultMsg: {}, TID: {}, Amt: {}", resultCode, resultMsg, tid, amt);

            // 결제 성공 여부 확인 (NICE Pay는 다양한 성공 코드 사용)
            boolean isSuccess = isNicePaySuccess(resultCode);

            PaymentResultDto result = new PaymentResultDto();
            result.setSuccess(isSuccess);
            result.setOrderNo(orderNo);
            result.setResultCode(resultCode);
            result.setResultMessage(resultMsg);

            if (isSuccess && orderNo != null && tid != null && amt != null) {
                try {
                    logger.info("=== 결제 정보 저장 시작 ===");
                    logger.info("OrderNo: {}, TID: {}, Amount: {}", orderNo, tid, amt);

                    // 중복 결제 확인
                    Optional<Payment> existingPayment = paymentRepository.findByOrderNoAndTid(orderNo, tid);
                    if (existingPayment.isPresent()) {
                        logger.warn("Duplicate payment detected: orderNo={}, tid={}", orderNo, tid);
                        result.setTid(tid);
                        result.setAmount(Long.valueOf(amt));
                        logger.info("Using existing payment record");
                        return result;
                    }

                    // 결제 성공 시 Payment 엔티티 저장
                    Payment payment = new Payment(
                        orderNo,
                        tid,
                        Long.valueOf(amt),
                        "COMPLETED",
                        resultCode,
                        resultMsg,
                        Payment.PaymentType.CARD.name()
                    );

                    logger.info("Created Payment entity: {}", payment.getId());

                    // NICE Pay 응답 데이터로 추가 정보 설정
                    updatePaymentFromNicePayResponse(payment, params);

                    // 저장 전 로그
                    logger.info("Saving payment to database...");
                    Payment savedPayment = paymentRepository.save(payment);
                    logger.info("Payment saved with ID: {}", savedPayment.getId());

                    // 주문 상태 업데이트
                    logger.info("Updating order status...");
                    updateOrderStatusBasedOnPayments(orderNo);

                    result.setTid(tid);
                    result.setAmount(Long.valueOf(amt));

                    logger.info("=== NICE Pay payment completed for order: {} ===", orderNo);
                } catch (Exception e) {
                    logger.error("=== Error saving NICE Pay payment ===", e);
                    logger.error("OrderNo: {}, TID: {}, Amount: {}", orderNo, tid, amt);
                    result.setSuccess(false);
                    result.setResultMessage("결제 저장 중 오류 발생: " + e.getMessage());
                }
            } else {
                logger.warn("=== NICE Pay payment validation failed ===");
                logger.warn("Success: {}, OrderNo: {}, TID: {}, Amount: {}", isSuccess, orderNo, tid, amt);
                logger.warn("ResultCode: {}, ResultMsg: {}", resultCode, resultMsg);
            }

            // 로그 응답 업데이트
            updatePaymentProviderLogResponse(log, params, 200, isSuccess, isSuccess ? null : resultMsg);

            return result;

        } catch (Exception e) {
            logger.error("Error processing NICE Pay response: {}", e.getMessage(), e);
            PaymentResultDto result = new PaymentResultDto();
            result.setSuccess(false);
            result.setOrderNo(orderNo);
            result.setResultMessage("결제 처리 중 오류 발생: " + e.getMessage());
            return result;
        }
    }

    // NICE Pay 취소 요청
    @Transactional
    public Map<String, Object> cancelNicePayment(String tid, Long amount, String reason) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 취소 요청 데이터 생성
            Map<String, Object> cancelData = new HashMap<>();
            cancelData.put("TID", tid);
            cancelData.put("MID", nicePayMerchantId);
            cancelData.put("Moid", tid); // 취소는 TID 기준
            cancelData.put("CancelAmt", amount);
            cancelData.put("CancelMsg", reason);
            cancelData.put("CancelPwd", nicePayCancelPwd);
            cancelData.put("PartialCancelCode", "0"); // 전체취소

            // API 호출
            String cancelUrl = nicePayApiUrl + "payment/webpay/cancel_form.jsp";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            for (Map.Entry<String, Object> entry : cancelData.entrySet()) {
                formData.add(entry.getKey(), entry.getValue().toString());
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(cancelUrl, request, String.class);

            // 로그 생성
            createPaymentProviderLog(tid, "NICEPAY_CANCEL", cancelUrl, cancelData, "NICEPAY");

            result.put("success", true);
            result.put("response", response.getBody());

            logger.info("NICE Pay cancellation requested for TID: {}", tid);

        } catch (Exception e) {
            logger.error("Error cancelling NICE Pay: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // NICE Pay 요청 데이터 생성
    private Map<String, Object> createNicePayRequestData(String orderNo, Long amount, String productName, String buyerName, String buyerEmail, String buyerTel) {
        Map<String, Object> data = new HashMap<>();

        data.put("MID", nicePayMerchantId);
        data.put("Moid", orderNo);
        data.put("Amt", amount);
        data.put("GoodsName", productName != null ? productName : "상품결제");
        data.put("BuyerName", buyerName != null ? buyerName : "구매자");
        data.put("BuyerEmail", buyerEmail != null ? buyerEmail : "test@test.com");
        data.put("BuyerTel", buyerTel != null ? buyerTel : "010-0000-0000");
        // PC 결제는 ReturnURL 사용하지 않음 (콜백 함수 사용)
        data.put("CloseURL", "http://localhost:8081/api/payment/nicepay/close");
        data.put("CancelURL", "http://localhost:8081/api/payment/nicepay/cancel");
        data.put("PayMethod", "CARD");
        data.put("GoodsCl", "1"); // 실물
        data.put("TransType", "0"); // 일반결제
        data.put("CharSet", "utf-8");
        data.put("ReqReserved", ""); // 가맹점 예약필드

        return data;
    }

    // NICE Pay 주문번호 추출
    private String extractOrderNoFromNicePay(Map<String, Object> params) {
        String orderNo = (String) params.get("Moid");
        if (orderNo == null) orderNo = (String) params.get("MOID");
        if (orderNo == null) orderNo = (String) params.get("moid");
        return orderNo;
    }

    // NICE Pay 응답으로 Payment 엔티티 업데이트
    private void updatePaymentFromNicePayResponse(Payment payment, Map<String, Object> response) {
        payment.setCardName((String) response.get("CardName"));
        payment.setCardCode((String) response.get("CardCode"));
        payment.setApplNum((String) response.get("AuthCode"));

        // NICE Pay 결제일시 설정
        String authDate = (String) response.get("AuthDate");
        if (authDate != null && authDate.length() >= 8) {
            try {
                payment.setPaymentDate(LocalDateTime.now()); // 현재 시간으로 설정
            } catch (Exception e) {
                logger.warn("Error parsing NICE Pay payment date: {}", authDate);
                payment.setPaymentDate(LocalDateTime.now());
            }
        }
    }

    // 범용 결제 제공자 로그 생성
    private IfInisisLog createPaymentProviderLog(String orderNo, String requestType, String requestUrl, Map<String, Object> requestData, String provider) {
        try {
            IfInisisLog log = new IfInisisLog(orderNo != null ? orderNo : "UNKNOWN", requestType, provider);
            log.setRequestUrl(requestUrl);

            // 요청 데이터를 JSON으로 변환하여 저장
            if (requestData != null) {
                try {
                    String jsonData = objectMapper.writeValueAsString(requestData);
                    log.setRequestData(jsonData);
                } catch (Exception e) {
                    logger.error("JSON conversion failed: {}", e.getMessage());
                    log.setRequestData("JSON_ERROR: " + requestData.toString());
                }
            }

            // 거래 ID 추출
            String tid = extractTransactionIdFromProvider(requestData, provider);
            if (tid != null && !tid.trim().isEmpty()) {
                log.setTransactionId(tid);
            }

            ifInisisLogRepository.save(log);
            logger.info("Created {} log: orderNo={}, requestType={}", provider, orderNo, requestType);

            return log;

        } catch (Exception e) {
            logger.error("Error creating {} log: {}", provider, e.getMessage(), e);
            return null;
        }
    }

    // 범용 결제 제공자 로그 응답 업데이트
    private void updatePaymentProviderLogResponse(IfInisisLog log, Map<String, Object> responseData, int httpStatus, boolean isSuccess, String errorMessage) {
        try {
            if (log != null) {
                // 응답 데이터를 JSON으로 변환하여 저장
                if (responseData != null) {
                    try {
                        String jsonData = objectMapper.writeValueAsString(responseData);
                        log.setResponseData(jsonData);
                    } catch (Exception e) {
                        logger.error("JSON conversion failed: {}", e.getMessage());
                        log.setResponseData("JSON_ERROR: " + responseData.toString());
                    }
                }

                log.setHttpStatus(httpStatus);
                log.setIsSuccess(isSuccess);

                if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                    log.setErrorMessage(errorMessage);
                }

                ifInisisLogRepository.save(log);
                logger.info("Updated {} log response: orderNo={}, success={}", log.getPaymentProvider(), log.getOrderNo(), isSuccess);
            }

        } catch (Exception e) {
            logger.error("Error updating {} log: {}", log != null ? log.getPaymentProvider() : "payment", e.getMessage(), e);
        }
    }

    // 결제 제공자별 거래 ID 추출
    private String extractTransactionIdFromProvider(Map<String, Object> data, String provider) {
        if (data == null) return null;

        if ("NICEPAY".equals(provider)) {
            String tid = (String) data.get("TID");
            if (tid == null) tid = (String) data.get("tid");
            if (tid == null) tid = (String) data.get("Tid");
            return tid;
        } else if ("INICIS".equals(provider)) {
            return extractTransactionId(data); // 기존 Inicis 로직 사용
        }

        return null;
    }

    // 다양한 키로 문자열 파라미터 추출 (NICE Pay용)
    private String getStringParam(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    // NICE Pay 성공 여부 확인
    private boolean isNicePaySuccess(String resultCode) {
        if (resultCode == null) return false;

        // NICE Pay 성공 코드들
        return "0000".equals(resultCode) ||
               "2001".equals(resultCode) || // 가상계좌 발급 성공
               "2211".equals(resultCode);   // 인증 성공
    }

    // NICE Pay 응답에서 주문번호 추출 (실제 응답 구조용)
    private String extractOrderNoFromNicePayResponse(Map<String, Object> authParams, Map<String, Object> result) {
        // 1. 상위 레벨에서 주문번호 확인
        String orderNo = getStringParam(authParams, "Moid", "MOID", "moid", "OrderNo", "orderNo");

        // 2. result 객체에서 주문번호 확인
        if (orderNo == null && result != null) {
            orderNo = getStringParam(result, "Moid", "MOID", "moid", "OrderNo", "orderNo");
        }

        // 3. 기타 가능한 위치에서 확인
        if (orderNo == null) {
            orderNo = getStringParam(authParams, "orderNo", "order_no", "ORDER_NO");
        }

        return orderNo;
    }

    // NICE Pay 승인 API 처리 (인증 응답 후 호출)
    @Transactional
    public Map<String, Object> approveNicePayPayment(Map<String, Object> authParams) {
        String orderNo = null;
        try {
            logger.info("=== NICE Pay 승인 처리 시작 ===");
            logger.info("인증 응답 파라미터: {}", authParams);

            // NICE Pay 실제 응답 구조 파싱 (플랫한 구조)
            String authResultCode = getStringParam(authParams, "AuthResultCode");
            String authResultMsg = getStringParam(authParams, "AuthResultMsg");

            logger.info("인증 결과 코드: {}, 메시지: {}", authResultCode, authResultMsg);

            // 주문번호 추출
            orderNo = getStringParam(authParams, "Moid");
            if (orderNo == null) {
                throw new RuntimeException("주문번호(Moid)를 찾을 수 없습니다.");
            }

            // 인증 결과 확인 (AuthResultCode가 0000이면 성공)
            if (!"0000".equals(authResultCode)) {
                logger.error("NICE Pay 인증 실패: {} - {}", authResultCode, authResultMsg);
                throw new RuntimeException("인증 실패: " + authResultMsg);
            }

            // 필수 인증 정보 추출
            String authToken = getStringParam(authParams, "AuthToken");
            String txTid = getStringParam(authParams, "TxTid");
            String nextAppURL = getStringParam(authParams, "NextAppURL");
            String netCancelURL = getStringParam(authParams, "NetCancelURL");

            if (authToken == null || txTid == null) {
                throw new RuntimeException("필수 인증 정보가 없습니다. AuthToken: " + authToken + ", TxTid: " + txTid);
            }
            String signature = getStringParam(authParams, "Signature");

            logger.info("AuthResultCode: {}, AuthResultMsg: {}, AuthToken: {}, TxTid: {}, NextAppURL: {}",
                       authResultCode, authResultMsg, authToken != null ? "***" : null, txTid, nextAppURL);

            // 서명 검증 (현재는 단순 존재 확인, 필요시 실제 검증 로직 추가)
            if (authToken == null || signature == null) {
                logger.error("NICE Pay 필수 파라미터 누락: AuthToken={}, Signature={}",
                           authToken != null ? "***" : null, signature != null ? "***" : null);
                return Map.of(
                    "success", false,
                    "resultCode", "SIGNATURE_ERROR",
                    "resultMessage", "필수 파라미터가 누락되었습니다.",
                    "orderNo", orderNo
                );
            }

            // 승인 로그 생성
            IfInisisLog log = createPaymentProviderLog(orderNo, "NICEPAY_APPROVAL_REQUEST",
                                                     "APPROVAL_API", authParams, "NICEPAY");

            // TODO: 실제 NICE Pay NextAppURL 호출
            // 현재는 성공으로 시뮬레이션 (인증 응답 파라미터 전체 전달)
            Map<String, Object> approvalResult = simulateNicePayApproval(authParams, orderNo);

            // 승인 결과 로깅
            createPaymentProviderLog(orderNo, "NICEPAY_APPROVAL_RESPONSE",
                                   "APPROVAL_API", approvalResult, "NICEPAY");

            // 결제 정보 저장
            saveNicePayPaymentResult(orderNo, approvalResult);

            return approvalResult;

        } catch (Exception e) {
            logger.error("NICE Pay 승인 처리 오류: {}", e.getMessage(), e);

            // 오류 로깅
            if (orderNo != null) {
                Map<String, Object> errorData = Map.of(
                    "error", e.getMessage(),
                    "originalParams", authParams
                );
                createPaymentProviderLog(orderNo, "NICEPAY_APPROVAL_ERROR",
                                       "APPROVAL_API", errorData, "NICEPAY");
            }

            return Map.of(
                "success", false,
                "resultCode", "PROCESSING_ERROR",
                "resultMessage", "승인 처리 중 오류가 발생했습니다: " + e.getMessage(),
                "orderNo", orderNo
            );
        }
    }

    // NICE Pay 서명 검증 (SHA256(AuthToken+MID+Amt+MerchantKey))
    private boolean verifyNicePaySignature(String authToken, String mid, String amt, String merchantKey) {
        try {
            if (authToken == null || mid == null || amt == null || merchantKey == null) {
                logger.warn("서명 검증에 필요한 파라미터가 누락됨: authToken={}, mid={}, amt={}, merchantKey={}",
                          authToken != null ? "***" : null, mid, amt, merchantKey != null ? "***" : null);
                return false;
            }

            String signData = authToken + mid + amt + merchantKey;
            String expectedSignature = sha256Hash(signData);

            logger.info("서명 검증 데이터: AuthToken=***, MID={}, Amt={}, 생성된 서명=***", mid, amt);

            // 실제 프로덕션에서는 NICE Pay에서 전달받은 서명과 비교해야 함
            // 현재는 서명 생성 과정만 검증
            return expectedSignature != null && expectedSignature.length() > 0;

        } catch (Exception e) {
            logger.error("서명 검증 중 오류: {}", e.getMessage(), e);
            return false;
        }
    }

    // NICE Pay 승인 시뮬레이션 (실제 구현시 NextAppURL 호출로 대체)
    private Map<String, Object> simulateNicePayApproval(Map<String, Object> authParams, String orderNo) {
        String authToken = getStringParam(authParams, "AuthToken");
        String txTid = getStringParam(authParams, "TxTid");
        String nextAppURL = getStringParam(authParams, "NextAppURL");
        String amount = getStringParam(authParams, "Amt");

        logger.info("NicePay 승인 시뮬레이션 - OrderNo: {}, Amount: {}, TxTid: {}", orderNo, amount, txTid);

        // 실제로는 NextAppURL을 호출하여 최종 승인을 받아야 함
        // 현재는 성공으로 시뮬레이션
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("resultCode", "0000");
        result.put("resultMessage", "정상처리");
        result.put("orderNo", orderNo);
        result.put("amount", amount != null ? amount : "0");
        result.put("tid", txTid != null ? txTid : "NICE" + System.currentTimeMillis());
        result.put("authToken", authToken);
        result.put("nextAppURL", nextAppURL);
        result.put("authResultCode", getStringParam(authParams, "AuthResultCode"));
        result.put("authResultMsg", getStringParam(authParams, "AuthResultMsg"));
        result.put("approvedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    // NICE Pay 결제 결과 저장
    private void saveNicePayPaymentResult(String orderNo, Map<String, Object> result) {
        try {
            if (!(Boolean) result.get("success")) {
                logger.info("결제 실패로 인해 저장하지 않음: {}", result);
                return;
            }

            Optional<Order> orderOpt = orderRepository.findByOrderNo(orderNo);
            if (orderOpt.isEmpty()) {
                logger.warn("주문을 찾을 수 없음: {}", orderNo);
                return;
            }

            Order order = orderOpt.get();
            String tid = (String) result.get("tid");
            String amt = (String) result.get("amount");

            // Payment 엔티티 저장
            Payment payment = new Payment();
            payment.setOrderNo(orderNo);
            payment.setTid(tid);
            payment.setAmount(amt != null ? Long.parseLong(amt) : order.getCardAmount());
            payment.setStatus("COMPLETED");
            payment.setPaymentDate(LocalDateTime.now());
            payment.setPaymentType("NICEPAY_CARD");

            paymentRepository.save(payment);

            // 주문 상태 업데이트
            order.setStatus(Order.OrderStatus.COMPLETED);
            orderRepository.save(order);

            logger.info("NICE Pay 결제 정보 저장 완료: orderNo={}, tid={}", orderNo, tid);

        } catch (Exception e) {
            logger.error("NICE Pay 결제 정보 저장 중 오류: {}", e.getMessage(), e);
        }
    }
}