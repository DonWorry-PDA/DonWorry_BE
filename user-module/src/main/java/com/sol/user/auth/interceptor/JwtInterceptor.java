package com.sol.user.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sol.common.exception.ErrorCode;
import com.sol.common.response.ApiResponse;
import com.sol.user.auth.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            writeErrorResponse(response, ErrorCode.AUTH_002);
            return false;
        }

        String token = header.substring(7);
        try {
            Long userId = jwtUtil.extractUserId(token);
            request.setAttribute("userId", userId);
            return true;
        } catch (ExpiredJwtException e) {
            writeErrorResponse(response, ErrorCode.AUTH_003);
            return false;
        } catch (JwtException e) {
            writeErrorResponse(response, ErrorCode.AUTH_002);
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
    }
}
