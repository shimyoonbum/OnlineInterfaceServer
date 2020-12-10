package com.pulmuone.OnlineIFServer.intercept;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.google.gson.Gson;
import com.pulmuone.OnlineIFServer.common.ResponseStatus;
import com.pulmuone.OnlineIFServer.config.SystemConfig;
import com.pulmuone.OnlineIFServer.dto.UserInfo;
import com.pulmuone.OnlineIFServer.util.CUtil;
import com.pulmuone.OnlineIFServer.util.RestUtil;

@Component("beforeActionInterceptor") // 컴포넌트 이름 설정
public class BeforeActionInterceptor implements HandlerInterceptor {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());	
	
//	@Autowired
//	MemberService memberService;

	@Autowired
	SystemConfig systemConfig;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		
		boolean isLogined = false;
		long loginedMemberId = 0;
//		Member loginedMember = null;
		
		String authkey = request.getHeader("authkey");
		String interfaceId = request.getHeader("interfaceId");
		
		if(authkey != null && interfaceId != null) {
	    	Map sysMap = systemConfig.getSystemInfo(authkey);
	    	if(sysMap == null)
				request.setAttribute("isLogined", false);
	    	else
	    		request.setAttribute("isLogined", true);
			return HandlerInterceptor.super.preHandle(request, response, handler);
		}
		
//		HttpSession session = request.getSession();		
				
//		UserInfo user = UserInfo.getInstance();
//		if (user.getId() != "N") {
//			isLogined = true;
//			loginedMemberId = (long)session.getAttribute("loginedMemberId");
//			loginedMember = memberService.getOne(loginedMemberId);
//		}		

//		logger.info("session : "  + user.getId());
		
//		session.setAttribute("isLogined", isLogined);
//		session.setAttribute("loginedMemberId", loginedMemberId);
//		session.setAttribute("loginedMember", loginedMember);
		
		
//		if(!isLogined) {
//			sendResponse(response);
//			return false;
//		}
		return HandlerInterceptor.super.preHandle(request, response, handler);
	}
	
	
//	private void sendResponse(HttpServletResponse response) throws IOException {
//    	Map map = new HashMap();
//		map.put("responseCode", "999");
//    	map.put("responseMessage", "로그인이 필요합니다.");
//    	
//		response.setContentType("text/json; charset=UTF-8");
//		response.getWriter().append(new Gson().toJson(map));
//
//	}
	/**
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mv)
			throws Exception {

		System.out.println("컨트롤러 실행 후 :Bye");

		String url = request.getServletPath();
		String[] url_arr = url.split("/");
		String depth = url_arr[url_arr.length - 1];
		System.out.println("컨트롤러 실행 후 :url=>"+url);
		if(mv != null)
			System.out.println("컨트롤러 실행 후 :mv=>"+mv.getModel().toString());

		if ("test.do".equals(depth)) {
			mv.setViewName("redirect");
			mv.addObject("msg", "테스트 페이지에 접속하셨습니다.");
			mv.addObject("url", "/test/test2.do");
		}
	}
	
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) throws Exception {
		System.out.println("모든 요청 처리 후!  5초 후 response 처리됩니다.");
		// Thread.sleep(5000);

	}
	**/
}
