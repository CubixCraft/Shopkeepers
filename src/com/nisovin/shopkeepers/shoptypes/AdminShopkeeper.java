package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.NBTTagList;
import net.minecraft.server.NBTTagString;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.EditorClickResult;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.Shopkeeper;
import com.nisovin.shopkeepers.ShopkeeperType;


/**
 * Represents a shopkeeper that is managed by an admin. This shopkeeper will have unlimited supply
 * and will not save earnings anywhere.
 *
 */
public class AdminShopkeeper extends Shopkeeper {

	protected List<ItemStack[]> recipes;
	
	public AdminShopkeeper(ConfigurationSection config) {
		super(config);
	}
	
	/**
	 * Creates a new shopkeeper and spawns it in the world. This should be used when a player is
	 * creating a new shopkeeper.
	 * @param location the location to spawn at
	 * @param prof the id of the profession
	 */
	public AdminShopkeeper(Location location, int prof) {
		super(location, prof);
		recipes = new ArrayList<ItemStack[]>();
	}
	
	@Override
	public void load(ConfigurationSection config) {
		super.load(config);
		recipes = new ArrayList<ItemStack[]>();
		ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
		if (recipesSection != null) {
			for (String key : recipesSection.getKeys(false)) {
				ConfigurationSection recipeSection = recipesSection.getConfigurationSection(key);
				ItemStack[] recipe = new ItemStack[3];
				for (int i = 0; i < 3; i++) {
					if (recipeSection.contains(i + "")) {
						recipe[i] = loadItemStack(recipeSection.getConfigurationSection(i + ""));
					}
				}
				recipes.add(recipe);
			}
		}
	}
	
	@Override
	public void save(ConfigurationSection config) {
		super.save(config);
		config.set("type", "admin");
		ConfigurationSection recipesSection = config.createSection("recipes");
		int count = 0;
		for (ItemStack[] recipe : recipes) {
			ConfigurationSection recipeSection = recipesSection.createSection(count + "");
			for (int i = 0; i < 3; i++) {
				if (recipe[i] != null) {
					saveItemStack(recipe[i], recipeSection.createSection(i + ""));
				}
			}
			count++;
		}
	}
	
	@Override
	public ShopkeeperType getType() {
		return ShopkeeperType.ADMIN;
	}
	
	@Override
	public boolean onEdit(Player player) {
		if (player.hasPermission("shopkeeper.admin")) {
			// get the shopkeeper's trade options
			Inventory inv = Bukkit.createInventory(player, 27, Settings.editorTitle);
			List<ItemStack[]> recipes = getRecipes();
			for (int i = 0; i < recipes.size() && i < 8; i++) {
				ItemStack[] recipe = recipes.get(i);
				inv.setItem(i, recipe[0]);
				inv.setItem(i + 9, recipe[1]);
				inv.setItem(i + 18, recipe[2]);
			}
			// add the special buttons
			inv.setItem(8, new ItemStack(Settings.saveItem));
			inv.setItem(17, new ItemStack(Material.WOOL, 1, getProfessionWoolColor()));
			inv.setItem(26, new ItemStack(Settings.deleteItem));
			// show editing inventory
			player.openInventory(inv);
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public EditorClickResult onEditorClick(InventoryClickEvent event) {
		// check for special buttons
		if (event.getRawSlot() == 8) {
			// it's the save button - get the trades and save them to the shopkeeper
			Inventory inv = event.getInventory();
			saveEditor(inv, event.getWhoClicked());
			event.setCancelled(true);
			return EditorClickResult.DONE_EDITING;
		} else if (event.getRawSlot() == 17) {
			// it's the cycle button - cycle to next profession
			profession += 1;
			if (profession > 5) profession = 0;
			setProfession();
			event.getInventory().setItem(17, new ItemStack(Material.WOOL, 1, getProfessionWoolColor()));
			event.setCancelled(true);
			return EditorClickResult.SAVE_AND_CONTINUE;
		} else if (event.getRawSlot() == 26) {
			// it's the delete button - remove the shopkeeper
			remove();
			event.setCancelled(true);
			return EditorClickResult.DELETE_SHOPKEEPER;
		} else {
			return EditorClickResult.NOTHING;
		}
	}

	@Override
	public void onEditorClose(InventoryCloseEvent event) {
		Inventory inv = event.getInventory();
		saveEditor(inv, event.getPlayer());
	}
	
	private void saveEditor(Inventory inv, HumanEntity player) {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		for (int i = 0; i < 8; i++) {
			ItemStack cost1 = inv.getItem(i);
			ItemStack cost2 = inv.getItem(i + 9);
			ItemStack result = inv.getItem(i + 18);
			if (cost1 != null && result != null) {
				// save trade recipe
				ItemStack[] recipe = new ItemStack[3];
				recipe[0] = cost1;
				recipe[1] = cost2;
				recipe[2] = result;
				recipes.add(recipe);
			} else {
				// return unused items to inventory
				if (cost1 != null) {
					player.getInventory().addItem(cost1);
				}
				if (cost2 != null) {
					player.getInventory().addItem(cost2);
				}
				if (result != null) {
					player.getInventory().addItem(result);
				}
			}
		}
		setRecipes(recipes);
	}
	
	@Override
	public void onPurchaseClick(InventoryClickEvent event) {
		if (event.getCurrentItem().getType() == Material.MAP && event.getCurrentItem().getDurability() > 0) {
			// handle map manually
			short mapId = event.getCurrentItem().getDurability();
			// update purchase slot and cursor item
			event.setCursor(event.getCurrentItem());
			event.setCurrentItem(null);
			// find recipe
			for (ItemStack[] recipe : getRecipes()) {
				if (recipe[2].getType() == Material.MAP && recipe[2].getDurability() == mapId) {
					// update cost 1
					if (recipe[0] != null) {
						ItemStack cost = event.getInventory().getItem(0);
						cost.setAmount(cost.getAmount() - recipe[0].getAmount());
						if (cost.getAmount() > 0) {
							event.getInventory().setItem(0, cost);
						} else {
							event.getInventory().setItem(0, null);
						}
					}
					// update cost 2
					if (recipe[1] != null) {
						ItemStack cost = event.getInventory().getItem(1);
						cost.setAmount(cost.getAmount() - recipe[1].getAmount());
						if (cost.getAmount() > 0) {
							event.getInventory().setItem(1, cost);
						} else {
							event.getInventory().setItem(1, null);
						}
					}
				}
			}
			// do it!
			event.setResult(Result.ALLOW);
		}
	}
	
	@Override
	public List<ItemStack[]> getRecipes() {
		return recipes;
	}

	private void setRecipes(List<ItemStack[]> recipes) {
		this.recipes = recipes;
	}
	
	/**
	 * Loads an ItemStack from a config section.
	 * @param config
	 * @return
	 */
	private ItemStack loadItemStack(ConfigurationSection config) {
		ItemStack item = new ItemStack(config.getInt("id"), config.getInt("amt"), (short)config.getInt("data"));
		if (config.contains("enchants")) {
			List<String> list = config.getStringList("enchants");
			for (String s : list) {
				String[] enchantData = s.split(" ");
				item.addUnsafeEnchantment(Enchantment.getById(Integer.parseInt(enchantData[0])), Integer.parseInt(enchantData[1]));
			}
		}
		if (item.getType() == Material.WRITTEN_BOOK && config.contains("title") && config.contains("author") && config.contains("pages")) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setString("title", config.getString("title"));
			tag.setString("author", config.getString("author"));
			List<String> pages = config.getStringList("pages");
			NBTTagList tagPages = new NBTTagList();
			for (String page : pages) {
				NBTTagString tagPage = new NBTTagString(null, page);
				tagPages.add(tagPage);
			}
			tag.set("pages", tagPages);
			if (config.contains("extra")) {
				ConfigurationSection extraDataSection = config.getConfigurationSection("extra");
				for (String key : extraDataSection.getKeys(false)) {
					tag.setString(key, extraDataSection.getString(key));
				}
			}
			item = new CraftItemStack(item);
			((CraftItemStack)item).getHandle().tag = tag;
		}
		return item;
	}
	
	/**
	 * Saves an ItemStack to a config section.
	 * @param item
	 * @param config
	 */
	private void saveItemStack(ItemStack item, ConfigurationSection config) {
		config.set("id", item.getTypeId());
		config.set("data", item.getDurability());
		config.set("amt", item.getAmount());
		Map<Enchantment, Integer> enchants = item.getEnchantments();
		if (enchants.size() > 0) {
			List<String> list = new ArrayList<String>();
			for (Enchantment enchant : enchants.keySet()) {
				list.add(enchant.getId() + " " + enchants.get(enchant));
			}
			config.set("enchants", list);
		}
		if (item.getType() == Material.WRITTEN_BOOK && item instanceof CraftItemStack) {
			NBTTagCompound tag = ((CraftItemStack)item).getHandle().tag;
			if (tag != null && tag.hasKey("title") && tag.hasKey("author") && tag.hasKey("pages")) {
				config.set("title", tag.getString("title"));
				config.set("author", tag.getString("author"));
				List<String> pages = new ArrayList<String>();
				NBTTagList tagPages = (NBTTagList)tag.get("pages");
				for (int i = 0; i < tagPages.size(); i++) {
					NBTTagString tagPage = (NBTTagString)tagPages.get(i);
					if (tagPage.data != null) {
						pages.add(tagPage.data);
					}
				}
				config.set("pages", pages);
				Map<String, String> extraData = new HashMap<String, String>();
				for (Object o : tag.c()) {
					if (o instanceof NBTTagString) {
						NBTTagString s = (NBTTagString)o;
						String name = s.getName();
						if (!name.equals("title") && !name.equals("author")) {
							extraData.put(name, s.data);
						}
					}
				}
				if (extraData.size() > 0) {
					ConfigurationSection extraDataSection = config.createSection("extra");
					for (String key : extraData.keySet()) {
						extraDataSection.set(key, extraData.get(key));
					}
				}
			}
		}
	}
	
}
