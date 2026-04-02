package de.bmack.MultiVoteListener.commands;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.bmack.MultiVoteListener.MultiVoteListener;
import de.bmack.MultiVoteListener.Tools;
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
        // Do nothing if sender is not OP or has appropriate permission
		if(plugin.isEnabledVaultPerm()) {
			if (!(sender.isOp() || Objects.requireNonNull(Bukkit.getPlayer(String.valueOf(sender))).hasPermission("mvote.admin"))) return true;
		}
		else {
			if (!(sender.isOp() || sender.hasPermission("mvote.admin"))) return true;
		}
		
		
		// Handle /mvote command
        if (cmd.getName().equalsIgnoreCase("mvote")) {
            if (args.length > 0) {
				switch (args[0]) {
					case "reload":
						try {
							plugin.reloadConfig();
							commandResponse(sender, "&4Config reloaded.");
						} catch (Exception e) {
							System.out.println("[MultiVoteListener] Error in config. Plugin will not be enabled");
							commandResponse(sender, "&4Error in your config file.");
						}
						break;
					case "status":
						commandResponse(sender, Tools.getStatus(plugin));
						break;
					case "services":
						commandResponse(sender, Tools.getServices(plugin));
						break;
					case "receive-trophies":
						if(args.length > 1){
							UUID playerUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
							plugin.receiveTrophies(plugin, playerUUID, sender);
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
