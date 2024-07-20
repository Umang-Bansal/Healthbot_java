package com.bot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
public class DatabaseId {
    public static Connection getConnection() {
        try {
            String dbURL = "jdbc:mysql://localhost:3306/umang"; // Adjust for your database
            String username = "";
            String password = "";
            return DriverManager.getConnection(dbURL, username, password);
        } catch (SQLException e) {
            System.err.println("Error establishing database connection: " + e.getMessage());
            return null;
        }
    }

}
