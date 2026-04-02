package com.jimbro.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jimbro.dto.RegisterRequest;
import com.jimbro.dto.LoginRequest;
import com.jimbro.dto.UserResponse;
import com.jimbro.entity.User;
import com.jimbro.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // REGISTER USER
    public UserResponse registerUser(RegisterRequest request) {

        User existingUser = userRepository.findFirstByEmail(request.getEmail());

        if (existingUser != null) {
            throw new IllegalStateException("Email already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }

    // LOGIN USER
    public UserResponse loginUser(LoginRequest request) {

        User user = userRepository.findFirstByEmail(request.getEmail());

        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        boolean passwordMatch =
                passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!passwordMatch) {
            throw new IllegalArgumentException("Invalid password");
        }

        return mapToResponse(user);
    }

    // DTO MAPPER
    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
