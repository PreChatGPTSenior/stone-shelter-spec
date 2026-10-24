package lab.stoneshelter.controllers;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class StoneChatWebConfiguration implements WebMvcConfigurer {
    private final StoneChatBindingInterceptor interceptor;
    public StoneChatWebConfiguration(StoneChatBindingInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).addPathPatterns("/api/v1/chat/**"); }
}
