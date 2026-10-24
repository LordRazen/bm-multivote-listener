package de.bmack.MultiVoteListener;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Broadcast helper adding an OPEN_URL click event to a message (Adventure API).
 *
 * @author		bmack94
 */
public class UrlBroadcast {
	/**
     * Add ClickEvent to a broadcast and perform broadcast.
     * @param		plugin Needed to get the server instance for broadcasting
     * @param		message The message to broadcast. Legacy section-sign color codes are supported.
     * @param		url The URL to open when the message is clicked. If null or blank, no click event will be added.
     */
	public static void doBroadcast(MultiVoteListener plugin, String message, String url) {
		Component component = LegacyComponentSerializer.legacySection().deserialize(message);

		if (url != null && !url.isBlank()) {
			component = component.clickEvent(ClickEvent.openUrl(url));
		}

		plugin.getServer().broadcast(component);
	}

}
