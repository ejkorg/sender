package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<IndexThemeFilter> indexThemeFilter() {
        FilterRegistrationBean<IndexThemeFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IndexThemeFilter());
        // Apply to all paths; the filter will quickly skip non-HTML responses.
        reg.addUrlPatterns("/*");
        reg.setName("indexThemeFilter");
        reg.setOrder(Integer.MIN_VALUE + 10);
        return reg;
    }
}
