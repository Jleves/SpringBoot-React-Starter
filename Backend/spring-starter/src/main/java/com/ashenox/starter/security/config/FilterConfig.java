package com.ashenox.starter.security.config;



import com.ashenox.starter.log.filter.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestLoggingFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Primero de todos
        registrationBean.addUrlPatterns("/*"); // Todas las URLs
        return registrationBean;
    }
}
