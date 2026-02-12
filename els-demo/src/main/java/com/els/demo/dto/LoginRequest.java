package com.els.demo.dto;

public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final LoginRequest request;

        public Builder() {
            this.request = new LoginRequest();
        }

        public Builder username(String username) {
            this.request.setUsername(username);
            return this;
        }

        public Builder password(String password) {
            this.request.setPassword(password);
            return this;
        }

        public LoginRequest build() {
            return this.request;
        }
    }
}
