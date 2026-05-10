package org.rapla.storage.dbsql.tests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class MariadbTest {
    // Override via system properties or env vars when running this scratchpad against a real DB:
    //   -Drapla.mariadb.url=jdbc:mariadb://host:3306/db
    //   -Drapla.mariadb.user=USER
    //   -Drapla.mariadb.password=PASSWORD
    // No defaults are committed — supply credentials at run time.
    public static void main(String[] argv) {
        String url = property("rapla.mariadb.url", "MARIADB_URL");
        String user = property("rapla.mariadb.user", "MARIADB_USER");
        String password = property("rapla.mariadb.password", "MARIADB_PASSWORD");
        if (url == null || user == null || password == null) {
            System.err.println("Set rapla.mariadb.{url,user,password} system properties (or MARIADB_{URL,USER,PASSWORD} env vars) before running.");
            return;
        }

        Properties connConfig = new Properties();
        connConfig.setProperty("user", user);
        connConfig.setProperty("password", password);

        try (Connection conn = DriverManager.getConnection(url, connConfig)) {
            conn.createStatement().execute("SELECT * FROM rapla_resource");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String property(String sysProp, String envVar) {
        String v = System.getProperty(sysProp);
        return v != null ? v : System.getenv(envVar);
    }
}
