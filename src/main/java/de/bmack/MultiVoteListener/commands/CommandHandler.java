package de.bmack.MultiVoteListener.commands;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import de.bmack.MultiVoteListener.MultiVoteListener;
import de.bmack.MultiVoteListener.Tools;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.jspecify.annotations.NonNull;

import static org.bukkit.Bukkit.getOfflinePlayer;

/**
 * Implements a CommandExecutor
 * 
 * @author		bmack94
 * @see			org.bukkit.command.CommandExecutor
 */
public class CommandHandler implements CommandExecutor {
	private MultiVoteListener plugin;
	
	public CommandHandler(MultiVoteListener plugin) {
        this.plugin = plugin;
    }

	/**
     * Implementation of the onCommand method.
     * 
     * The entire command handling is done here.
     * 
     * @param		sender The CommandSender who issued the command. This can be a Player or the Console.
     * @param		cmd The Command object representing the command that was issued. This contains information about the command, such as its name and arguments.
     * @param		commandLabel The exact command string that was used to trigger this command. This may differ from the command's registered name if an alias was used.
     * @param		args An array of strings representing the arguments passed to the command. For example, if the command was "/mvote reload", then args would be an array containing a single element: ["reload"].
     * @return		true
     */ 
	public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String commandLabel, String @NonNull [] args) {
		if (!(sender.isOp() || sender.hasPermission("mvote.admin"))) {
			sender.sendMessage("§cYou do not have permission to use this command.");
			return true;
		}

		// Handle /mvote command
        if (cmd.getName().equalsIgnoreCase("mvote")) {
            if (args.length > 0) {
				switch (args[0]) {
					case "reload":
						List<String> problems = plugin.reloadAndValidateConfig();
						if (problems.isEmpty()) {
							commandResponse(sender, "&AConfig reloaded.");
						} else {
							commandResponse(sender, "&4Error in your config file, votes are ignored until it is fixed:");
							problems.forEach(problem -> commandResponse(sender, "&4- " + problem));
						}
						break;
					case "status":
						commandResponse(sender, Tools.getStatus(plugin));
						break;
					case "services":
						commandResponse(sender, Tools.getServices(plugin));
						break;
					case "testvote":
							if (args.length > 1) {
								String service = args.length > 2 ? args[2] : firstServiceName();
								if (service == null) {
									commandResponse(sender, "&4No service configured.");
									break;
								}
								Vote vote = new Vote(service, args[1], "127.0.0.1", String.valueOf(System.currentTimeMillis() / 1000));
								Bukkit.getPluginManager().callEvent(new VotifierEvent(vote));
								commandResponse(sender, "Test vote for " + args[1] + " on " + service + " fired.");
							}
							else {
								commandResponse(sender, "Usage: /mvote testvote <player> [service]");
							}
							break;
						case "receive-trophies":
						if(args.length > 1){
							UUID playerUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
							plugin.receiveTrophies(playerUUID, sender);
						}
						else{
							String message = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix") + plugin.getConfig().getString("messages.usage_receive_trophies"));
							sender.sendMessage(message);
						}
						break;
					case "give-trophies":
						plugin.voteCheck(sender);
						break;
					default:
						commandResponse(sender, Tools.getUsage());
						break;
				}
            } 
            else {
            	commandResponse(sender,Tools.getUsage());
            }
        } 
        else {
        	commandResponse(sender,Tools.getUsage());
        }
        return true;
	}

	
	private String firstServiceName() {
		ConfigurationSection services = plugin.getConfig().getConfigurationSection("services");
		if (services == null) return null;
		for (String id : services.getKeys(false)) {
			if (id.equals("default")) continue;
			String name = services.getString(id + ".name");
			if (name != null) return name;
		}
		return null;
	}

	public void commandResponse(CommandSender sender, List<String> responseText) {
		String prefix = "";
		if(sender instanceof Player) {
			prefix += Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix"));
			for(Iterator<String> nextMessage = responseText.iterator(); nextMessage.hasNext(); ) {
				sender.sendMessage(prefix+Tools.reformatColorCodes(nextMessage.next()));
			}
		}
		else {
			prefix += Tools.stripColorCodes(plugin.getConfig().getString("message_prefix"));
			for(Iterator<String> nextMessage = responseText.iterator(); nextMessage.hasNext(); ) {
				sender.sendMessage(prefix+Tools.stripColorCodes(nextMessage.next()));
			}
		}
	}

	public void commandResponse(CommandSender sender, String responseText) {
		String prefix = "";
		if(sender instanceof Player) {
			prefix += Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix"));
			sender.sendMessage(prefix+Tools.reformatColorCodes(responseText));
		}
		else {
			prefix += Tools.stripColorCodes(plugin.getConfig().getString("message_prefix"));
			sender.sendMessage(prefix+Tools.stripColorCodes(responseText));
		}
	}

}
