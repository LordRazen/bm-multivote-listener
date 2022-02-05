package de.bmack.MultiVoteListener;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;

/**
 * This class is loaded on demand using reflection. You should not invoke this directly!
 * It will not work on non-Spigot servers.
 * 
 * @author		bmack94
 * @see			net.md_5.bungee.api.chat
 */
public class UrlBroadcast {
	/**
     * Add ClickEvent to a broadcast and perform broadcast.
     * 
     * Spigot only! This method adds a clickevent to the message using the Bungee Chat API.
     * @param		plugin
     * @param		message
     * @param		url
     */ 
	public static void doBroadcast(MultiVoteListener plugin, String message, String url) {
		TextComponent bcMessage = new TextComponent(message);
		bcMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,url));
		plugin.getServer().spigot().broadcast(bcMessage);
	}
	
}
