package com.pulmuone.OnlineIFServer.config;

import javax.servlet.Filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

import com.pulmuone.OnlineIFServer.filter.JwtAuthenticationFilter;
import com.pulmuone.OnlineIFServer.util.JwtUtil;

@Configuration
@EnableWebSecurity
public class SecurityJavaConfig extends WebSecurityConfigurerAdapter{
	
	@Override
    protected void configure(HttpSecurity http) throws Exception {
		Filter filter = new JwtAuthenticationFilter(
                authenticationManager(), jwtUtil());
		
        http
                .cors().disable()
                .csrf().disable()
                //폼 로그인 창 나오지 않도록 설정
                .formLogin().disable()
                .headers().frameOptions().disable()
                .and()
                .addFilter(filter)
                //세션 관련
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
	
	@Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }
}
