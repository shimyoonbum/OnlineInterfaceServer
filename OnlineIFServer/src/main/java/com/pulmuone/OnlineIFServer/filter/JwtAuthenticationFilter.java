package com.pulmuone.OnlineIFServer.filter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.pulmuone.OnlineIFServer.util.JwtUtil;

import io.jsonwebtoken.Claims;

public class JwtAuthenticationFilter extends BasicAuthenticationFilter{
	private JwtUtil jwtUtil;
    private Logger logger = LoggerFactory.getLogger(this.getClass());

	public JwtAuthenticationFilter(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
		super(authenticationManager);
		// TODO Auto-generated constructor stub
		this.jwtUtil = jwtUtil;
	}
	
	// TODO JWT 분석 필요함
	
	@Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        Authentication authentication = getAuthentication(request);

        if (authentication != null) {
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }
	
	private Authentication getAuthentication(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null) {
            return null;
        }
        
        Claims claims = jwtUtil.getClaims(token);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(claims, null);
        return authentication;
    }

}
