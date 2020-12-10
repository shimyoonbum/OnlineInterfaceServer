package com.pulmuone.OnlineIFServer.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Component
@ConfigurationProperties(prefix="systems")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemConfigSample {
    private Map security;
    
    public Map getSecurityInfo(String authkey) {
    	Map authMap = (Map) this.security.get("authkey");
    	if(authMap == null)
    		return null;
    	return (Map) authMap.get(authkey);
    }
}
