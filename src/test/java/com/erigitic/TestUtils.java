package com.erigitic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class TestUtils {
    private static final HikariConfig config = new HikariConfig();
    private static final HikariDataSource ds;

    static {
        config.setJdbcUrl("jdbc:mysql://localhost:3306/totaleconomytest");
        config.setUsername("te_dev");
        config.setPassword("Password1!");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public static void seedCurrencies() {
        try (Connection conn = TestUtils.getConnection()) {
            String insertDollarCurrency = "INSERT INTO te_currency\n"
                + "VALUES(1, 'Dollar', 'Dollars', '$', true, true)";
            String insertEuroCurrency = "INSERT INTO te_currency\n"
                + "VALUES(2, 'Euro', 'Euros', 'E', false, false)";

            Statement statement = conn.createStatement();
            statement.execute(insertDollarCurrency);
            statement.execute(insertEuroCurrency);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void seedUser() {
        try (Connection conn = TestUtils.getConnection()) {
            String insertAccount = "INSERT INTO te_user\n"
                + "VALUES('62694fb0-07cc-4396-8d63-4f70646d75f0', 'Display Name');";
            String insertBalance = "INSERT INTO te_balance\n"
                + "VALUES('62694fb0-07cc-4396-8d63-4f70646d75f0', 1, 123)";
            String insertBalance2 = "INSERT INTO te_balance\n"
                + "VALUES('62694fb0-07cc-4396-8d63-4f70646d75f0', 2, 456)";

            Statement statement = conn.createStatement();
            statement.execute(insertAccount);
            statement.execute(insertBalance);
            statement.execute(insertBalance2);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void seedUsers() {
        try (Connection conn = TestUtils.getConnection()) {
            String insertAccount = "INSERT INTO te_user\n"
                + "VALUES('62694fb0-07cc-4396-8d63-4f70646d75f0', 'Display Name');";
            String insertBalance = "INSERT INTO te_balance\n"
                + "VALUES('62694fb0-07cc-4396-8d63-4f70646d75f0', 1, 50)";
            String insertAccount2 = "INSERT INTO te_user\n"
                + "VALUES('551fe9be-f77f-4bcb-81db-548db6e77aea', 'Display Name 2');";
            String insertBalance2 = "INSERT INTO te_balance\n"
                + "VALUES('551fe9be-f77f-4bcb-81db-548db6e77aea', 1, 100)";

            Statement statement = conn.createStatement();
            statement.execute(insertAccount);
            statement.execute(insertBalance);
            statement.execute(insertAccount2);
            statement.execute(insertBalance2);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void resetDb() {
        try (Connection conn = TestUtils.getConnection()) {
            String truncateUsers = "DELETE FROM te_user";
            String truncateBalances = "DELETE FROM te_balance";
            String truncateCurrencies = "DELETE FROM te_currency";

            Statement statement = conn.createStatement();
            statement.execute(truncateUsers);
            statement.execute(truncateBalances);
            statement.execute(truncateCurrencies);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}