package de.asgarioth.MultiVoteListener;

import java.io.File;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;

//import com.avaje.ebean.enhance.asm.commons.Method;


import de.asgarioth.MultiVoteListener.commands.CommandHandler;
import de.asgarioth.MultiVoteListener.listeners.VoteEventListener;

/**
 * Main class of the MultiVotePlugin
 * 
 * @author 		Asgarioth
 */
public class MultiVoteListener extends JavaPlugin {

	public FileConfiguration config;

	// Soft depend indicators and instances
	private boolean enableVaultEco = false;
	private boolean enableVaultPerm = false;
	private boolean enablePlayerPoints = false;
	
	private boolean isSpigot = false;
	
    private Economy economy = null;
    private Permission permission = null;
    private PlayerPoints points = null;
	
	// Our listeners
	VoteEventListener newVote = null;
	
	/**
     * Implements the onEnable method invoked when plugin is enabled.
     */    
	@Override
    public void onEnable() {

	       if (!new File(getDataFolder(), "config.yml").exists()) {
	            saveResource("config.yml", false);
	            //TODO: Created config message
	       }
	       if(!loadConfig()) {
	    	   this.getServer().getPluginManager().disablePlugin(this);
	    	   return;
	       }
	       //TODO: Load config message
	       
	       // Check for Vault
	    	if(this.getServer().getPluginManager().getPlugin("Vault") != null) {
		    	RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
		    	if (economyProvider != null) {
		    		economy = economyProvider.getProvider();
		    		enableVaultEco = true;
		    		System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: Vault - Economy hook found.");
		    	}
		    	else {
		    		enableVaultEco = false;
		    		System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: Vault - not found. money reward disabled.");
		    	}
		        RegisteredServiceProvider<Permission> permissionProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
		        if (permissionProvider != null) {
		            permission = permissionProvider.getProvider();
		            enableVaultPerm = true;
		            System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: Vault - Permission hook found.");
		        }
		        else {
		        	enableVaultPerm = false;
		        	System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: Vault - not found. money reward disabled.");
		    	}
	    	}
	    	else {
	    		System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "Dependency: Vault - not found. Disabling MultiVoteListener.");
		    	this.getServer().getPluginManager().disablePlugin(this);
		    	return;
	    	}

	    	// Check for player points	    	
	    	if(this.getServer().getPluginManager().getPlugin("PlayerPoints") != null) {
		    	points = PlayerPoints.class.cast(this.getServer().getPluginManager().getPlugin("PlayerPoints"));
		    	if(points != null) {
		    		enablePlayerPoints = true;
		    		System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: PlayerPoints - found.");
		    	}
	    	}
	    	else {
	    		System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "SoftDependency: PlayerPoints - not found. points reward disabled.");	    	
	    	}
	    	
	       // Check if server is Spigot
			try {
				
				Class.forName("org.bukkit.Bukkit").getMethod("spigot");
				System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "Running on Spigot");
				isSpigot = true;
			}
			catch (NoSuchMethodException cfne) {
				System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "Running on CraftBukkit");
			}
			catch (ClassNotFoundException cnfe) {
				// This condition should not be met at all.
				// The plugin should instantly crash due to missing Bukkit-API
				System.out.println(Tools.stripColorCodes(getConfig().getString("message_prefix")) + "No bukkit found at all - disabling");
		    	this.getServer().getPluginManager().disablePlugin(this);
		    	return;
			}
	    	
	    	this.newVote = new VoteEventListener(this);
	    	this.getCommand("mvote").setExecutor(new CommandHandler(this));
	    	
	}
	
	/**
     * Load config file from disk.
     * The method handles exceptions that may occur during file load using getConfig()
     * @return		true if config has benn loaded, false on error.
     */    
    public boolean loadConfig() {
    	//TODO: Handle invalid configuation file exception
    	try {
    		getConfig();
    		//this.config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));	
    	}
    	catch(Exception e) {
    		System.out.println("[MultiVoteListener] Error in config. Plugin will not be enabled");
    		return false;
    	}
    	return true;
        
    }
    
    /**
     * Returns the Vault API - Economy used by MultiVoteListener.
     * 
     * @return Vault Instance or null, if Vault is not active
     * @see    net.milkbowl.vault.economy.Economy
     */
    public Economy getEcoAPI() {
    	return this.economy;
    }
    
    /**
     * Returns the Vault API - Permissions used by MultiVoteListener.
     * 
     * @return Vault Instance or null, if Vault is not active
     * @see    net.milkbowl.vault.economy.Economy
     */
    public Permission getPermAPI() {
    	return this.permission;
    }
    
    
    /**
     * Returns the Vault API used by MultiVoteListener.
     * 
     * @return PlayerPoints Instance or null, if Vault is not active
     * @see    org.black_ixx.playerpoints.PlayerPointsAPI
     */    
    public PlayerPointsAPI getPointsAPI() {
    	return this.points.getAPI();
    }
    
    /**
     * Indicates if Vault is currently used for Economy
     * 
     * @return true, if Vault is used. Otherwise false
     */    
    public boolean isEnabledVaultEco() {
    	return this.enableVaultEco;
    }

    /**
     * Indicates if Vault is currently used for Permissions
     * 
     * @return true, if Vault is used. Otherwise false
     */    
    public boolean isEnabledVaultPerm() {
    	return this.enableVaultPerm;
    }
    
    /**
     * Indicates if PlayerPoints is currently used
     * 
     * @return true, if Vault is used. Otherwise false.
     */    
    public boolean isEnabledPoints() {
    	return this.enablePlayerPoints;
    }
    
    /**
     * Indicates if plugin runs in a Spigot Environment.
     * Depending on the type of server (Spigot/CraftBukkit) some extended functionality is used, e.g.
     * if running in a Spigot environment the plugin will make use of the Bungee Chat API which is not
     * available in CraftBukkit.
     * 
     * @return true, if server is Spigot is used. Otherwise false.
     */    
    public boolean isSpigot() {
    	return isSpigot;
    }
}
