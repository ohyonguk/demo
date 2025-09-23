package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 사용자 관리 API를 처리하는 컨트롤러
 *
 * 사용자 등록, 조회, 수정, 삭제 등의 기능을 제공합니다.
 *
 * @author Generated with Claude Code
 * @version 1.0
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    /**
     * 새로운 사용자 등록
     *
     * 이메일 중복 체크를 수행한 후 사용자를 등록합니다.
     *
     * @param user 등록할 사용자 정보
     * @return 등록된 사용자 정보 또는 오류 응답
     */
    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest().build();
        }
        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    }
}