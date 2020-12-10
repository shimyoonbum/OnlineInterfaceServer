package com.pulmuone.OnlineIFServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

///import org.springframework.cloud.context.config.annotation.RefreshScope;

@SpringBootApplication
///@RefreshScope
@EnableScheduling
//@EnableAsync
public class RestApplication {

	public static void main(String[] args) {
		SpringApplication.run(RestApplication.class, args);
		//new SpringApplicationBuilder(RestApplication.class)
		//	.properties( "spring.config=" + "classpath:/application.yml" + ", file:D:/work/springboot/OnlineIFServer/src/main/resources/application.yml" )
		//	.run(args);

	}

}
