package com.pulmuone.OnlineIFServer.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

public class EnvironmentPostProcessorSample implements EnvironmentPostProcessor {
	private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		//Resource path = new ClassPathResource("/config/application.yml");
		String location = System.getProperty("user.dir")+"/config/application.yml";
		Resource path = new PathResource(location);
		List<PropertySource<?>> propertySourceList = loadYaml(path);
		for(PropertySource<?> propertySource : propertySourceList)
			environment.getPropertySources().addLast(propertySource);
	}

	private List<PropertySource<?>> loadYaml(Resource path) {
		if (!path.exists()) {
			throw new IllegalArgumentException("Resource " + path + " does not exist");
		}
		try {
			return this.loader.load("custom-resource", path);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to load yaml configuration from " + path, ex);
		}
	}
}
