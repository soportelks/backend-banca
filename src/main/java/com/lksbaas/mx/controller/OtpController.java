package com.lksbaas.mx.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lksbaas.mx.service.impl.IMplEmailOtpService;

@RestController
@CrossOrigin("*")
@RequestMapping("/otp")
public class OtpController {

    @Autowired
    private IMplEmailOtpService emailOtpService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        emailOtpService.sendOtp(email);

        Map<String, String> response = Map.of("email", email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<Boolean> verifyOtp(@RequestBody Map<String, Object> request) {
        String email = (String) request.get("email");
        Integer code = Integer.parseInt(request.get("code").toString());

        boolean isValid = emailOtpService.verifyOtp(email, code);
        return ResponseEntity.ok(isValid);
    }
}
