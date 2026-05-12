package org.rapla;

import java.util.Arrays;
/** Object that encapsulates the login information. 
 *  For admin users it is possible to connect as an other user.  */
public class ConnectInfo {
    String username;
    char[] password;
    String connectAs;
    String accessToken;
    String refreshToken;
    public ConnectInfo(String username, char[] password, String connectAs) {
        this.username = username;
        this.password = password;
        this.connectAs = connectAs;
    }

    public ConnectInfo(String username, char[] password) {
        this( username, password, null);
    }

    public static ConnectInfo withAccessToken(String accessToken, String refreshToken) {
        ConnectInfo info = new ConnectInfo(null, null, null);
        info.accessToken = accessToken;
        info.refreshToken = refreshToken;
        return info;
    }

    public String getUsername() {
        return username;
    }
    public char[] getPassword() {
        return password;
    }
    public String getConnectAs() {
        return connectAs;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public String toString() {
        if (accessToken != null) {
            return "ConnectInfo [accessToken=***]";
        }
        return "ReconnectInfo [username=" + username + ", password=" + Arrays.toString(password) + ", connectAs=" + connectAs + "]";
    }
}
