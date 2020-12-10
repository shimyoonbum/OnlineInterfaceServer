package com.pulmuone.OnlineIFServer.intercept;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.pulmuone.OnlineIFServer.service.JwtService;

@Component("AuthInterceptor") // 컴포넌트 이름 설정
public class AuthInterceptor implements HandlerInterceptor {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private JwtService jwtService;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
		String token = request.getHeader("Authorization");
		
		if(token == null)
			return true;
		else if(token != null && jwtService.isUsable(token)){
			return true;
		}else{
			return false;
		}
	}
}
