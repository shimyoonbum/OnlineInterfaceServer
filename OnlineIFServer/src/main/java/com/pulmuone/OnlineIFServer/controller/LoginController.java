package com.pulmuone.OnlineIFServer.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulmuone.OnlineIFServer.dto.UserInfo;
import com.pulmuone.OnlineIFServer.service.LoginService;
import com.pulmuone.OnlineIFServer.util.CUtil;
import com.pulmuone.OnlineIFServer.util.JwtUtil;
import com.pulmuone.OnlineIFServer.util.SecurityUtil;

import io.jsonwebtoken.Claims;

@RestController
@CrossOrigin(origins = "*", allowCredentials = "true")
@RequestMapping("/users")
public class LoginController {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
		
	@Autowired
	SecurityUtil sUtil;
	
	@Autowired
	JwtUtil jUtil;
	
	@Autowired
	LoginService loginService;		
	
	@PostMapping(value = "/doLogin")
    public Map<String, Object> doLogin(@RequestBody Map<String, Object> param, HttpSession session){			
		Map<String, Object> params = new HashMap<>();
		
		try {
			if(loginService.doLogin(param) == 0) {
				params.put("responseCode", "003");	
	    		params.put("responseMessage", "사용자ID가 존재하지 않습니다");
			}else {	
				
				UserInfo user = loginService.checkEmpty(param);
				
				if(CUtil.nullString(String.valueOf(user.getPassword()), "").isEmpty()) {
					params.put("responseCode", "005");	
		    		params.put("responseMessage", "비밀번호 변경이 필요합니다");
				}else {
					if(loginService.checkPw(param) == 1) {	
						
						String token = jUtil.createToken("member", user);		    		
			    		params.put("responseCode", "000");	
			    		params.put("responseMessage", "성공");
			    		params.put("data", token);			    					    		
			    		
					}else if(loginService.checkPw(param) == 0) {
						params.put("responseCode", "004");	
			    		params.put("responseMessage", "비밀번호가 일치하지 않습니다");
					}
				}
				
			}    	
		}catch(Exception e) {
			e.printStackTrace();
		}	
    	
    	return params;
    }

	@PostMapping(value = "/setPassword")
    public Map<String, Object> updatePassword(@RequestBody Map<String, Object> param, HttpSession session) {
				
		Map<String, Object> params = new HashMap<>();
		//비밀번호 변경 주기가 지나 변경하게 되는 경우
    	if(loginService.doLogin(param) == 1 && CUtil.nullString(String.valueOf(loginService.checkEmpty(param).getPassword()), "") == "") {
    		  		
    		loginService.updatePassword(param);
    		
    		params.put("responseCode", "000");	
    		params.put("responseMessage", "비밀번호가 변경되었습니다");
    	//기존 비밀번호 불일치	
    	}else if(loginService.checkPw(param) == 0){
    		
    		params.put("responseCode", "006");	
    		params.put("responseMessage", "기존 비밀번호가 일치하지 않습니다");
    	//비밀번호 변경 버튼으로 직접 변경하는 경우
    	}else if(loginService.doLogin(param) == 1 && loginService.checkPw(param) == 1) {
    		
    		loginService.updatePassword(param);
    		params.put("responseCode", "000");	
    		params.put("responseMessage", "비밀번호가 변경되었습니다");    		
    	}
    	
    	return params;		
    }
	
	@GetMapping(value = "/doLogout")
    public Map<String, Object> doLogout(HttpSession session) {
		
		Map<String, Object> params = new HashMap<>();
		
    	try{
    		params.put("responseCode", "000");	
        	params.put("responseMessage", "로그아웃 되었습니다.");   
    		
    	}catch(Exception e) {
    		params.put("responseCode", "007");	
        	params.put("responseMessage", "로그아웃 실패!");   
    	}	 	
    	
    	return params;
    }
	
	@GetMapping(value = "/getSession")
    public Map<String, Object> getSession(Authentication authentication) throws Exception {	
		
		Map<String, Object> params = new HashMap<>();	
		Claims claims;		
		
		try {
			//주체의 정보(Principal)를 가져온다
			claims = (Claims) authentication.getPrincipal();
			
			params.put("responseCode", "000");	
	    	params.put("responseMessage", "세션 불러오기 성공");
	    	params.put("data", claims.get("member"));
	    	
		}catch(Exception e) {
			params.put("responseCode", "999");	
			params.put("responseMessage", "토큰 인증 실패");
		}	
		
    	return params;    	
    }
	
	//yarn start 시에 필요합니다.
	@GetMapping(value = "/getProxySession")
    public Map<String, Object> getProxySession(HttpServletRequest request) throws Exception {	
		
		Map<String, Object> params = new HashMap<>();		
		
		params.put("responseCode", "000");	
    	params.put("responseMessage", "세션 불러오기 성공");
    	
    	return params;    	
    }
	
	@PostMapping(value = "/register")
    public Map<String, Object> register(@RequestParam Map<String, Object> param) {

    	Map<String, Object> params = new HashMap<>();	
		
		param.put("pw", sUtil.encryptSHA256(param.get("pw").toString()));				
		loginService.register(param);	
		
		params.put("responseCode", "000");	
    	params.put("responseMessage", "회원 등록 되었습니다.");    	
    	
    	return params;
    }
}
