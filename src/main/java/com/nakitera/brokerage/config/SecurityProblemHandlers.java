package com.nakitera.brokerage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nakitera.brokerage.dto.ErrorResponse;
import com.nakitera.brokerage.exception.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityProblemHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "Access is denied");
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI()
        ));
    }
}
