package com.example.reloader;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;

@TestConfiguration
public class TestRestTemplateConfig {

    static class CookieInterceptor implements ClientHttpRequestInterceptor {
        private volatile String cookieHeader = null;

        @Override
        public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            // attach stored cookie if present
            if (cookieHeader != null) {
                request.getHeaders().add(HttpHeaders.COOKIE, cookieHeader);
            }

            ClientHttpResponse response = execution.execute(request, body);

            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies != null && !setCookies.isEmpty()) {
                // store the cookie portion (before ';') of the first Set-Cookie header
                String first = setCookies.get(0);
                String cookiePart = first.split(";")[0].trim();
                this.cookieHeader = cookiePart;
            }

            return response;
        }
    }

    @Bean
    public TestRestTemplate cookieEnabledTestRestTemplate() {
        CookieInterceptor interceptor = new CookieInterceptor();
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .additionalInterceptors(interceptor);
        return new TestRestTemplate(builder);
    }
}
