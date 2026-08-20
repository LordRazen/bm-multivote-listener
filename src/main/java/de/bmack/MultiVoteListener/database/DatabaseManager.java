package de.bmack.MultiVoteListener.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Reusable database manager for Bukkit/Paper plugins.
 *
 * This implementation uses HikariCP and reads all connection pool settings from the plugin config.
 */
public class DatabaseManager {

	private final JavaPlugin plugin;
	private HikariDataSource dataSource;
	private boolean ready;

	public DatabaseManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public synchronized void start() throws SQLException {
		close();

		ConfigurationSection databaseSection = plugin.getConfig().getConfigurationSection("database");
		if (databaseSection == null) {
			throw new SQLException("Missing configuration section 'database'.");
		}

		ConfigurationSection hikariSection = databaseSection.getConfigurationSection("hikari");
		if (hikariSection == null) {
			throw new SQLException("Missing configuration section 'database.hikari'.");
		}

		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setJdbcUrl(requireString(databaseSection, "jdbcUrl"));
		hikariConfig.setUsername(requireString(databaseSection, "username"));
		hikariConfig.setPassword(requireString(databaseSection, "password"));
		hikariConfig.setPoolName(hikariSection.getString("poolName", plugin.getName() + "Pool"));
		hikariConfig.setMaximumPoolSize(hikariSection.getInt("maximumPoolSize", 10));
		hikariConfig.setMinimumIdle(hikariSection.getInt("minimumIdle", 2));
		hikariConfig.setConnectionTimeout(hikariSection.getLong("connectionTimeout", 5000L));
		hikariConfig.setIdleTimeout(hikariSection.getLong("idleTimeout", 600000L));
		hikariConfig.setMaxLifetime(hikariSection.getLong("maxLifetime", 1800000L));
		hikariConfig.setKeepaliveTime(hikariSection.getLong("keepaliveTime", 300000L));
		hikariConfig.setValidationTimeout(hikariSection.getLong("validationTimeout", 3000L));
		hikariConfig.setInitializationFailTimeout(hikariSection.getLong("initializationFailTimeout", 1L));
		hikariConfig.setLeakDetectionThreshold(hikariSection.getLong("leakDetectionThreshold", 0L));
		hikariConfig.setAutoCommit(hikariSection.getBoolean("autoCommit", true));

		String connectionTestQuery = hikariSection.getString("connectionTestQuery", "");
		if (!connectionTestQuery.isBlank()) {
			hikariConfig.setConnectionTestQuery(connectionTestQuery);
		}

		try {
			this.dataSource = new HikariDataSource(hikariConfig);
		} catch (RuntimeException e) {
			throw new SQLException("Failed to initialize connection pool.", e);
		}
		int validationSeconds = Math.max(1, (int) Math.ceil(hikariConfig.getValidationTimeout() / 1000.0));
		try (Connection connection = this.dataSource.getConnection()) {
			if (!connection.isValid(validationSeconds)) {
				throw new SQLException("Database connection validation failed.");
			}
		}

		this.ready = true;
		plugin.getLogger().info("Connection pool to database established successfully.");
	}

	public synchronized void close() {
		this.ready = false;
		if (this.dataSource != null) {
			this.dataSource.close();
			this.dataSource = null;
		}
	}

	public boolean isReady() {
		return this.ready && this.dataSource != null && !this.dataSource.isClosed();
	}

	public Connection getConnection() throws SQLException {
		if (!isReady()) {
			throw new SQLException("DatabaseManager is not ready.");
		}
		return this.dataSource.getConnection();
	}

	public void runMigration(String sql) throws SQLException {
		try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	public void executeUpdate(String sql, StatementPreparer statementPreparer) throws SQLException {
		try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
			if (statementPreparer != null) {
				statementPreparer.accept(statement);
			}
			statement.executeUpdate();
		}
	}

	public <T> T executeQuery(String sql, StatementPreparer statementPreparer, ResultSetExtractor<T> extractor) throws SQLException {
		try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
			if (statementPreparer != null) {
				statementPreparer.accept(statement);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				return extractor.extract(resultSet);
			}
		}
	}

	private String requireString(ConfigurationSection section, String path) throws SQLException {
		String value = section.getString(path);
		if (value == null || value.isBlank()) {
			throw new SQLException("Missing required database config value 'database." + path + "'.");
		}
		return value;
	}

	@FunctionalInterface
	public interface StatementPreparer {
		void accept(PreparedStatement statement) throws SQLException;
	}

	@FunctionalInterface
	public interface ResultSetExtractor<T> {
		T extract(ResultSet resultSet) throws SQLException;
	}
}

