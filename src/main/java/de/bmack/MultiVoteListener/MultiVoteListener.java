package de.bmack.MultiVoteListener;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import de.bmack.MultiVoteListener.database.DatabaseManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;

import de.bmack.MultiVoteListener.commands.CommandHandler;
import de.bmack.MultiVoteListener.listeners.VoteEventListener;

/**
 * Main class of the MultiVotePlugin
 *
 * @author 		bmack94
 */
public class MultiVoteListener extends JavaPlugin {

	/** Timezone used for all date based logic (vote dedup, streak, monthly trophy run). */
	public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

	private static final String TROPHY_PERMISSION_PREFIX = "blockminers.votepokal.";
	private static final String TROPHY_VOTESITE = "minecraft-server.eu";

	private DatabaseManager databaseManager;

	// Soft depend indicators and instances
	private boolean enablePlayerPoints = false;

	private Economy economy = null;
	private PlayerPoints points = null;

	private volatile boolean configValid = false;

	// players whose trophies are currently being claimed (prevents double payout)
	private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();

	/**
	 * Implements the onEnable method invoked when plugin is enabled.
	 */
	@Override
	public void onEnable() {
		if (!new File(getDataFolder(), "config.yml").exists()) {
			saveResource("config.yml", false);
		}
		reloadConfig();
		List<String> configProblems = validateConfig();
		if (!configProblems.isEmpty()) {
			configProblems.forEach(problem -> getLogger().severe("Config problem: " + problem));
			getLogger().severe("Invalid config. Disabling plugin.");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}
		configValid = true;

		this.databaseManager = new DatabaseManager(this);
		try {
			databaseManager.start();
			runMigrations();
		} catch (SQLException e) {
			getLogger().severe("Failed to initialize database connection pool. Disabling plugin.");
			getLogger().severe(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Vault is a hard dependency; the economy provider may register later, so it is resolved lazily
		if (resolveEconomy() != null) {
			getLogger().info("SoftDependency: Vault - Economy hook found.");
		} else {
			getLogger().info("SoftDependency: Vault - no economy provider yet. Money rewards start working as soon as one registers.");
		}

		// Check for player points
		if (this.getServer().getPluginManager().getPlugin("PlayerPoints") instanceof PlayerPoints playerPoints) {
			points = playerPoints;
			enablePlayerPoints = true;
			getLogger().info("SoftDependency: PlayerPoints - found.");
		} else {
			getLogger().info("SoftDependency: PlayerPoints - not found. points reward disabled.");
		}

		if (isLuckPermsAvailable()) {
			getLogger().info("SoftDependency: LuckPerms - found.");
		} else {
			getLogger().info("SoftDependency: LuckPerms - not found. Vote trophies disabled.");
		}

		new VoteEventListener(this);
		PluginCommand command = getCommand("mvote");
		if (command == null) {
			getLogger().severe("Command 'mvote' is missing in plugin.yml. Disabling plugin.");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}
		command.setExecutor(new CommandHandler(this));

		// Monthly trophy run: once per month, delayed so that LuckPerms & co. are fully loaded
		YearMonth currentMonth = YearMonth.now(ZONE);
		if (LocalDate.now(ZONE).getDayOfMonth() == 1 && !currentMonth.toString().equals(getLastTrophyRun())) {
			Bukkit.getScheduler().runTaskLater(this, () -> voteCheck(Bukkit.getConsoleSender()), 20L * 30);
		}
	}

	@Override
	public void onDisable() {
		if (this.databaseManager != null) {
			this.databaseManager.close();
		}
	}

	private void runMigrations() throws SQLException {
		databaseManager.runMigration("CREATE TABLE IF NOT EXISTS votes (" +
				"id INT NOT NULL AUTO_INCREMENT," +
				"date DATE NOT NULL," +
				"time TIME NOT NULL," +
				"uuid VARCHAR(36) NOT NULL," +
				"username VARCHAR(36) NOT NULL," +
				"votesite VARCHAR(64) NOT NULL," +
				"PRIMARY KEY (`id`)," +
				"UNIQUE KEY uq_vote_per_day (uuid, votesite, date))");
		// Existing installations: add the unique key afterwards (votesite may still be TEXT, hence the prefix length)
		try {
			databaseManager.runMigration("ALTER TABLE votes ADD UNIQUE KEY uq_vote_per_day (uuid, votesite(64), date)");
			getLogger().info("Added unique key uq_vote_per_day to table 'votes'.");
		} catch (SQLException e) {
			// 1061 = duplicate key name -> already present
			if (e.getErrorCode() != 1061) {
				getLogger().warning("Could not add unique key to table 'votes' (duplicate rows?): " + e.getMessage()
						+ " - falling back to the select-before-insert dedup only.");
			}
		}
	}

	/**
	 * Checks the loaded config for the keys the plugin cannot work without.
	 *
	 * @return list of human readable problems, empty if the config is usable
	 */
	public List<String> validateConfig() {
		List<String> problems = new ArrayList<>();
		FileConfiguration cfg = getConfig();
		if (cfg.getString("message_prefix") == null) {
			problems.add("'message_prefix' is missing");
		}
		if (cfg.getConfigurationSection("messages") == null) {
			problems.add("'messages' section is missing");
		}
		if (cfg.getConfigurationSection("database") == null) {
			problems.add("'database' section is missing");
		}
		ConfigurationSection services = cfg.getConfigurationSection("services");
		if (services == null) {
			problems.add("'services' section is missing");
		} else {
			for (String id : services.getKeys(false)) {
				// a disabled default service (e.g. just "enabled: false") needs no name
				boolean optional = id.equals("default") && !services.getBoolean("default.enabled");
				if (!optional && services.getString(id + ".name") == null) {
					problems.add("'services." + id + ".name' is missing");
				}
			}
		}
		return problems;
	}

	public boolean isConfigValid() {
		return configValid;
	}

	/**
	 * Reloads config.yml from disk and validates it.
	 *
	 * @return list of problems, empty if the config is usable. While the config is invalid votes are ignored.
	 */
	public List<String> reloadAndValidateConfig() {
		reloadConfig();
		List<String> problems = validateConfig();
		configValid = problems.isEmpty();
		return problems;
	}

	/**
	 * Returns the Vault economy, resolving it on first use if it has not been registered yet.
	 *
	 * @return Economy instance or null if no economy provider is registered
	 * @see net.milkbowl.vault.economy.Economy
	 */
	public Economy getEcoAPI() {
		return resolveEconomy();
	}

	private Economy resolveEconomy() {
		if (economy == null) {
			RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
			if (provider != null) {
				economy = provider.getProvider();
			}
		}
		return economy;
	}

	public DatabaseManager getDatabaseManager() {
		return this.databaseManager;
	}

	/**
	 * Returns the PlayerPoints API used by MultiVoteListener.
	 *
	 * @return PlayerPoints API or null, if PlayerPoints is not active
	 * @see org.black_ixx.playerpoints.PlayerPointsAPI
	 */
	public PlayerPointsAPI getPointsAPI() {
		return this.points != null ? this.points.getAPI() : null;
	}

	/**
	 * Indicates if an economy provider is currently available through Vault
	 *
	 * @return true, if money rewards can be paid. Otherwise false
	 */
	public boolean isEnabledVaultEco() {
		return resolveEconomy() != null;
	}

	/**
	 * Indicates if PlayerPoints is currently used
	 *
	 * @return true, if PlayerPoints is used. Otherwise false.
	 */
	public boolean isEnabledPoints() {
		return this.enablePlayerPoints;
	}

	public boolean isLuckPermsAvailable() {
		return getServer().getPluginManager().getPlugin("LuckPerms") != null
				&& Bukkit.getServicesManager().getRegistration(LuckPerms.class) != null;
	}

	private LuckPerms luckPerms() {
		RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
		return provider != null ? provider.getProvider() : null;
	}

	private String prefix() {
		return getConfig().getString("message_prefix", "");
	}

	private String message(String key) {
		return Tools.reformatColorCodes(prefix() + getConfig().getString("messages." + key, ""));
	}

	// ---- monthly trophy state -------------------------------------------------------------------

	private File stateFile() {
		return new File(getDataFolder(), "state.yml");
	}

	private String getLastTrophyRun() {
		return YamlConfiguration.loadConfiguration(stateFile()).getString("last_trophy_run");
	}

	private synchronized void setLastTrophyRun(String yearMonth) {
		YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile());
		state.set("last_trophy_run", yearMonth);
		try {
			state.save(stateFile());
		} catch (IOException e) {
			getLogger().warning("Could not save state.yml: " + e.getMessage());
		}
	}

	private static String trophyPermission(int month, int year) {
		return TROPHY_PERMISSION_PREFIX + month + "_" + year;
	}

	private String monthName(int month) {
		return Month.of(month).getDisplayName(TextStyle.FULL, Locale.GERMAN);
	}

	/**
	 * Grants the trophy permission for the previous month to every player who voted often enough.
	 * The database lookup and the LuckPerms updates run asynchronously, the result is sent to the sender afterwards.
	 */
	public void voteCheck(CommandSender sender) {
		LuckPerms lp = luckPerms();
		if (lp == null) {
			sender.sendMessage(Tools.reformatColorCodes(prefix() + "&4LuckPerms not found, vote trophies are not available."));
			return;
		}

		YearMonth previousMonth = YearMonth.now(ZONE).minusMonths(1);
		int month = previousMonth.getMonthValue();
		int year = previousMonth.getYear();
		int minDaysNeeded = previousMonth.lengthOfMonth() - 1;
		// mark before running so a restart cannot trigger the automatic run again
		setLastTrophyRun(YearMonth.now(ZONE).toString());

		String sql = """
				SELECT uuid
				FROM votes
				WHERE MONTH(date) = ? AND YEAR(date) = ? AND votesite = ?
				GROUP BY uuid
				HAVING COUNT(DISTINCT date) >= ?
				""";

		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			List<UUID> winners = new ArrayList<>();
			try {
				getDatabaseManager().executeQuery(sql, stmt -> {
					stmt.setInt(1, month);
					stmt.setInt(2, year);
					stmt.setString(3, TROPHY_VOTESITE);
					stmt.setInt(4, minDaysNeeded);
				}, rs -> {
					while (rs.next()) {
						try {
							winners.add(UUID.fromString(rs.getString("uuid")));
						} catch (IllegalArgumentException e) {
							getLogger().warning("Ignoring invalid uuid in votes table: " + rs.getString("uuid"));
						}
					}
					return null;
				});
			} catch (SQLException e) {
				getLogger().severe("Failed to run monthly vote check: " + e.getMessage());
				Bukkit.getScheduler().runTask(this, () ->
						sender.sendMessage(Tools.reformatColorCodes(prefix() + "&4Database error during vote check, see console.")));
				return;
			}

			String permission = trophyPermission(month, year);
			List<CompletableFuture<Void>> updates = new ArrayList<>();
			for (UUID uuid : winners) {
				updates.add(lp.getUserManager().modifyUser(uuid, user -> user.data().add(Node.builder(permission).build())));
			}
			try {
				CompletableFuture.allOf(updates.toArray(new CompletableFuture[0])).join();
			} catch (RuntimeException e) {
				getLogger().severe("Failed to set trophy permissions: " + e.getMessage());
			}

			String text = message("given_trophies")
					.replace("%month%", monthName(month))
					.replace("%year%", String.valueOf(year));
			Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(text));
		});
	}

	/**
	 * Pays out all trophies a player has permissions for. The permissions are removed (and saved) before anything is
	 * paid, and a player can only have one claim in flight, so repeated calls cannot pay twice.
	 */
	public void receiveTrophies(UUID playerUUID, CommandSender commandSender) {
		Player player = Bukkit.getPlayer(playerUUID);
		if (player == null || !player.isOnline()) {
			commandSender.sendMessage(message("player_not_online"));
			return;
		}
		LuckPerms lp = luckPerms();
		if (lp == null) {
			commandSender.sendMessage(Tools.reformatColorCodes(prefix() + "&4LuckPerms not found, vote trophies are not available."));
			return;
		}
		if (!claiming.add(playerUUID)) {
			return;
		}

		lp.getUserManager().loadUser(playerUUID).thenCompose(user -> {
			List<int[]> claims = new ArrayList<>();
			List<Node> toRemove = new ArrayList<>();
			for (PermissionNode node : user.getNodes(NodeType.PERMISSION)) {
				if (!node.getValue() || node.hasExpired() || !node.getKey().startsWith(TROPHY_PERMISSION_PREFIX)) {
					continue;
				}
				int[] monthYear = parseTrophy(node.getKey().substring(TROPHY_PERMISSION_PREFIX.length()));
				if (monthYear == null) {
					getLogger().warning("Ignoring malformed trophy permission: " + node.getKey());
					continue;
				}
				toRemove.add(node);
				claims.add(monthYear);
			}
			toRemove.forEach(node -> user.data().remove(node));
			return lp.getUserManager().saveUser(user).thenApply(ignored -> claims);
		}).whenComplete((claims, error) -> Bukkit.getScheduler().runTask(this, () -> {
			try {
				if (error != null) {
					getLogger().severe("Failed to claim trophies for " + playerUUID + ": " + error.getMessage());
					commandSender.sendMessage(Tools.reformatColorCodes(prefix() + "&4Trophies could not be claimed, see console."));
					return;
				}
				Player online = Bukkit.getPlayer(playerUUID);
				if (online == null) {
					// the permissions are already removed, so log the lost claim for manual handling
					getLogger().warning("Player " + playerUUID + " went offline while claiming " + claims.size() + " trophies.");
					return;
				}
				for (int[] monthYear : claims) {
					giveTrophy(online, monthYear[0], monthYear[1]);
				}
				online.sendMessage(message(claims.isEmpty() ? "not_voted_enough" : "thank_you_for_voting_regularly"));
			} finally {
				claiming.remove(playerUUID);
			}
		}));
	}

	/** @return {month, year} or null if the permission suffix is not of the form M_YYYY */
	private static int[] parseTrophy(String suffix) {
		String[] parts = suffix.split("_");
		if (parts.length != 2) {
			return null;
		}
		try {
			int month = Integer.parseInt(parts[0]);
			int year = Integer.parseInt(parts[1]);
			if (month < 1 || month > 12) {
				return null;
			}
			return new int[] { month, year };
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void giveTrophy(Player player, int month, int year) {
		String monthName = monthName(month);

		String giveCommand = Tools.reformatColorCodes(getConfig().getString("trophy_head_command", ""))
				.replace("%player_name%", player.getName())
				.replace("%month%", monthName)
				.replace("%year%", String.valueOf(year));
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), giveCommand);

		player.sendMessage(message("vote_trophy_reward_notice")
				.replace("%month%", monthName)
				.replace("%year%", String.valueOf(year)));

		int moneyReward = getConfig().getInt("monthly_vote_reward_money");
		Economy eco = getEcoAPI();
		if (eco != null && moneyReward > 0) {
			eco.depositPlayer(player, moneyReward);
			player.sendMessage(message("player_money_reward").replace("%amount%", String.valueOf(moneyReward)));
		}
	}

}
