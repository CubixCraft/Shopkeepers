package com.nisovin.shopkeepers;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityVillager;
import net.minecraft.server.MerchantRecipeList;
import net.minecraft.server.PathfinderGoalLookAtPlayer;
import net.minecraft.server.PathfinderGoalLookAtTradingPlayer;
import net.minecraft.server.PathfinderGoalSelector;
import net.minecraft.server.PathfinderGoalTradeWithPlayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public abstract class Shopkeeper {

	protected String world;
	protected int x;
	protected int y;
	protected int z;
	protected int profession;
	protected Villager villager;
	protected String uuid;

	public Shopkeeper(ConfigurationSection config) {
		load(config);
	}
	
	/**
	 * Creates a new shopkeeper and spawns it in the world. This should be used when a player is
	 * creating a new shopkeeper.
	 * @param location the location to spawn at
	 * @param prof the id of the profession
	 */
	public Shopkeeper(Location location, int prof) {
		world = location.getWorld().getName();
		x = location.getBlockX();
		y = location.getBlockY();
		z = location.getBlockZ();
		profession = prof;
	}
	
	/**
	 * Loads a shopkeeper's saved data from a config section of a config file.
	 * @param config the config section
	 */
	public void load(ConfigurationSection config) {
		world = config.getString("world");
		x = config.getInt("x");
		y = config.getInt("y");
		z = config.getInt("z");
		profession = config.getInt("prof");
		if (config.contains("uuid")) {
			uuid = config.getString("uuid");
		}
	}
	
	/**
	 * Saves the shopkeeper's data to the specified configuration section.
	 * @param config the config section
	 */
	public void save(ConfigurationSection config) {
		config.set("world", world);
		config.set("x", x);
		config.set("y", y);
		config.set("z", z);
		config.set("prof", profession);
		if (villager != null) {
			config.set("uuid", villager.getUniqueId().toString());
		}
	}
	
	/**
	 * Gets the type of this shopkeeper (admin, normal player, book player, or buying player).
	 * @return the shopkeeper type
	 */
	public abstract ShopkeeperType getType();
	
	/**
	 * Spawns the shopkeeper into the world at its spawn location. Also sets the
	 * trade recipes and overwrites the villager AI.
	 */
	public void spawn() {
		// prepare location
		World w = Bukkit.getWorld(world);
		Location loc = new Location(w, x + .5, y + .5, z + .5);
		// find old villager
		if (uuid != null && !uuid.isEmpty()) {
			Entity[] entities = loc.getChunk().getEntities();
			for (Entity e : entities) {
				if (e instanceof Villager && e.getUniqueId().toString().equalsIgnoreCase(uuid) && !e.isDead()) {
					villager = (Villager)e;
					teleport();
					break;
				}
			}
		}
		// spawn villager
		if (villager == null) {
			villager = w.spawn(loc, Villager.class);
		}
		villager.setBreed(false);
		setProfession();
		overwriteAI();
	}
	
	/**
	 * Checks if the shopkeeper is active (is alive in the world).
	 * @return whether the shopkeeper is active
	 */
	public boolean isActive() {
		return villager != null;
	}
	
	/**
	 * Teleports this shopkeeper to its spawn location.
	 */
	public void teleport() {
		if (villager != null) {
			World w = Bukkit.getWorld(world);
			villager.teleport(new Location(w, x + .5, y, z + .5, villager.getLocation().getYaw(), villager.getLocation().getPitch()));
		}
	}
	
	/**
	 * Removes this shopkeeper from the world.
	 */
	public void remove() {
		if (villager != null) {
			villager.remove();
			villager = null;
		}
	}
	
	/**
	 * Gets a string identifying the chunk this shopkeeper spawns in, 
	 * in the format world,x,z.
	 * @return the chunk as a string
	 */
	public String getChunk() {
		return world + "," + (x >> 4) + "," + (z >> 4);
	}
	
	/**
	 * Gets the name of the world this shopkeeper lives in.
	 * @return the world name
	 */
	public String getWorldName() {
		return world;
	}
	
	/**
	 * Gets the villager entity for this shopkeeper. Can return null if the shopkeeper
	 * is not spawned in the world.
	 * @return the villager entity
	 */
	public Villager getVillager() {
		return villager;
	}
	
	/**
	 * Gets the shopkeeper's entity ID.
	 * @return the entity id, or 0 if the shopkeeper is not in the world
	 */
	public int getEntityId() {
		if (villager != null) {
			return villager.getEntityId();
		}
		return 0;
	}

	/**
	 * Gets the shopkeeper's trade recipes. This will be a list of ItemStack[3],
	 * where the first two elemets of the ItemStack[] array are the cost, and the third
	 * element is the trade result (the item sold by the shopkeeper).
	 * @return the trade recipes of this shopkeeper
	 */
	public abstract List<ItemStack[]> getRecipes();

	/**
	 * Called when a player shift-right-clicks on the villager in an attempt to edit
	 * the shopkeeper information. This method should open the editing interface.
	 * @param player the player doing the edit
	 * @return whether the player is now editing (returns false if permission fails)
	 */
	public abstract boolean onEdit(Player player);
	
	/**
	 * Called when a player clicks on any slot in the editor window.
	 * @param event the click event
	 * @return how the main plugin should handle the click
	 */
	public abstract EditorClickResult onEditorClick(InventoryClickEvent event);	
	
	/**
	 * Called when a player closes the editor window.
	 * @param event the close event
	 */
	public abstract void onEditorClose(InventoryCloseEvent event);
	
	/**
	 * Called when a player purchases an item from a shopkeeper.
	 * @param event the click event of the purchase
	 */
	public abstract void onPurchaseClick(InventoryClickEvent event);
	
	protected void setProfession() {
		((CraftVillager)villager).getHandle().setProfession(profession);
	}
	
	protected short getProfessionWoolColor() {
		switch (profession) {
		case 0: return 12;
		case 1: return 0;
		case 2: return 2;
		case 3: return 7;
		case 4: return 8;
		case 5: return 5;
		default: return 14;
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void updateRecipes() {
		try {
			EntityVillager ev = ((CraftVillager)villager).getHandle();
			
			Field recipeListField = EntityVillager.class.getDeclaredField(Settings.recipeListVar);
			recipeListField.setAccessible(true);
			MerchantRecipeList recipeList = (MerchantRecipeList)recipeListField.get(ev);
			if (recipeList == null) {
				recipeList = new MerchantRecipeList();
				recipeListField.set(ev, recipeList);
			}
			recipeList.clear();
			for (ItemStack[] recipe : getRecipes()) {
				recipeList.add(ShopRecipe.factory(recipe[0], recipe[1], recipe[2]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	/**
	 * Overwrites the AI of the villager to not wander, but to just look at customers.
	 */
	private void overwriteAI() {
		try {
			EntityVillager ev = ((CraftVillager)villager).getHandle();
			
			Field goalsField = EntityLiving.class.getDeclaredField("goalSelector");
			goalsField.setAccessible(true);
			PathfinderGoalSelector goals = (PathfinderGoalSelector) goalsField.get(ev);
			
			Field listField = PathfinderGoalSelector.class.getDeclaredField("a");
			listField.setAccessible(true);
			@SuppressWarnings("rawtypes")
			List list = (List)listField.get(goals);
			list.clear();

			goals.a(1, new PathfinderGoalTradeWithPlayer(ev));
			goals.a(1, new PathfinderGoalLookAtTradingPlayer(ev));
			goals.a(2, new PathfinderGoalLookAtPlayer(ev, EntityHuman.class, 12.0F, 1.0F));
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
}
