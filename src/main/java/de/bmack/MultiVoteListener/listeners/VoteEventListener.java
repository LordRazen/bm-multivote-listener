package de.bmack.MultiVoteListener.listeners;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import de.bmack.MultiVoteListener.MultiVoteListener;
import de.bmack.MultiVoteListener.Tools;

import de.bmack.MultiVoteListener.UrlBroadcast;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.vexsoftware.votifier.model.VotifierEvent;
import com.vexsoftware.votifier.model.Vote;

/**
 * Listener implementation for Vote Events.
 *
 * The listener will catch and handle VotifierEvents.
 *
 * @author		bmack94
 */
public class VoteEventListener implements Listener {

	// URL regex to check config
	private static final Pattern URL_PATTERN = Pattern.compile("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
	// Only plain Minecraft names are accepted: the name is inserted into console commands
	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

	private final MultiVoteListener plugin;

	/**
     * Constructor. Registers the listener.
     *
     * @param		plugin
     */
	public VoteEventListener(MultiVoteListener plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

	/**
     * EventHandler
     * Actions happening on VotifierEvents.
     *
     * Service lookup and normalisation run on the calling (main) thread, the database work runs asynchronously
     * and the rewards are handed out on the main thread again.
     *
     * @param		e
     */
	@EventHandler(priority = EventPriority.NORMAL)
	public void onVote(VotifierEvent e) {
		if (!plugin.isConfigValid()) {
			plugin.getLogger().warning("Config is invalid, vote ignored. Fix config.yml and run /mvote reload.");
			return;
		}

		FileConfiguration config = plugin.getConfig();
		Vote vote = e.getVote();
		String incomingService = vote.getServiceName();
		String username = vote.getUsername();

		if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
			plugin.getLogger().warning("Vote from service " + incomingService + " ignored: invalid username '" + username + "'");
			return;
		}

		String serviceId = resolveServiceId(config, incomingService);
		if (serviceId == null) {
			return;
		}
		String service = config.getString("services." + serviceId + ".name", incomingService);

		// try to fetch UUID
		OfflinePlayer user = Bukkit.getOfflinePlayer(username);
		UUID uuid = user.getUniqueId();

		ZonedDateTime zdt = parseVoteTime(vote);
		LocalDate voteDate = zdt.toLocalDate();
		LocalTime voteTime = zdt.toLocalTime();

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				if (!saveVoteIfNotExists(uuid.toString(), username, service, voteDate, voteTime)) {
					plugin.getLogger().info("User " + username + " has already voted on " + service + " at " + voteDate + " " + voteTime);
					return;
				}
			} catch (SQLException exception) {
				plugin.getLogger().severe("Database error while checking/inserting vote from user " + username + " on service " + service
						+ " at " + voteDate + " " + voteTime + ": " + exception.getMessage());
				return;
			}

			// The vote is stored at this point; a failing streak lookup must never block the base reward.
			int streakDays = 0;
			try {
				streakDays = calculateStreakDays(uuid.toString(), service, voteDate);
			} catch (RuntimeException exception) {
				plugin.getLogger().severe("Error while calculating vote streak for user " + username + " on service " + service
						+ ", continuing without streak bonus: " + exception.getMessage());
			}

			int finalStreakDays = streakDays;
			Bukkit.getScheduler().runTask(plugin, () -> giveRewards(uuid, username, service, serviceId, finalStreakDays));
		});
	}

	/**
	 * Finds the config id of the service a vote belongs to.
	 *
	 * @return the id (a key below "services"), or null if the vote has to be ignored
	 */
	private String resolveServiceId(FileConfiguration config, String incomingService) {
		ConfigurationSection services = config.getConfigurationSection("services");
		if (services != null) {
			for (String id : services.getKeys(false)) {
				if (id.equals("default") || !incomingService.equals(services.getString(id + ".name"))) {
					continue;
				}
				if (!services.getBoolean(id + ".enabled", true)) {
					plugin.getLogger().info("Vote from disabled service ignored: " + incomingService);
					return null;
				}
				return id;
			}
		}

		if (config.isSet("services.default") && config.getBoolean("services.default.enabled")) {
			return "default";
		}
		plugin.getLogger().info("Vote from unknown service ignored: " + incomingService + " (default service is disabled in config)");
		return null;
	}

	/**
	 * Normalizes the vote timestamp.
	 *
	 * The timezone handling is service-name-keyed by observation:
	 * minecraft-server.eu sends the Berlin wall clock time as epoch, so it has to be read as GMT,
	 * every other service sends real epoch values which are read as Europe/Berlin.
	 * An unparsable timestamp falls back to the current time.
	 */
	private ZonedDateTime parseVoteTime(Vote vote) {
		Instant instant;
		try {
			String timestamp = vote.getTimeStamp().trim();
			long ts = Long.parseLong(timestamp);
			if (timestamp.length() == 10) {
				ts = ts * 1000;
			}
			instant = Instant.ofEpochMilli(ts);
		} catch (RuntimeException exception) {
			plugin.getLogger().warning("Vote from " + vote.getServiceName() + " has an invalid timestamp '" + vote.getTimeStamp()
					+ "', using the current time.");
			instant = Instant.now();
		}
		if ("minecraft-server.eu".equals(vote.getServiceName())) {
			return instant.atZone(ZoneId.of("GMT"));
		}
		return instant.atZone(MultiVoteListener.ZONE);
	}

	/**
	 * Hands out all rewards of a stored vote. Must run on the main thread.
	 */
	private void giveRewards(UUID uuid, String username, String service, String serviceId, int streakDays) {
		FileConfiguration config = plugin.getConfig();
		String servicePath = "services." + serviceId;

		int moneyReward = config.getInt(servicePath + ".money");
		int pointsReward = config.getInt(servicePath + ".points");
		List<String> onlineCommands = config.getStringList(servicePath + ".online_commands");
		List<String> offlineCommands = config.getStringList(servicePath + ".offline_commands");
		boolean userMessage = config.isSet(servicePath + ".usermessage");

		String serviceUrl = config.getString(servicePath + ".url");
		if (serviceUrl == null || !URL_PATTERN.matcher(serviceUrl).matches()) {
			serviceUrl = null;
		}

		OfflinePlayer user = Bukkit.getOfflinePlayer(uuid);
		Player player = user.getPlayer();
		String logPrefix = Tools.stripColorCodes(config.getString("message_prefix", "")) + "Service: " + service + "/Player: " + username + " - ";

		// broadcast
		broadcast(format("messages.broadcast_vote_success", username, service), serviceUrl);

		// General thx message + usermessage if defined for service
		if (player != null) {
			player.sendMessage(format("messages.player_vote_success", username, service).replace("%amount%", String.valueOf(moneyReward)));
			if (userMessage) {
				player.sendMessage(Tools.reformatColorCodes(config.getString("message_prefix", "") + config.getString(servicePath + ".usermessage")));
			}
		}

		// vault action
		Economy economy = plugin.getEcoAPI();
		if (economy != null) {
			int streakBonus = streakDays > 1 ? (streakDays - 1) * config.getInt("vote_streak.bonus_per_day") : 0;
			if (economy.hasAccount(user) && moneyReward + streakBonus > 0) {
				economy.depositPlayer(user, moneyReward + streakBonus);

				// base money message only if there is a base reward (the streak bonus has its own message)
				if (moneyReward > 0 && player != null && !userMessage) {
					player.sendMessage(format("messages.player_money_reward", username, service).replace("%amount%", String.valueOf(moneyReward)));
				}

				if (streakBonus > 0) {
					// sent even if the service has a usermessage: that text only describes the base reward
					if (player != null) {
						player.sendMessage(format("messages.player_streak_bonus", username, service)
								.replace("%days%", String.valueOf(streakDays))
								.replace("%amount%", String.valueOf(streakBonus)));
					}
					plugin.getLogger().info(logPrefix + "Vote streak: " + streakDays + " days, bonus +" + streakBonus);
				}

				plugin.getLogger().info(logPrefix + "Vault transaction: +" + (moneyReward + streakBonus));
			}
		}

		// player points action
		if (plugin.isEnabledPoints() && pointsReward > 0) {
			plugin.getPointsAPI().give(uuid, pointsReward);
			if (player != null && !userMessage) {
				player.sendMessage(format("messages.player_points_reward", username, service));
			}
			plugin.getLogger().info(logPrefix + "PlayerPoints: +" + pointsReward);
		}

		// heal action
		if (config.getBoolean(servicePath + ".heal") && player != null) {
			AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
			player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
			if (player.getFoodLevel() < 20) {
				player.setFoodLevel(20);
			}
			if (!userMessage) {
				player.sendMessage(format("messages.player_heal_reward", username, service));
			}
			plugin.getLogger().info(logPrefix + "Healed");
		}

		// command actions
		if (player != null) {
			runCommands(onlineCommands, username, service);
		}
		runCommands(offlineCommands, username, service);
	}

	private void runCommands(List<String> commands, String username, String service) {
		for (String command : commands) {
			Bukkit.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
					command.replace("%name%", username).replace("%service%", service).replace("%amount%", ""));
		}
	}

	/** Builds prefix + message with the %name% and %service% placeholders replaced. */
	private String format(String messagePath, String username, String service) {
		FileConfiguration config = plugin.getConfig();
		return Tools.reformatColorCodes(config.getString("message_prefix", "") + config.getString(messagePath, ""))
				.replace("%name%", username)
				.replace("%service%", service);
	}

	/**
	 * Returns the current streak length (consecutive vote days within the month of voteDate)
	 * if this vote qualifies for the streak bonus, otherwise 0.
	 *
	 * A vote qualifies if the streak is enabled, the service counts for the streak, the player
	 * is a direct member (parent) of one of the configured LuckPerms groups, the vote is for today and it is the first
	 * streak-service vote of the day.
	 */
	private int calculateStreakDays(String uuid, String service, LocalDate voteDate) {
		if (!plugin.getConfig().getBoolean("vote_streak.enabled")) {
			return 0;
		}
		List<String> streakServices = plugin.getConfig().getStringList("vote_streak.services");
		List<String> groups = plugin.getConfig().getStringList("vote_streak.groups");
		if (!streakServices.contains(service) || groups.isEmpty()) {
			return 0;
		}
		// late delivered votes must not trigger the bonus again
		if (!voteDate.equals(LocalDate.now(MultiVoteListener.ZONE))) {
			return 0;
		}

		String servicePlaceholders = String.join(",", Collections.nCopies(streakServices.size(), "?"));
		// only players that are a direct member (parent) of one of the configured LuckPerms groups qualify
		Set<String> playerGroups = plugin.getGroups(UUID.fromString(uuid));
		if (playerGroups == null || groups.stream().noneMatch(group -> playerGroups.contains(group.toLowerCase(Locale.ROOT)))) {
			plugin.getLogger().info("No vote streak bonus for uuid " + uuid + ": groups are " + playerGroups + ", required one of " + groups);
			return 0;
		}

		try {
			List<LocalDate> voteDays = plugin.getDatabaseManager().executeQuery(
					"SELECT DISTINCT date FROM votes WHERE uuid = ? AND date BETWEEN ? AND ? AND votesite IN (" + servicePlaceholders + ") ORDER BY date DESC",
					stmt -> {
						stmt.setString(1, uuid);
						stmt.setObject(2, voteDate.withDayOfMonth(1));
						stmt.setObject(3, voteDate);
						for (int i = 0; i < streakServices.size(); i++) {
							stmt.setString(i + 4, streakServices.get(i));
						}
					},
					rs -> {
						List<LocalDate> days = new ArrayList<>();
						while (rs.next()) {
							days.add(rs.getObject("date", LocalDate.class));
						}
						return days;
					}
			);

			// bonus only on the first streak-service vote of the day
			Integer votesToday = plugin.getDatabaseManager().executeQuery(
					"SELECT COUNT(*) FROM votes WHERE uuid = ? AND date = ? AND votesite IN (" + servicePlaceholders + ")",
					stmt -> {
						stmt.setString(1, uuid);
						stmt.setObject(2, voteDate);
						for (int i = 0; i < streakServices.size(); i++) {
							stmt.setString(i + 3, streakServices.get(i));
						}
					},
					rs -> rs.next() ? rs.getInt(1) : 0
			);
			if (votesToday == null || votesToday != 1) {
				return 0;
			}

			Set<LocalDate> daySet = new HashSet<>(voteDays);
			int days = 0;
			LocalDate day = voteDate;
			while (daySet.contains(day)) {
				days++;
				day = day.minusDays(1);
			}
			int maxStreakDays = plugin.getConfig().getInt("vote_streak.max_streak_days");
			return maxStreakDays > 0 ? Math.min(days, maxStreakDays) : days;
		} catch (SQLException exception) {
			plugin.getLogger().severe("Database error while calculating vote streak for uuid " + uuid + ": " + exception.getMessage());
			return 0;
		}
	}

	/**
	 * @return true if the vote was stored, false if the player already voted on this service on that date
	 */
	private boolean saveVoteIfNotExists(String uuid, String username, String service, LocalDate date, LocalTime time) throws SQLException {
		Boolean alreadyVoted = plugin.getDatabaseManager().executeQuery(
				"SELECT 1 FROM votes WHERE uuid = ? AND votesite = ? AND date = ?",
				stmt -> {
					stmt.setString(1, uuid);
					stmt.setString(2, service);
					stmt.setObject(3, date);
				},
				rs -> rs.next()
		);

		if (Boolean.TRUE.equals(alreadyVoted)) {
			return false;
		}

		// INSERT IGNORE + unique key: a concurrent duplicate affects 0 rows instead of being rewarded twice
		int inserted = plugin.getDatabaseManager().executeUpdate(
				"INSERT IGNORE INTO votes (uuid, username, votesite, date, time) VALUES (?, ?, ?, ?, ?)",
				stmt -> {
					stmt.setString(1, uuid);
					stmt.setString(2, username);
					stmt.setString(3, service);
					stmt.setObject(4, date);
					stmt.setObject(5, time);
				}
		);

		return inserted > 0;
	}

	/**
     * Broadcast a message to all players and the console, with an OPEN_URL click event if a url is given.
     *
     * @param		message
     * @param		url
     * @see			de.bmack.MultiVoteListener.UrlBroadcast
     */
	public void broadcast(String message, String url) {
		UrlBroadcast.doBroadcast(plugin, message, url);
	}
}
