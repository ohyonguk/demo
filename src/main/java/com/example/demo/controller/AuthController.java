package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 인증 관련 API를 처리하는 컨트롤러
 *
 * 사용자 로그인, 토큰 검증, 사용자 정보 조회 등의 기능을 제공합니다.
 * JWT 토큰 기반의 인증 시스템을 사용합니다.
 *
 * @author Generated with Claude Code
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 사용자 로그인 처리
     *
     * 이메일을 통해 사용자를 조회하고 JWT 토큰을 생성하여 반환합니다.
     *
     * @param loginRequest 로그인 요청 정보 (이메일 포함)
     * @return 로그인 성공 시 JWT 토큰과 사용자 정보, 실패 시 오류 메시지
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("사용자를 찾을 수 없습니다.");
        }
        
        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        
        LoginResponse response = new LoginResponse(
            token, 
            user.getId(), 
            user.getEmail(), 
            user.getName(), 
            user.getPhoneNumber(), 
            user.getPoints()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 로그인한 사용자 정보 조회
     *
     * Authorization 헤더의 JWT 토큰을 검증하고 해당 사용자의 정보를 반환합니다.
     *
     * @param authHeader Authorization 헤더 (Bearer 토큰 형식)
     * @return 사용자 정보 또는 오류 메시지
     */
    @GetMapping("/me")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body("토큰이 필요합니다.");
            }
            
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.badRequest().body("유효하지 않은 토큰입니다.");
            }
            
            Long userId = jwtUtil.getUserIdFromToken(token);
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("사용자를 찾을 수 없습니다.");
            }
            
            User user = userOpt.get();
            LoginResponse response = new LoginResponse(
                null, 
                user.getId(), 
                user.getEmail(), 
                user.getName(), 
                user.getPhoneNumber(), 
                user.getPoints()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("토큰 처리 중 오류가 발생했습니다.");
        }
    }
}