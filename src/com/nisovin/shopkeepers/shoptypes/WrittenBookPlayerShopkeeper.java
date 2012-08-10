package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.NBTTagCompound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.ShopkeeperType;


public class WrittenBookPlayerShopkeeper extends PlayerShopkeeper {

	private Map<String, Integer> costs;
	
	public WrittenBookPlayerShopkeeper(ConfigurationSection config) {
		super(config);
	}

	public WrittenBookPlayerShopkeeper(Player owner, Block chest, Location location, int profession) {
		super(owner, chest, location, profession);
		this.costs = new HashMap<String, Integer>();
	}
	
	@Override
	public void load(ConfigurationSection config) {
		super.load(config);
		costs = new HashMap<String, Integer>();
		ConfigurationSection costsSection = config.getConfigurationSection("costs");
		if (costsSection != null) {
			for (String key : costsSection.getKeys(false)) {
				costs.put(key, costsSection.getInt(key));
			}
		}
	}
	
	@Override
	public void save(ConfigurationSection config) {
		super.save(config);
		config.set("type", "book");
		ConfigurationSection costsSection = config.createSection("costs");
		for (String title : costs.keySet()) {
			costsSection.set(title, costs.get(title));
		}
	}
	
	@Override
	public ShopkeeperType getType() {
		return ShopkeeperType.PLAYER_BOOK;
	}

	@Override
	public List<ItemStack[]> getRecipes() {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		if (chestHasBlankBooks()) {
			List<ItemStack> books = getBooksFromChest();
			for (ItemStack book : books) {
				if (book != null) {
					String title = getTitleOfBook(book);
					if (title != null && costs.containsKey(title)) {
						int cost = costs.get(title);
						ItemStack[] recipe = new ItemStack[3];
						setRecipeCost(recipe, cost);
						recipe[2] = book.clone();
						recipes.add(recipe);
					}
				}
			}
		}
		return recipes;
	}

	@Override
	public boolean onPlayerEdit(Player player) {
		Inventory inv = Bukkit.createInventory(player, 27, Settings.editorTitle);
		
		List<ItemStack> books = getBooksFromChest();
		
		for (int i = 0; i < books.size() && i < 8; i++) {
			String title = getTitleOfBook(books.get(i));
			if (title != null) {
				int cost = 0;
				if (costs.containsKey(title)) {
					cost = costs.get(title);
				}
				inv.setItem(i, books.get(i));
				setEditColumnCost(inv, i, cost);
			}
			
		}
		
		// add the special buttons
		setActionButtons(inv);
		
		player.openInventory(inv);
		
		return true;
	}

	@Override
	protected void saveEditor(Inventory inv) {
		for (int i = 0; i < 8; i++) {
			ItemStack item = inv.getItem(i);
			if (item != null && item.getType() == Material.WRITTEN_BOOK) {
				String title = getTitleOfBook(item);
				if (title != null) {
					int cost = getCostFromColumn(inv, i);
					if (cost > 0) {
						costs.put(title, cost);
					} else {
						costs.remove(title);
					}
				}
			}
		}
	}

	@Override
	public void onPurchaseClick(InventoryClickEvent event) {
		ItemStack book = event.getCurrentItem();
		String title = getTitleOfBook(book);
		if (title == null) {
			event.setCancelled(true);
			return;
		}
		
		// get chest
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() != Material.CHEST) {
			event.setCancelled(true);
			return;
		}
		
		// remove blank book from chest
		boolean removed = false;
		Inventory inv = ((Chest)chest.getState()).getInventory();
		ItemStack[] contents = inv.getContents();
		for (int i = 0; i < contents.length; i++) {
			if (contents[i] != null && contents[i].getType() == Material.BOOK_AND_QUILL) {
				if (contents[i].getAmount() == 1) {
					contents[i] = null;
				} else {
					contents[i].setAmount(contents[i].getAmount() - 1);
				}
				removed = true;
				break;
			}
		}
		if (!removed) {
			event.setCancelled(true);
			return;
		}		

		// get cost
		int cost = 0;
		if (costs.containsKey(title)) {
			cost = costs.get(title);
		} else {
			event.setCancelled(true);
			return;
		}
		
		// add earnings to chest
		int highCost = cost / Settings.highCurrencyValue;
		int lowCost = cost % Settings.highCurrencyValue;
		boolean added = false;
		if (highCost > 0) {
			added = addToInventory(new ItemStack(Settings.highCurrencyItem, highCost, Settings.highCurrencyItemData), contents);
			if (!added) {
				event.setCancelled(true);
				return;
			}
		}
		if (lowCost > 0) {
			added = addToInventory(new ItemStack(Settings.currencyItem, lowCost, Settings.currencyItemData), contents);
			if (!added) {
				event.setCancelled(true);
				return;
			}
		}
		
		// set chest contents
		inv.setContents(contents);
	}
	
	private List<ItemStack> getBooksFromChest() {
		List<ItemStack> list = new ArrayList<ItemStack>();
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() == Material.CHEST) {
			Inventory inv = ((Chest)chest.getState()).getInventory();
			for (ItemStack item : inv.getContents()) {
				if (item != null && item.getType() == Material.WRITTEN_BOOK && isBookAuthoredByShopOwner(item)) {
					list.add(item);
				}
			}
		}
		return list;
	}
	
	private String getTitleOfBook(ItemStack book) {
		if (book instanceof CraftItemStack) {
			NBTTagCompound tag = ((CraftItemStack)book).getHandle().tag;
			if (tag != null && tag.hasKey("title")) {
				return tag.getString("title");
			}
		}
		return null;
	}
	
	private boolean isBookAuthoredByShopOwner(ItemStack book) {
		if (book instanceof CraftItemStack) {
			NBTTagCompound tag = ((CraftItemStack)book).getHandle().tag;
			if (tag != null && tag.hasKey("author")) {
				return tag.getString("author").equalsIgnoreCase(owner);
			}
		}
		return false;
	}
	
	private boolean chestHasBlankBooks() {
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() == Material.CHEST) {
			Inventory inv = ((Chest)chest.getState()).getInventory();
			return inv.contains(Material.BOOK_AND_QUILL);
		}
		return false;
	}

}
