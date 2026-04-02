package de.bmack.MultiVoteListener;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

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
     * Spigot only! This method adds a clickevent to the message using the Bungee Chat API.
     * @param		plugin Don't worry about this, it's just needed to get the server instance for broadcasting
     * @param		message The message to broadcast. Color codes are supported and will be reformatted to use the correct character.
     * @param		url The URL to open when the message is clicked. If null or blank, no click event will be added and the message will be broadcast without a click event.
     */
	public static void doBroadcast(MultiVoteListener plugin, String message, String url) {
		Component component = Component.text(message);

		if (url != null && !url.isBlank()) {
			component = component.clickEvent(ClickEvent.openUrl(url));
		}

		Component finalComponent = component;
		plugin.getServer().getOnlinePlayers().forEach(player -> player.sendMessage(finalComponent));
		plugin.getServer().getConsoleSender().sendMessage(message);
	}

}

