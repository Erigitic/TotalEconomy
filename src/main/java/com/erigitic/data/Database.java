package com.erigitic.data;

import com.erigitic.TotalEconomy;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.service.sql.SqlService;

public class Database {
    private final TotalEconomy plugin;
    private final Logger logger;
    private final String connectionString;
    private final String database;
    private SqlService sql;

    public Database() {
        plugin = TotalEconomy.getPlugin();
        logger = plugin.getLogger();
        connectionString = plugin.getDefaultConfiguration().getConnectionString();
        database = getDatabase();
    }

    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public void setup() {
        if (database.equals("mysql")) {
            Optional<Asset> sqlFileOpt = plugin.getPluginContainer().getAsset("schema/mysql.sql");
            sqlFileOpt.ifPresent(this::runSqlScript);
            return;
        }

        logger.error("Could not find SQL file");
    }

    private DataSource getDataSource() throws SQLException {
        if (sql == null) {
            sql = Sponge.getServiceManager().provide(SqlService.class).get();
        }

        return sql.getDataSource(connectionString);
    }

    private String getDatabase() {
        return connectionString.split("jdbc:")[1].split(":")[0];
    }

    private void runSqlScript(Asset sqlScript) {
        try (Connection conn = getConnection();
             InputStreamReader reader = new InputStreamReader(sqlScript.getUrl().openStream())
        ) {
            logger.info("Successfully connected");

            ScriptRunner runner = new ScriptRunner(conn);
            runner.runScript(reader);

            logger.info("Database successfully setup");
        } catch (SQLException e) {
            logger.error("Unable to connect to the database");
        } catch (IOException e) {
            logger.error("Could not read SQL file");
        }
    }
}
