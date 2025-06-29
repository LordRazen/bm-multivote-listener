package de.bmack.MultiVoteListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Static helper methods
 * 
 * @author 		bmack94
 */
public class Tools {
	/**
     * Reformat color codes from a string.
     * Color codes indicated by & are reformatted to use § before provided
     * to some server method
     * @param		input
     * @return		A String with reformatted Minecraft color codes
     */ 
    public static String reformatColorCodes(String input) {
    	String output = input;
    	output = output.replaceAll("&(?!0-9a-fA-Fk-oK-OrR)", "§");
    	return output;
    }
    
    /**
     * Strip all color codes from a string.
     * Color codes are removed from a string. This method is used to prepare
     * messages which are sent to the log file & console
     * @param		input
     * @return		A String without any Minecraft color codes
     */ 
    public static String stripColorCodes(String input) {
    	String output = input;
    	output = output.replaceAll("&[0-9a-fA-Fk-oK-OrR]{1}", "");
    	return output;
    }
    
	/**
     * Reformat color codes from a string.
     * Color codes indicated by & are reformatted to use § before provided
     * to some server method
     * @param		input
     * @return		A String with reformatted Minecraft color codes
     */ 
    public static String stateMessage(String input, boolean state) {
    	String output = input;
    	if(!state) {
    		output = String.format("%1$-20s", input) +" &4No";
    	}
    	else {
    		output = String.format("%1$-20s", input) +" &AYes";
    	}
    	return output;
    }    
    
    public static List<String> getStatus(MultiVoteListener plugin) {
    	List<String> stateList = new ArrayList<String>();
		stateList.add("Status:");
		stateList.add("---------------------------------------");
    	stateList.add(Tools.stateMessage("Spigot Server", plugin.isSpigot()));
    	stateList.add(Tools.stateMessage("Vault", plugin.isEnabledVaultEco()));
    	stateList.add(Tools.stateMessage("PlayerPoints", plugin.isEnabledPoints()));
    	return stateList;
    }
    
    public static List<String> getUsage() {
    	List<String> usageList = new ArrayList<String>();
    	usageList.add("MultiVoteListener Command options");
		usageList.add("---------------------------------------");
    	usageList.add("reload    - Reload config file from disk");
    	usageList.add("services  - List available vote services");
    	usageList.add("status     - Show active plugin hooks");
    	usageList.add("help       - This information");
		usageList.add("Vote-Trophy Commands:");
		usageList.add("---------------------------------------");
		usageList.add("give-trophies:");
		usageList.add("- Manually activates monthly run giving permissions");
		usageList.add("to get monthly vote rewards to all players who voted");
		usageList.add("enough in the last month");
		usageList.add("receive-trophies player_name:");
		usageList.add("- Command for giving the player alls his monthly vote");
		usageList.add("rewards if he has the permissions (used by Bosshop)");
    	return usageList;
    }
    
    public static List<String> getServices(MultiVoteListener plugin) {
    	List<String> servicesList = new ArrayList<String>();
    	String entry;
    	String next;
    	
    	servicesList.add("Configured Services       Status");
    	servicesList.add("------------------------------------");
    	
		Set<String> serviceNames = plugin.getConfig().getConfigurationSection("services").getKeys(false);
		for ( Iterator<String> iterator = serviceNames.iterator(); iterator.hasNext(); ) {
			next = iterator.next();
			entry = "(" + String.format("%1$-9s",next) + ") " + String.format("%1$-20s", plugin.getConfig().getString("services."+next+".name"));
			if(plugin.getConfig().getBoolean("services."+next+".enabled")) {
				entry += " &AActive";
			}
			else {
				entry += " &4Inactive";
			}
			servicesList.add(entry);
		}
    	
    	return servicesList; 
    }
}
