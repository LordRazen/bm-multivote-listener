package de.bmack.MultiVoteListener.commands;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.bmack.MultiVoteListener.MultiVoteListener;
import de.bmack.MultiVoteListener.Tools;

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
     * @param		sender
     * @param		cmd
     * @param		commandLabel
     * @param		args
     * @return		true
     */ 
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        // Do nothing if sender is not OP or has appropriate permission
		if(plugin.isEnabledVaultPerm()) {
			if (!(sender.isOp() || Objects.requireNonNull(Bukkit.getPlayer(String.valueOf(sender))).hasPermission("mvote.admin"))) return true;
		}
		else {
			if (!(sender.isOp() || sender.hasPermission("mvote.admin"))) return true;
		}
		
		
		// Handle /mvote command
        if (cmd.getName().toLowerCase().equals("mvote")) {
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
							plugin.receiveTrophies(plugin, args[1]);
						}
						else{
							sender.sendMessage("Bitte Spielername angeben </mvote receive-trophies Spielername>");
						}
						break;
					case "give-trophies":
						plugin.voteCheck();
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
