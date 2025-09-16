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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;
    
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