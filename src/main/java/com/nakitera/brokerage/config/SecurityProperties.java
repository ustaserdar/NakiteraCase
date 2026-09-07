package com.nakitera.brokerage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "nakitera.security")
public class SecurityProperties {

    private UserCredentials admin = new UserCredentials();
    private List<UserCredentials> customers = new ArrayList<>();

    public UserCredentials getAdmin() {
        return admin;
    }

    public void setAdmin(UserCredentials admin) {
        this.admin = admin;
    }

    public List<UserCredentials> getCustomers() {
        return customers;
    }

    public void setCustomers(List<UserCredentials> customers) {
        this.customers = customers;
    }

    public static class UserCredentials {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
