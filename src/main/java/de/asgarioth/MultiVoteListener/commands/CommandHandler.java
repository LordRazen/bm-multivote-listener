package de.asgarioth.MultiVoteListener.commands;

import java.util.Iterator;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.asgarioth.MultiVoteListener.MultiVoteListener;
import de.asgarioth.MultiVoteListener.Tools;

/**
 * Implements a CommandExecutor
 * 
 * @author		Asgarioth
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
			if (!(sender.isOp() || plugin.getPermAPI().has(sender,"mvote.admin"))) return true;	
		}
		else {
			if (!(sender.isOp() || sender.hasPermission("mvote.admin"))) return true;
		}
		
		
		// Handle /mvote command
        if (cmd.getName().toLowerCase().equals("mvote")) {
            if (args.length > 0) {
                if (args[0].equals("reload")) {
                	try {
                		plugin.reloadConfig();
                		commandResponse(sender,"&4Config reloaded.");	
                	}
                	catch(Exception e) {
                		System.out.println("[MultiVoteListener] Error in config. Plugin will not be enabled");
                		commandResponse(sender,"&4Error in your config file.");
                	}
                } 
                else if(args[0].equals("status")) {
                	commandResponse(sender, Tools.getStatus(plugin));
                }
                else if(args[0].equals("services")) {
                	commandResponse(sender, Tools.getServices(plugin));
                }
                else {
                	commandResponse(sender,Tools.getUsage());
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
