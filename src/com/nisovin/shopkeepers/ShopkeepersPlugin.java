package com.nisovin.shopkeepers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.nisovin.shopkeepers.events.*;
import com.nisovin.shopkeepers.shoptypes.*;

public class ShopkeepersPlugin extends JavaPlugin implements Listener {

	static ShopkeepersPlugin plugin;

	private boolean debug = false;
	
	Map<String, List<Shopkeeper>> allShopkeepersByChunk = new HashMap<String, List<Shopkeeper>>();
	Map<Integer, Shopkeeper> activeShopkeepers = new HashMap<Integer, Shopkeeper>();
	Map<String, Integer> editing = new HashMap<String, Integer>();
	Map<String, Integer> purchasing = new HashMap<String, Integer>();
	Map<String, Integer> selectedShopType = new HashMap<String, Integer>();
	Map<String, Block> selectedChest = new HashMap<String, Block>();
	
	private boolean dirty = false;
		
	BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
	
	@Override
	public void onEnable() {
		plugin = this;
		
		// get config
		File file = new File(getDataFolder(), "config.yml");
		if (!file.exists()) {
			saveDefaultConfig();
		}
		reloadConfig();
		Configuration config = getConfig();

		debug = config.getBoolean("debug", debug);
		
		Settings.loadConfiguration(config);
		
		// load shopkeeper saved data
		load();
		
		// spawn villagers in loaded chunks
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				loadShopkeepersInChunk(chunk);
			}
		}
		
		// register events
		getServer().getPluginManager().registerEvents(new ShopListener(this), this);
		if (Settings.protectChests) {
			getServer().getPluginManager().registerEvents(new ChestProtectListener(this), this);
		}
		
		// start teleporter
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
					shopkeeper.teleport();
				}
			}
		}, 200, 200);
		
		// start saver
		if (!Settings.saveInstantly) {
			Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					if (dirty) {
						saveReal();
						dirty = false;
					}
				}
			}, 6000, 6000);
		}
	}
	
	@Override
	public void onDisable() {
		if (dirty) {
			saveReal();
			dirty = false;
		}
		
		for (String playerName : editing.keySet()) {
			Player player = Bukkit.getPlayerExact(playerName);
			if (player != null) {
				player.closeInventory();
			}
		}
		editing.clear();
		
		for (String playerName : purchasing.keySet()) {
			Player player = Bukkit.getPlayerExact(playerName);
			if (player != null) {
				player.closeInventory();
			}
		}
		purchasing.clear();
		
		for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
			shopkeeper.remove();
		}
		activeShopkeepers.clear();
		allShopkeepersByChunk.clear();
		
		selectedShopType.clear();
		selectedChest.clear();
		
		HandlerList.unregisterAll((Plugin)this);		
		Bukkit.getScheduler().cancelTasks(this);
		
		plugin = null;
	}
	
	/**
	 * Reloads the plugin.
	 */
	public void reload() {
		onDisable();
		onEnable();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("shopkeeper.reload")) {
			// reload command
			reload();
			sender.sendMessage(ChatColor.GREEN + "Shopkeepers plugin reloaded!");
			return true;
		} else if (sender instanceof Player) {
			Player player = (Player)sender;
			if (!player.hasPermission("shopkeeper.admin") && !player.hasPermission("shopkeeper.player")) return true;
			
			// get the profession, default to farmer if an invalid one is specified
			int prof = 0;
			if (args.length > 0) {
				if (args[0].matches("[0-9]+")) {
					prof = Integer.parseInt(args[0]);
					if (prof > 5) {
						prof = 0;
					}
				} else {
					Profession p = Profession.valueOf(args[0].toUpperCase());
					if (p != null) {
						prof = p.getId();
					}
				}
			}
			
			// get the spawn location for the shopkeeper
			Block block = player.getTargetBlock(null, 10);
			if (block != null && block.getType() != Material.AIR) {
				if (Settings.createPlayerShopWithCommand && block.getType() == Material.CHEST && player.hasPermission("shopkeeper.player")) {
					// check if already a chest
					if (isChestProtected(null, block)) {
						return true;
					}
					// check for permission
					PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), block, BlockFace.UP);
					Bukkit.getPluginManager().callEvent(event);
					if (event.isCancelled()) {
						return true;
					}
					// create the player shopkeeper
					createNewPlayerShopkeeper(player, block, block.getLocation().add(0, 1.5, 0), prof, 0);
					sendMessage(player, Settings.msgPlayerShopCreated);
				} else if (player.hasPermission("shopkeeper.admin")) {
					// create the admin shopkeeper
					createNewAdminShopkeeper(block.getLocation().add(0, 1.5, 0), prof);
					sendMessage(player, Settings.msgAdminShopCreated);
				}
			} else {
				sendMessage(player, Settings.msgShopCreateFail);
			}
			
			return true;
		} else {
			sender.sendMessage("You must be a player to create a shopkeeper.");
			sender.sendMessage("Use 'shopkeeper reload' to reload the plugin.");
			return true;
		}
	}
	
	/**
	 * Creates a new admin shopkeeper and spawns it into the world.
	 * @param location the block location the shopkeeper should spawn
	 * @param profession the shopkeeper's profession, a number from 0 to 5
	 * @return the shopkeeper created
	 */
	public Shopkeeper createNewAdminShopkeeper(Location location, int profession) {
		// make sure profession is valid
		if (profession < 0 || profession > 5) {
			profession = 0;
		}
		// create the shopkeeper (and spawn it)
		Shopkeeper shopkeeper = new AdminShopkeeper(location, profession);
		shopkeeper.spawn();
		activeShopkeepers.put(shopkeeper.getEntityId(), shopkeeper);
		addShopkeeper(shopkeeper);
		
		return shopkeeper;
	}

	/**
	 * Creates a new player-based shopkeeper and spawns it into the world.
	 * @param player the player who created the shopkeeper
	 * @param chest the backing chest for the shop
	 * @param location the block location the shopkeeper should spawn
	 * @param profession the shopkeeper's profession, a number from 0 to 5
	 * @param type the player shop type (0=normal, 1=book, 2=buy)
	 * @return the shopkeeper created
	 */
	public Shopkeeper createNewPlayerShopkeeper(Player player, Block chest, Location location, int profession, int type) {
		// make sure profession is valid
		if (profession < 0 || profession > 5) {
			profession = 0;
		}
		
		ShopkeeperType shopType = null;
		if (type == 0) {
			shopType = ShopkeeperType.PLAYER_NORMAL;
		} else if (type == 1) {
			shopType = ShopkeeperType.PLAYER_BOOK;
		} else if (type == 2) {
			shopType = ShopkeeperType.PLAYER_BUY;
		}
		if (shopType == null) {
			return null;
		}
		
		int maxShops = Settings.maxShopsPerPlayer;
		
		// call event
		CreatePlayerShopkeeperEvent event = new CreatePlayerShopkeeperEvent(player, chest, location, profession, shopType, maxShops);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return null;
		} else {
			location = event.getSpawnLocation();
			profession = event.getProfessionId();
			shopType = event.getType();
			maxShops = event.getMaxShopsForPlayer();
		}
		
		// count owned shops
		if (maxShops > 0) {
			int count = 0;
			for (List<Shopkeeper> list : allShopkeepersByChunk.values()) {
				for (Shopkeeper shopkeeper : list) {
					if (shopkeeper instanceof PlayerShopkeeper && ((PlayerShopkeeper)shopkeeper).getOwner().equalsIgnoreCase(player.getName())) {
						count++;
					}
				}
			}
			if (count >= maxShops) {
				sendMessage(player, Settings.msgTooManyShops);
				return null;
			}
		}
		
		// create the shopkeeper
		Shopkeeper shopkeeper = null;
		if (shopType == ShopkeeperType.PLAYER_NORMAL) {
			if (Settings.allowCustomQuantities) {
				shopkeeper = new CustomQuantityPlayerShopkeeper(player, chest, location, profession);
			} else {
				shopkeeper = new FixedQuantityPlayerShopkeeper(player, chest, location, profession);
			}
		} else if (shopType == ShopkeeperType.PLAYER_BOOK) {
			shopkeeper = new WrittenBookPlayerShopkeeper(player, chest, location, profession);
		} else if (shopType == ShopkeeperType.PLAYER_BUY) {
			shopkeeper = new BuyingPlayerShopkeeper(player, chest, location, profession);
		}

		// spawn and save the shopkeeper
		if (shopkeeper != null) {
			shopkeeper.spawn();
			activeShopkeepers.put(shopkeeper.getEntityId(), shopkeeper);
			addShopkeeper(shopkeeper);
		}
		
		return shopkeeper;
	}
	
	/**
	 * Gets the shopkeeper by the villager's entity id.
	 * @param entityId the entity id of the villager
	 * @return the Shopkeeper, or null if the enitity with the given id is not a shopkeeper
	 */
	public Shopkeeper getShopkeeperByEntityId(int entityId) {
		return activeShopkeepers.get(entityId);
	}
	
	/**
	 * Gets all shopkeepers from a given chunk. Returns null if there are no shopkeepers in that chunk.
	 * @param world the world
	 * @param x chunk x-coordinate
	 * @param z chunk z-coordinate
	 * @return a list of shopkeepers, or null if there are none
	 */
	public List<Shopkeeper> getShopkeepersInChunk(String world, int x, int z) {
		return allShopkeepersByChunk.get(world + "," + x + "," + z);
	}
	
	void addShopkeeper(Shopkeeper shopkeeper) {
		// add to chunk list
		List<Shopkeeper> list = allShopkeepersByChunk.get(shopkeeper.getChunk());
		if (list == null) {
			list = new ArrayList<Shopkeeper>();
			allShopkeepersByChunk.put(shopkeeper.getChunk(), list);
		}
		list.add(shopkeeper);
		// save all data
		save();
	}

	void closeTradingForShopkeeper(int entityId) {
		Iterator<String> editors = editing.keySet().iterator();
		while (editors.hasNext()) {
			String name = editors.next();
			if (editing.get(name).equals(entityId)) {
				Player player = Bukkit.getPlayerExact(name);
				if (player != null) {
					player.closeInventory();
				}
				editors.remove();
			}
		}
		Iterator<String> purchasers = purchasing.keySet().iterator();
		while (purchasers.hasNext()) {
			String name = purchasers.next();
			if (purchasing.get(name).equals(entityId)) {
				Player player = Bukkit.getPlayerExact(name);
				if (player != null) {
					player.closeInventory();
				}
				purchasers.remove();
			}
		}
	}
	
	boolean isChestProtected(Player player, Block block) {
		for (Shopkeeper shopkeeper : activeShopkeepers.values()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper pshop = (PlayerShopkeeper)shopkeeper;
				if ((player == null || !pshop.getOwner().equalsIgnoreCase(player.getName())) && pshop.usesChest(block)) {
					return true;
				}
			}
		}
		return false;
	}
	
	void sendMessage(Player player, String message) {
		message = ChatColor.translateAlternateColorCodes('&', message);
		String[] msgs = message.split("\n");
		for (String msg : msgs) {
			player.sendMessage(msg);
		}
	}
	
	void loadShopkeepersInChunk(Chunk chunk) {
		List<Shopkeeper> shopkeepers = allShopkeepersByChunk.get(chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ());
		if (shopkeepers != null) {
			debug("Loading " + shopkeepers.size() + " shopkeepers in chunk " + chunk.getX() + "," + chunk.getZ());
			for (Shopkeeper shopkeeper : shopkeepers) {
				if (!shopkeeper.isActive()) {
					shopkeeper.spawn();
					activeShopkeepers.put(shopkeeper.getEntityId(), shopkeeper);
				}
			}
			save();
		}
	}
	
	private void load() {
		File file = new File(getDataFolder(), "save.yml");
		if (!file.exists()) return;
		
		YamlConfiguration config = new YamlConfiguration();
		try {
			config.load(file);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		Set<String> keys = config.getKeys(false);
		for (String key : keys) {
			ConfigurationSection section = config.getConfigurationSection(key);
			Shopkeeper shopkeeper = null;
			String type = section.getString("type", "");
			if (type.equals("book")) {
				if (Settings.allowPlayerBookShop) {
					shopkeeper = new WrittenBookPlayerShopkeeper(section);
				}
			} else if (type.equals("buy")) {
				shopkeeper = new BuyingPlayerShopkeeper(section);
			} else if (type.equals("player") || section.contains("owner")) {
				if (Settings.allowCustomQuantities) {
					shopkeeper = new CustomQuantityPlayerShopkeeper(section);
				} else {
					shopkeeper = new FixedQuantityPlayerShopkeeper(section);
				}
			} else {
				shopkeeper = new AdminShopkeeper(section);
			}
			if (shopkeeper != null) {
				List<Shopkeeper> list = allShopkeepersByChunk.get(shopkeeper.getChunk());
				if (list == null) {
					list = new ArrayList<Shopkeeper>();
					allShopkeepersByChunk.put(shopkeeper.getChunk(), list);
				}
				list.add(shopkeeper);
			}
		}
	}
	
	void save() {
		if (Settings.saveInstantly) {
			saveReal();
		} else {
			dirty = true;
		}
	}
	
	private void saveReal() {
		YamlConfiguration config = new YamlConfiguration();
		int counter = 0;
		for (List<Shopkeeper> shopkeepers : allShopkeepersByChunk.values()) {
			for (Shopkeeper shopkeeper : shopkeepers) {
				ConfigurationSection section = config.createSection(counter + "");
				shopkeeper.save(section);
				counter++;
			}
		}
		
		File file = new File(getDataFolder(), "save.yml");
		if (file.exists()) {
			file.delete();
		}
		try {
			config.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static void debug(String message) {
		if (plugin.debug) {
			plugin.getLogger().info(message);
		}
	}

}
