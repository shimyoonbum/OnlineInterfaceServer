package com.pulmuone.OnlineIFServer.config;

import java.util.Map;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ajp.AjpNioProtocol;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Component
@ConfigurationProperties(prefix="tomcat")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Configuration
public class ContainerConfig {

    private Map ajp;
    
    String ajpProtocol;
    int ajpPort;

    @Bean
    public ServletWebServerFactory servletContainer() {

        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        tomcat.addAdditionalTomcatConnectors(createAjpConnector());

        return tomcat;        
    }

    private Connector createAjpConnector() {
    	ajpProtocol = (String) this.ajp.get("protocol");
    	ajpPort = (int) this.ajp.get("port");
    	
        Connector ajpConnector = new Connector(ajpProtocol);
        AjpNioProtocol protocol = (AjpNioProtocol)ajpConnector.getProtocolHandler();
        ajpConnector.setPort(ajpPort);
        ajpConnector.setSecure(false);
        ajpConnector.setAllowTrace(false);
        ajpConnector.setScheme("http");
        return ajpConnector;
    }
}
