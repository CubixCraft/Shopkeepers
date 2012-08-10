package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.EditorClickResult;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.ShopkeeperType;


public class CustomQuantityPlayerShopkeeper extends PlayerShopkeeper {

	private Map<SaleType, Cost> costs;
	
	public CustomQuantityPlayerShopkeeper(ConfigurationSection config) {
		super(config);
	}

	public CustomQuantityPlayerShopkeeper(Player owner, Block chest, Location location, int profession) {
		super(owner, chest, location, profession);
		this.costs = new HashMap<CustomQuantityPlayerShopkeeper.SaleType, CustomQuantityPlayerShopkeeper.Cost>();
	}
	
	@Override
	public void load(ConfigurationSection config) {
		super.load(config);		
		costs = new HashMap<CustomQuantityPlayerShopkeeper.SaleType, CustomQuantityPlayerShopkeeper.Cost>();
		ConfigurationSection costsSection = config.getConfigurationSection("costs");
		if (costsSection != null) {
			for (String key : costsSection.getKeys(false)) {
				ConfigurationSection itemSection = costsSection.getConfigurationSection(key);
				SaleType type = new SaleType();
				Cost cost = new Cost();
				type.id = itemSection.getInt("id");
				type.data = (short)itemSection.getInt("data");
				if (itemSection.contains("enchants")) {
					type.enchants = itemSection.getString("enchants");
				}
				cost.amount = itemSection.getInt("amount");
				cost.cost = itemSection.getInt("cost");
				costs.put(type, cost);
			}
		}
	}
	
	@Override
	public void save(ConfigurationSection config) {
		super.save(config);
		ConfigurationSection costsSection = config.createSection("costs");
		int count = 0;
		for (SaleType type : costs.keySet()) {
			Cost cost = costs.get(type);
			ConfigurationSection itemSection = costsSection.createSection(count + "");
			itemSection.set("id", type.id);
			itemSection.set("data", type.data);
			if (type.enchants != null) {
				itemSection.set("enchants", type.enchants);
			}
			itemSection.set("amount", cost.amount);
			itemSection.set("cost", cost.cost);
			count++;
		}
	}
	
	@Override
	public ShopkeeperType getType() {
		return ShopkeeperType.PLAYER_NORMAL;
	}
	
	@Override
	public List<ItemStack[]> getRecipes() {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		Map<SaleType, Integer> chestItems = getItemsFromChest();
		for (SaleType type : costs.keySet()) {
			if (chestItems.containsKey(type)) {
				Cost cost = costs.get(type);
				int chestAmt = chestItems.get(type);
				if (chestAmt >= cost.amount) {
					ItemStack[] recipe = new ItemStack[3];
					setRecipeCost(recipe, cost.cost);
					recipe[2] = type.getItemStack(cost.amount);
					recipes.add(recipe);
				}
			}
		}
		return recipes;
	}
	
	@Override
	public boolean onPlayerEdit(Player player) {
		Inventory inv = Bukkit.createInventory(player, 27, Settings.editorTitle);
		
		// add the sale types
		Map<SaleType, Integer> typesFromChest = getItemsFromChest();
		int i = 0;
		for (SaleType type : typesFromChest.keySet()) {
			Cost cost = costs.get(type);
			if (cost != null) {
				inv.setItem(i, type.getItemStack(cost.amount));
				setEditColumnCost(inv, i, cost.cost);
			} else {
				inv.setItem(i, type.getItemStack(1));
				setEditColumnCost(inv, i, 0);
			}
			i++;
			if (i > 8) break;
		}
		
		// add the special buttons
		setActionButtons(inv);
		
		player.openInventory(inv);
		
		return true;
	}

	@Override
	public EditorClickResult onEditorClick(InventoryClickEvent event) {
		if (event.getRawSlot() >= 0 && event.getRawSlot() <= 7) {
			event.setCancelled(true);
			// handle changing sell stack size
			ItemStack item = event.getCurrentItem();
			if (item != null && item.getTypeId() != 0) {
				int amt = item.getAmount();
				if (event.isLeftClick()) {
					if (event.isShiftClick()) {
						amt += 10;
					} else {
						amt += 1;
					}
				} else if (event.isRightClick()) {
					if (event.isShiftClick()) {
						amt -= 10;
					} else {
						amt -= 1;
					}
				}
				if (amt <= 0) amt = 1;
				if (amt > item.getMaxStackSize()) amt = item.getMaxStackSize();
				item.setAmount(amt);
			}
			return EditorClickResult.NOTHING;
		} else {
			return super.onEditorClick(event);
		}
	}
	
	@Override
	protected void saveEditor(Inventory inv) {
		for (int i = 0; i < 8; i++) {
			ItemStack item = inv.getItem(i);
			if (item != null && item.getType() != Material.AIR) {
				int cost = getCostFromColumn(inv, i);
				if (cost > 0) {
					costs.put(new SaleType(item), new Cost(item.getAmount(), cost));
				} else {
					costs.remove(new SaleType(item));
				}
			}
		}
	}
	
	@Override
	public void onPurchaseClick(final InventoryClickEvent event) {
		// prevent shift clicks
		if (event.isShiftClick() || event.isRightClick()) {
			event.setCancelled(true);
			return;
		}
		
		// get type and cost
		ItemStack item = event.getCurrentItem();
		SaleType type = new SaleType(item);
		if (!costs.containsKey(type)) {
			event.setCancelled(true);
			return;
		}
		Cost cost = costs.get(type);
		if (cost.amount != item.getAmount()) {
			event.setCancelled(true);
			return;
		}
		
		// get chest
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() != Material.CHEST) {
			event.setCancelled(true);
			return;
		}
		
		// remove item from chest
		Inventory inv = ((Chest)chest.getState()).getInventory();
		ItemStack[] contents = inv.getContents();
		boolean removed = removeFromInventory(item, contents);
		if (!removed) {
			event.setCancelled(true);
			return;
		}
		
		// add earnings to chest
		if (Settings.highCurrencyItem <= 0 || cost.cost <= Settings.highCurrencyMinCost) {
			boolean added = addToInventory(new ItemStack(Settings.currencyItem, cost.cost, Settings.currencyItemData), contents);
			if (!added) {
				event.setCancelled(true);
				return;
			}
		} else {
			int highCost = cost.cost / Settings.highCurrencyValue;
			int lowCost = cost.cost % Settings.highCurrencyValue;
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
		}

		// save chest contents
		inv.setContents(contents);
	}
	
	private Map<SaleType, Integer> getItemsFromChest() {
		Map<SaleType, Integer> map = new LinkedHashMap<SaleType, Integer>();
		Block chest = Bukkit.getWorld(world).getBlockAt(chestx, chesty, chestz);
		if (chest.getType() == Material.CHEST) {
			Inventory inv = ((Chest)chest.getState()).getInventory();
			ItemStack[] contents = inv.getContents();
			for (ItemStack item : contents) {
				if (item != null && item.getType() != Material.AIR && item.getTypeId() != Settings.currencyItem && item.getTypeId() != Settings.highCurrencyItem && item.getType() != Material.WRITTEN_BOOK) {
					SaleType si = new SaleType(item);
					if (map.containsKey(si)) {
						map.put(si, map.get(si) + item.getAmount());
					} else {
						map.put(si, item.getAmount());
					}
				}
			}
		}
		return map;
	}
	
	private class SaleType {
		int id;
		short data;
		String enchants;
		
		public SaleType() {
			
		}
		
		public SaleType(ItemStack item) {
			id = item.getTypeId();
			data = item.getDurability();
			Map<Enchantment, Integer> enchantments = item.getEnchantments();
			if (enchantments != null && enchantments.size() > 0) {
				enchants = "";
				for (Enchantment e : enchantments.keySet()) {
					enchants += e.getId() + ":" + enchantments.get(e) + " ";
				}
				enchants = enchants.trim();
			}
		}
		
		ItemStack getItemStack(int amount) {
			ItemStack item = new ItemStack(id, amount, data);
			if (enchants != null) {
				String[] dataList = enchants.split(" ");
				for (String s : dataList) {
					String[] data = s.split(":");
					item.addUnsafeEnchantment(Enchantment.getById(Integer.parseInt(data[0])), Integer.parseInt(data[1]));
				}
			}
			return item;
		}
		
		@Override
		public int hashCode() {
			return (id + " " + data + (enchants != null ? " " + enchants : "")).hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof SaleType)) return false;
			SaleType i = (SaleType)o;
			boolean test = (i.id == this.id && i.data == this.data);
			if (!test) return false;
			if (i.enchants == null && this.enchants == null) return true;
			if (i.enchants == null || this.enchants == null) return false;
			return i.enchants.equals(this.enchants);
		}
	}
	
	private class Cost {
		int amount;
		int cost;
		
		public Cost() {
			
		}
		
		public Cost(int amount, int cost) {
			this.amount = amount;
			this.cost = cost;
		}
	}
}
