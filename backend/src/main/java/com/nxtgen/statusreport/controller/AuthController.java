package com.nxtgen.statusreport.controller;

import com.nxtgen.statusreport.dto.AuthRequest;
import com.nxtgen.statusreport.dto.AuthResponse;
import com.nxtgen.statusreport.model.User;
import com.nxtgen.statusreport.repository.UserRepository;
import com.nxtgen.statusreport.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();

        if (username.isBlank() || password.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "Username and password are required.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return error(HttpStatus.BAD_REQUEST, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (userRepository.existsByUsername(username)) {
            return error(HttpStatus.CONFLICT, "That username is already taken.");
        }

        User user = userRepository.save(new User(username, passwordEncoder.encode(password)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(jwtService.generateToken(user.getUsername()), user.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();

        return userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(
                        new AuthResponse(jwtService.generateToken(u.getUsername()), u.getUsername())))
                .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
    }

    /** Returns the currently authenticated username (used by the frontend to restore the session). */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");
        }
        return ResponseEntity.ok(Map.of("username", authentication.getName()));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
