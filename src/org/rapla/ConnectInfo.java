package org.rapla;

import java.util.Arrays;
/** Object that encapsulates the login information. 
 *  For admin users it is possible to connect as an other user. 
 */
public class ConnectInfo {
    String username;
    char[] password;
    String connectAs;
    public ConnectInfo(String username, char[] password, String connectAs) {
        this.username = username;
        this.password = password;
        this.connectAs = connectAs;
    }
    
    public ConnectInfo(String username, char[] password) {
        this( username, password, null);
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

    @Override
    public String toString() {
        return "ReconnectInfo [username=" + username + ", password=" + Arrays.toString(password) + ", connectAs=" + connectAs + "]";
    }
}
