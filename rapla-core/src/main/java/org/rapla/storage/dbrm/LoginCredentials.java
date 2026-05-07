package org.rapla.storage.dbrm;

public class LoginCredentials {
    private String username;
    private String password;
    private String connectAs;
    public LoginCredentials()
    {
    }
    public LoginCredentials(String username, String password, String connectAs) {
        super();
        this.username = username;
        this.password = password;
        this.connectAs = connectAs;
    }
    
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
    public String getConnectAs() {
        return connectAs;
    }
}
