package org.rapla.storage.dbsql.tests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class MariadbTest {
    public static void main(String[] argv) {
        // Connection Configuration
        Properties connConfig = new Properties();
        connConfig.setProperty("user", "wochenplan");
        connConfig.setProperty("password", "iM8bThBrR");

        // Create Connection to the database
        try (Connection conn = DriverManager.getConnection("jdbc:mariadb://192.168.20.33:3306/raplawochenplan", connConfig)) {
            boolean execute = conn.createStatement().execute("SELECT * FROM rapla_resource");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
