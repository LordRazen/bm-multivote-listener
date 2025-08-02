package de.bmack.MultiVoteListener;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import de.bmack.MultiVoteListener.utils.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import net.milkbowl.vault.economy.Economy;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;

import de.bmack.MultiVoteListener.commands.CommandHandler;
import de.bmack.MultiVoteListener.listeners.VoteEventListener;

import static org.bukkit.Bukkit.getOfflinePlayer;

/**
 * Main class of the MultiVotePlugin
 * 
 * @author 		bmack94
 */
public class MultiVoteListener extends JavaPlugin {

	public static Connection connection;
	public FileConfiguration config;

	// Soft depend indicators and instances
	private boolean enableVaultEco = false;
	private boolean enableVaultPerm = false;
	private boolean enablePlayerPoints = false;

	private boolean isSpigot = false;

	private Economy economy = null;
	private PlayerPoints points = null;

	// Our listeners
	VoteEventListener newVote = null;

	/**
	 * Implements the onEnable method invoked when plugin is enabled.
	 */
	@Override
	public void onEnable() {
		config = getConfig();
		String host = config.getString("database.host");
		int port = config.getInt("database.port");
		String dbName = config.getString("database.name");
		String user = config.getString("database.user");
		String password = config.getString("database.password");
		try {
			connection = DriverManager.getConnection(
					"jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&autoReconnect=true",
					user,
					password
			);
			Logger.info("[MultiVoteListener] Connection to Database established!");

			// Create the mails table if it doesn't exist
			String createTableSQL = "CREATE TABLE IF NOT EXISTS votes (" +
					"id INT(11) NOT NULL AUTO_INCREMENT," +
					"date DATE NOT NULL DEFAULT CURRENT_TIMESTAMP ," +
					"time TIME NOT NULL DEFAULT CURRENT_TIMESTAMP ," +
					"uuid VARCHAR(36) NOT NULL ," +
					"username VARCHAR(36) NOT NULL ," +
					"votesite TEXT NOT NULL , PRIMARY KEY (`id`)" +
					")";
			try (Statement stmt = connection.createStatement()) {
				stmt.execute(createTableSQL);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Logger.info("[MultiVoteListener] No Connection to Database!");
		}

		if (!new File(getDataFolder(), "config.yml").exists()) {
			saveResource("config.yml", false);
			//TODO: Created config message
		}
		if (!loadConfig()) {
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}
		//TODO: Load config message

		// Check for Vault
		if (this.getServer().getPluginManager().getPlugin("Vault") != null) {
			RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
			if (economyProvider != null) {
				economy = economyProvider.getProvider();
				enableVaultEco = true;
				Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: Vault - Economy hook found.");
			} else {
				enableVaultEco = false;
				Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: Vault - not found. money reward disabled.");
			}
		} else {
			Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "Dependency: Vault - not found. Disabling MultiVoteListener.");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Check for player points
		if (this.getServer().getPluginManager().getPlugin("PlayerPoints") != null) {
			points = PlayerPoints.class.cast(this.getServer().getPluginManager().getPlugin("PlayerPoints"));
			if (points != null) {
				enablePlayerPoints = true;
				Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: PlayerPoints - found.");
			}
		} else {
			Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: PlayerPoints - not found. points reward disabled.");
		}

		// Check if server is Spigot
		try {

			Class.forName("org.bukkit.Bukkit").getMethod("spigot");
			System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "Running on Spigot");
			isSpigot = true;
		} catch (NoSuchMethodException cfne) {
			Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "Running on CraftBukkit");
		} catch (ClassNotFoundException cnfe) {
			// This condition should not be met at all.
			// The plugin should instantly crash due to missing Bukkit-API
			Logger.info(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "No bukkit found at all - disabling");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		this.newVote = new VoteEventListener(this);
		this.getCommand("mvote").setExecutor(new CommandHandler(this));

		LocalDate today = LocalDate.now();
		if (today.getDayOfMonth() == 1) {
			voteCheck(Bukkit.getConsoleSender());
		}
	}

	/**
	 * Load config file from disk.
	 * The method handles exceptions that may occur during file load using getConfig()
	 *
	 * @return true if config has benn loaded, false on error.
	 */
	public boolean loadConfig() {
		//TODO: Handle invalid configuation file exception
		try {
			getConfig();
			//this.config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
		} catch (Exception e) {
			Logger.info("[MultiVoteListener] Error in config. Plugin will not be enabled");
			return false;
		}
		return true;

	}

	/**
	 * Returns the Vault API - Economy used by MultiVoteListener.
	 *
	 * @return Vault Instance or null, if Vault is not active
	 * @see net.milkbowl.vault.economy.Economy
	 */
	public Economy getEcoAPI() {
		return this.economy;
	}

	/**
	 * Returns the Vault API used by MultiVoteListener.
	 *
	 * @return PlayerPoints Instance or null, if Vault is not active
	 * @see org.black_ixx.playerpoints.PlayerPointsAPI
	 */
	public PlayerPointsAPI getPointsAPI() {
		return this.points.getAPI();
	}

	/**
	 * Indicates if Vault is currently used for Economy
	 *
	 * @return true, if Vault is used. Otherwise false
	 */
	public boolean isEnabledVaultEco() {
		return this.enableVaultEco;
	}

	/**
	 * Indicates if Vault is currently used for Permissions
	 *
	 * @return true, if Vault is used. Otherwise false
	 */
	public boolean isEnabledVaultPerm() {
		return this.enableVaultPerm;
	}

	/**
	 * Indicates if PlayerPoints is currently used
	 *
	 * @return true, if Vault is used. Otherwise false.
	 */
	public boolean isEnabledPoints() {
		return this.enablePlayerPoints;
	}

	/**
	 * Indicates if plugin runs in a Spigot Environment.
	 * Depending on the type of server (Spigot/CraftBukkit) some extended functionality is used, e.g.
	 * if running in a Spigot environment the plugin will make use of the Bungee Chat API which is not
	 * available in CraftBukkit.
	 *
	 * @return true, if server is Spigot is used. Otherwise false.
	 */
	public boolean isSpigot() {
		return isSpigot;

	}

	public void voteCheck(CommandSender sender) {
		LocalDate today = LocalDate.now();
		YearMonth previousMonthOfThisYear = YearMonth.from(today.minusMonths(1));
		int monthValuePreviousMonth = previousMonthOfThisYear.getMonthValue();
		int yearValueOfPreviousMonth = previousMonthOfThisYear.getYear();
		int minDaysNeeded = previousMonthOfThisYear.lengthOfMonth() - 1;

		String sql = """
            SELECT
            	uuid
            FROM
            	votes
            WHERE
            	MONTH(date) = ? AND YEAR(date) = ? AND votesite = ?
            GROUP BY
            	uuid
            HAVING
            	COUNT(DISTINCT DATE(date)) >= ?
            ORDER BY
            	uuid DESC;
            """;

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, monthValuePreviousMonth);
			stmt.setInt(2, yearValueOfPreviousMonth);
			stmt.setString(3, "minecraft-server.eu");
			stmt.setInt(4, minDaysNeeded);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					String uuidString = rs.getString("uuid");
					setPermissionViaConsole(this,uuidString, monthValuePreviousMonth, yearValueOfPreviousMonth);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp sync");
		String message = Tools.reformatColorCodes(this.getConfig().getString("message_prefix") + this.getConfig().getString("messages.given_trophies"));
		Month monthAsEnum = Month.of(monthValuePreviousMonth);
		String monthName = monthAsEnum.getDisplayName(TextStyle.FULL, Locale.GERMAN);
		message = message.replaceAll("%month%", monthName);
		message = message.replaceAll("%year%", yearValueOfPreviousMonth + "");

		sender.sendMessage(message);
	}

	public void receiveTrophies(JavaPlugin plugin, UUID playerUUID, CommandSender commandSender) {
		Player player = Bukkit.getPlayer(playerUUID);
		if (player == null || !player.isOnline()) {
			String message = Tools.reformatColorCodes(this.getConfig().getString("message_prefix") + this.getConfig().getString("messages.player_not_online"));
			commandSender.sendMessage(message);
		} else{

			ArrayList<String> allTrophyPermissionsOfPlayer = new ArrayList<String>();

			String sql = """
					SELECT permission FROM luckperms_user_permissions WHERE permission LIKE "blockminers.votepokal.%" AND uuid = ?;
					""";

			try (PreparedStatement stmt = connection.prepareStatement(sql)) {
				stmt.setString(1, playerUUID.toString());

				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						String permission = rs.getString("permission");
						allTrophyPermissionsOfPlayer.add(permission);
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}

			if (!allTrophyPermissionsOfPlayer.isEmpty()) {
				for (String trophyPermission : allTrophyPermissionsOfPlayer) {
					String monthYearString = trophyPermission.split("\\.")[2];
					int month = Integer.parseInt(monthYearString.split("_")[0]);
					int year = Integer.parseInt(monthYearString.split("_")[1]);
					giveTrophy(plugin, playerUUID, month, year);
				}
			}

			String message;
			if (allTrophyPermissionsOfPlayer.isEmpty()) {
				message = Tools.reformatColorCodes(this.getConfig().getString("message_prefix") + this.getConfig().getString("messages.not_voted_enough"));
			} else {
				message = Tools.reformatColorCodes(this.getConfig().getString("message_prefix") + this.getConfig().getString("messages.thank_you_for_voting_regularly"));
			}
			player.sendMessage(message);
		}
	}

	private void giveTrophy(JavaPlugin plugin, UUID playerUUID, int month, int year){
		OfflinePlayer user = getOfflinePlayer(playerUUID);
		String username = user.getName();

		Month monthAsEnum = Month.of(month);
		String monthName = monthAsEnum.getDisplayName(TextStyle.FULL, Locale.GERMAN);

		String give_command = Tools.reformatColorCodes(plugin.getConfig().getString("trophy_head_command"));
		give_command = give_command.replaceAll("%player_name%", username);
		give_command = give_command.replaceAll("%month%", monthName);
		give_command = give_command.replaceAll("%year%", year + "");

		removePermissionViaConsole(plugin, playerUUID.toString(),month, year);
		Player player = Bukkit.getPlayer(playerUUID);

		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), give_command);
		String message = Tools.reformatColorCodes(this.getConfig().getString("message_prefix") + this.getConfig().getString("messages.vote_trophy_reward_notice"));
		message = message.replaceAll("%month%", monthName);
		message = message.replaceAll("%year%", year + "");
		player.sendMessage(message);

		int money_reward = plugin.getConfig().getInt("monthly_vote_reward_money");
		getEcoAPI().depositPlayer(user,money_reward);

		String money_message = Tools.reformatColorCodes(this.getConfig().getString("message_prefix") + this.getConfig().getString("messages.player_money_reward"));
		money_message = money_message.replaceAll("%amount%", this.getConfig().getString("monthly_vote_reward_money"));
		player.sendMessage(money_message);
	}

	public void setPermissionViaConsole(JavaPlugin plugin, String playerUUIDString, int monthValue, int yearValue) {
		String playerName = getOfflinePlayer(UUID.fromString(playerUUIDString)).getName();
		String permission = "blockminers.votepokal." + monthValue + "_" + yearValue;
		String command = "lp user " + playerName + " permission set " + permission;
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
	}

	public void removePermissionViaConsole(JavaPlugin plugin, String playerUUIDString, int monthValue, int yearValue) {
		String playerName = getOfflinePlayer(UUID.fromString(playerUUIDString)).getName();
		String permission = "blockminers.votepokal." + monthValue + "_" + yearValue;
		String command = "lp user " + playerName + " permission unset " + permission;
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp sync");
	}

}


