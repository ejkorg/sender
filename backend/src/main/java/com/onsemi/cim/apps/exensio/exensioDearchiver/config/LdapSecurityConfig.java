package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import com.onsemi.cim.apps.exensio.exensioDearchiver.security.LocalAuthoritiesMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "security.ldap", name = "enabled", havingValue = "true")
public class LdapSecurityConfig {

    @Bean
    public DefaultSpringSecurityContextSource contextSource(LdapProperties props) {
        DefaultSpringSecurityContextSource source = new DefaultSpringSecurityContextSource(List.of(props.getUrls()), props.getBase());
        source.setUserDn(props.getManagerDn());
        source.setPassword(props.getManagerPassword());
        return source;
    }

    @Bean
    public LdapAuthoritiesPopulator authoritiesPopulator(LocalAuthoritiesMapper mapper) {
        // Populator that ignores LDAP groups and uses local refdb roles instead
        return (dirContextOperations, username) -> new java.util.HashSet<>(mapper.loadAuthorities(username));
    }

    @Bean
    public LocalAuthoritiesMapper localAuthoritiesMapper(com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService refDbService) {
        return new LocalAuthoritiesMapper(refDbService);
    }

    @Bean
    public AuthenticationManager ldapAuthManager(HttpSecurity http,
                                                 DefaultSpringSecurityContextSource contextSource,
                                                 LdapAuthoritiesPopulator authoritiesPopulator,
                                                 LdapProperties props) throws Exception {
        AuthenticationManagerBuilder auth = http.getSharedObject(AuthenticationManagerBuilder.class);
        auth
            .ldapAuthentication()
            .userSearchBase(props.getUserSearchBase())
            .userSearchFilter(props.getUserSearchFilter())
            .contextSource(contextSource)
            .ldapAuthoritiesPopulator(authoritiesPopulator);
        return auth.build();
    }

    @Bean
    public SecurityFilterChain ldapFilterChain(HttpSecurity http, AuthenticationManager authManager) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/internal/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .authenticationManager(authManager)
            .formLogin(form -> form.loginProcessingUrl("/login").permitAll())
            .httpBasic();
        return http.build();
    }
}
