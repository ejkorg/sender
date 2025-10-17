package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.ldap")
public class LdapProperties {
    private boolean enabled = false;
    private String urls;           // e.g., ldap://ad.onsemi.com:389
    private String base;           // e.g., dc=onsemi,dc=com
    private String managerDn;      // e.g., CN=svc_ldap,OU=Service Accounts,DC=onsemi,DC=com
    private String managerPassword;
    private String userSearchBase; // e.g., OU=Users
    private String userSearchFilter = "(sAMAccountName={0})"; // AD default

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrls() { return urls; }
    public void setUrls(String urls) { this.urls = urls; }
    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }
    public String getManagerDn() { return managerDn; }
    public void setManagerDn(String managerDn) { this.managerDn = managerDn; }
    public String getManagerPassword() { return managerPassword; }
    public void setManagerPassword(String managerPassword) { this.managerPassword = managerPassword; }
    public String getUserSearchBase() { return userSearchBase; }
    public void setUserSearchBase(String userSearchBase) { this.userSearchBase = userSearchBase; }
    public String getUserSearchFilter() { return userSearchFilter; }
    public void setUserSearchFilter(String userSearchFilter) { this.userSearchFilter = userSearchFilter; }
}
