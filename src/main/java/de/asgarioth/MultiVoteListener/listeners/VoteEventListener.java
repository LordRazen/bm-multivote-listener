package de.asgarioth.MultiVoteListener.listeners;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.asgarioth.MultiVoteListener.MultiVoteListener;
import de.asgarioth.MultiVoteListener.Tools;
//import net.md_5.bungee.api.chat.ClickEvent;
//import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.vexsoftware.votifier.model.VotifierEvent;
import com.vexsoftware.votifier.model.Vote;

/**
 * Listener implementation for Vote Events.
 * 
 * The listener will catch and handle VotifierEvents.
 * 
 * @author		Asgarioth
 */ 
public class VoteEventListener implements Listener {

	private MultiVoteListener plugin; 
	
	/**
     * Constructor.
     * 
     * The entire command handling is done here.
     * 
     * @param		plugin
     */ 
	public VoteEventListener(MultiVoteListener plugin) {
        this.plugin = plugin;
        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
    }

	/**
     * EventHandler
     * Actions happening on VotifierEvents.
     * 
     * The entire command handling is done here.
     * 
     * @param		e
     */ 
	@EventHandler(priority = EventPriority.NORMAL)
	public void onVote(VotifierEvent e) {
		// URL regex to check config
		String url_regex = "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
		
		String service = "";
		String serviceUrl = "";
		String serviceId = "";
		String username = "";
		
		String message = "";
		String broadcast = "";
		
		Set<String> serviceNames;
		boolean knownService = false;
		boolean defaultService = false;
		
		boolean userMessage = false;
		
		OfflinePlayer user;

		Vote vote = e.getVote();
		
		service = vote.getServiceName();
		username = vote.getUsername();
		
		// Check, if vote service is configured
		// If unknown, do nothing
		
		if(plugin.getConfig().isSet("services.default") && plugin.getConfig().getBoolean("services.default.enabled")) {
			defaultService = true;
		}
		
		serviceNames = plugin.getConfig().getConfigurationSection("services").getKeys(false);
		for ( Iterator<String> iterator = serviceNames.iterator(); iterator.hasNext(); ) {
			  serviceId = iterator.next();
			  if(plugin.getConfig().getString("services."+serviceId+".name").equals(service)) {
				  knownService = true;
				  break;
			  }
		}
		
		// Exit if service is unknown - we will ignore this for now
		if(!knownService) {
			if(defaultService) {
				serviceId = "default";
			}
			else {
				System.out.println(Tools.stripColorCodes(plugin.getConfig().getString("message_prefix")) + "Vote from unknown service: "+service);
				System.out.println(Tools.stripColorCodes(plugin.getConfig().getString("message_prefix")) + "Default service is disabled in config.");
				return;	
			}
		}
		

		
		//Bukkit.getServer().spigot().broadcast(component);
		
		// get money amount
		int money_reward = plugin.getConfig().getInt("services." + serviceId + ".money");
		// get points amount
		int points_reward = plugin.getConfig().getInt("services." + serviceId + ".points");
		// get commands
		List<String> commands = plugin.getConfig().getStringList("services." + serviceId + ".online_commands");
		List<String> commands_offline = plugin.getConfig().getStringList("services." + serviceId + ".offline_commands");
		
		if(serviceId.equals("default")) {
			service = plugin.getConfig().getString("services.default.name");
		}
		
		if(plugin.getConfig().isSet("services."+serviceId+".url") && plugin.getConfig().getString("services."+serviceId+".url").matches(url_regex)) {
			serviceUrl = plugin.getConfig().getString("services."+serviceId+".url");
		}
		else {
			serviceUrl = null;
		}

		// try to fetch UUID
		user = Bukkit.getServer().getOfflinePlayer(username);
		
		if(plugin.getConfig().isSet("services."+serviceId+".usermessage") ) {
			userMessage = true;
		}
		
		// Compose a broadcast-message
		broadcast = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix")+plugin.getConfig().getString("messages.broadcast_vote_success"));
		broadcast = broadcast.replaceAll("%name%", username);
		broadcast = broadcast.replaceAll("%service%", service);
		
//		TextComponent spigotBroadcast = new TextComponent(broadcast);
//		
//		if(serviceUrl != null) {
//			spigotBroadcast.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, serviceUrl));	
//		}
		
		// Check if the player is known on the server.
		// If not, do nothing...
		if(!user.hasPlayedBefore() && !user.isOnline()) {
			if(plugin.getConfig().getBoolean("allow_fake_names")) {
				//Bukkit.getServer().spigot().broadcast(spigotBroadcast);
				broadcast(broadcast,serviceUrl);
			}
			System.out.println("Unknown user voted on "+serviceUrl+": " + user.getName());
			return;
		}
		else {
			// broadcast
			//Bukkit.getServer().spigot().broadcast(spigotBroadcast);
			broadcast(broadcast,serviceUrl);
			
			// General thx message + usermessage if defined for service
			if(user.isOnline()) {
				// Player thx message
				message = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix")+plugin.getConfig().getString("messages.player_vote_success"));
				message = message.replaceAll("%name%", username);
				message = message.replaceAll("%service%", service);
				message = message.replaceAll("%amount%", ""+money_reward);
				Player.class.cast(user).sendMessage(message);
				if(userMessage) {
					message = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix")+plugin.getConfig().getString("services."+serviceId+".usermessage"));
					Player.class.cast(user).sendMessage(message);
				}
			}			
			message = "";
			
			// vault action
			if(plugin.isEnabledVaultEco()) {
				if(plugin.getEcoAPI().hasAccount(user) && money_reward > 0) {
					plugin.getEcoAPI().depositPlayer(user,money_reward);
					
					message = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix")+plugin.getConfig().getString("messages.player_money_reward"));
					message = message.replaceAll("%name%", username);
					message = message.replaceAll("%service%", service);
					message = message.replaceAll("%amount%", ""+money_reward);
					if(user.isOnline() && message.length() > 1 && !userMessage) {
						Player.class.cast(user).sendMessage(message);						
					}
					message = Tools.stripColorCodes(plugin.getConfig().getString("message_prefix")) + "Service: "+service+"/Player: " + username + " - Vault transaction: +"+ money_reward;
					System.out.println(message);
					message = "";
				}
			}
			// player points action
			if(plugin.isEnabledPoints() && points_reward > 0) {
				plugin.getPointsAPI().give(user.getUniqueId(), points_reward);
				
				message = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix")+plugin.getConfig().getString("messages.player_points_reward"));
				message = message.replaceAll("%name%", username);
				message = message.replaceAll("%service%", service);
				message = message.replaceAll("%amount%", ""+points_reward);
				if(user.isOnline() && message.length() > 1 && !userMessage) {
					Player.class.cast(user).sendMessage(message);	
				}
				message = Tools.stripColorCodes(plugin.getConfig().getString("message_prefix")) +  "Service: "+service+"/Player: " + username + " - PlayerPoints: +"+ points_reward;
				System.out.println(message);
				message = "";
			}
			// heal action
			if(plugin.getConfig().getBoolean("services."+serviceId+".heal") && user.isOnline()) {
				Player.class.cast(user).setHealth(Player.class.cast(user).getMaxHealth());
				if(Player.class.cast(user).getFoodLevel() < 20) {
					Player.class.cast(user).setFoodLevel(20);	
				}
				message = Tools.reformatColorCodes(plugin.getConfig().getString("message_prefix")+plugin.getConfig().getString("messages.player_heal_reward"));
				message = message.replaceAll("%name%", username);
				message = message.replaceAll("%service%", service);
				message = message.replaceAll("%amount%", "");
				if(user.isOnline() && message.length() > 1 && !userMessage) {
					Player.class.cast(user).sendMessage(message);
				}
				message = Tools.stripColorCodes(plugin.getConfig().getString("message_prefix")) +  "Service: "+service+"/Player: " + username + " - Healed";
				System.out.println(message);
				message = "";
			}
			// command actions
			if(!commands.isEmpty() && user.isOnline()) {
				for ( Iterator<String> iterator = commands.iterator(); iterator.hasNext(); ) {
					message = iterator.next();
					message = message.replaceAll("%name%", username);
					message = message.replaceAll("%service%", service);
					message = message.replaceAll("%amount%", "");					  
					Bukkit.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), message);
					message = "";
				}
			}
			if(!commands_offline.isEmpty()) {
				for ( Iterator<String> iterator = commands_offline.iterator(); iterator.hasNext(); ) {
					message = iterator.next();
					message = message.replaceAll("%name%", username);
					message = message.replaceAll("%service%", service);
					message = message.replaceAll("%amount%", "");					  
					Bukkit.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), message);
					message = "";
				}
			}
		}
		return;
	}
	
	/**
     * Wrapper method for message broadcast based on underlying server.
     * 
     * The method uses Java reflection to dynamically load and use Bungee Chat API and
     * Spigot-specific broadcast when available to add an OPEN_URL ClickEvent to the message.
     * 
     * Otherwise default Bukkit broadcast is used.
     * 
     * @param		message
     * @param		url
     * @see			de.asgarioth.MultiVoteListener.UrlBroadcast
     */ 
	public void broadcast(String message, String url) {
		if(plugin.isSpigot()) {
			try {
				Class urlBC = Class.forName("de.asgarioth.MultiVoteListener.UrlBroadcast");
				Method bcMth = urlBC.getMethod("doBroadcast", MultiVoteListener.class, String.class, String.class);
				bcMth.invoke(null,this.plugin, message, url);
			}
			catch (ReflectiveOperationException cnfe) {
				System.out.println(cnfe.getMessage());
				cnfe.printStackTrace(System.out);
				plugin.getServer().broadcastMessage(message);
			}
		}
		else {
			plugin.getServer().broadcastMessage(message);
		}
	}
}
