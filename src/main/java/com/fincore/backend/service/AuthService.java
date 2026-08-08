package com.fincore.backend.service;


import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.dto.AuthRequest;
import com.fincore.backend.dto.AuthResponse;
import com.fincore.backend.dto.LoginRequest;
import com.fincore.backend.entity.Role;
import com.fincore.backend.entity.User;
import com.fincore.backend.enums.RoleName;
import com.fincore.backend.enums.UserStatus;
import com.fincore.backend.repository.RoleRepository;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.security.jwt.JwtUtil;


@Service
public class AuthService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;
	
	public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,JwtUtil jwtUtil) {
		this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
	}
	
	@Transactional
	public String register(AuthRequest request) {

	    if (userRepository.existsByUsername(request.getUsername())) {
	        throw new RuntimeException("Username already exists");
	    }

	    Role userRole = roleRepository
	            .findByName(RoleName.ROLE_USER)
	            .orElseThrow(() ->
	                    new RuntimeException("ROLE_USER not found"));

	    User user = new User();

	    user.setFirstName(request.getFirstName());
	    user.setLastName(request.getLastName());
	    user.setUsername(request.getUsername());
	    user.setEmail(request.getEmail());
	    user.setPhone(request.getPhone());

	    user.setPassword(
	            passwordEncoder.encode(request.getPassword())
	    );

	    user.setStatus(UserStatus.ACTIVE);
	    user.addRole(userRole);

	    userRepository.save(user);

	    return "User Registered Successfully";
	}
	
	@Transactional(readOnly=true)
	public AuthResponse login(LoginRequest req) {
		User user = userRepository.findByUsername(req.getUsername()).orElseThrow(()-> new RuntimeException("Invalid Username or Password"));
		
		if(user.getStatus()!=UserStatus.ACTIVE) {
			throw new RuntimeException("User account is not active");
		}
		
		if(!passwordEncoder.matches(req.getPassword(),user.getPassword())) {
			throw new RuntimeException("Invalid Username or Password");
		}
		
		Set<String> roles = user.getRoles().stream().map(role->role.getName().name()).collect(Collectors.toSet());
		
		String token = jwtUtil.generateToken(user.getUsername(),roles);
		
		return new AuthResponse(token, user.getUsername(), roles);
	}
	

    
}