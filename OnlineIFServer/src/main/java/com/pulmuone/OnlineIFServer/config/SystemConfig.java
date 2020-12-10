package com.pulmuone.OnlineIFServer.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import com.pulmuone.OnlineIFServer.service.CommonService;
import com.pulmuone.OnlineIFServer.util.RegExHashMap;
 
@Component
public class SystemConfig {
	@Autowired
	CommonService commonService;

    private static RegExHashMap systemMap = null;
    
    private static StopWatch stopWatch = null;
    
    public RegExHashMap getSystemMap() {
    	if(systemMap == null) {
    		systemMap = new RegExHashMap();
    	
        	List<Map> list = commonService.systems();
        	for(Map map : list)
        		systemMap.put(map.get("authkey").toString(), map);
    	}
    	return systemMap;
    }
    
    synchronized public Map getSystemInfo(String key) {
    	return (Map) getSystemMap().get(key);
    }
    
    public void setTime() {
    	stopWatch = new StopWatch();
		stopWatch.start();
    }
    
    public double getTime() {
    	stopWatch.stop();		 
	    return stopWatch.getTotalTimeSeconds();
    }
}
