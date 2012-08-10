package com.nisovin.shopkeepers;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.events.OpenTradeEvent;
import com.nisovin.shopkeepers.shoptypes.PlayerShopkeeper;

class ShopListener implements Listener {

	ShopkeepersPlugin plugin;
	
	public ShopListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
	void onEntityInteract(PlayerInteractEntityEvent event) {
		if (event.getRightClicked() instanceof Villager) {
			final Villager villager = (Villager)event.getRightClicked();
			ShopkeepersPlugin.debug("Player " + event.getPlayer().getName() + " is interacting with villager at " + event.getRightClicked().getLocation());
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(event.getRightClicked().getEntityId());
			if (event.isCancelled()) {
				ShopkeepersPlugin.debug("  Cancelled by another plugin");
			} else if (shopkeeper != null && event.getPlayer().isSneaking()) {
				// modifying a shopkeeper
				ShopkeepersPlugin.debug("  Opening editor window...");
				boolean isEditing = shopkeeper.onEdit(event.getPlayer());
				if (isEditing) {
					ShopkeepersPlugin.debug("  Editor window opened");
					event.setCancelled(true);
					plugin.editing.put(event.getPlayer().getName(), villager.getEntityId());
				} else {
					ShopkeepersPlugin.debug("  Editor window NOT opened");
				}
			} else if (shopkeeper != null) {
				// only allow one person per shopkeeper
				ShopkeepersPlugin.debug("  Opening trade window...");
				OpenTradeEvent evt = new OpenTradeEvent(event.getPlayer(), shopkeeper);
				Bukkit.getPluginManager().callEvent(evt);
				if (evt.isCancelled()) {
					ShopkeepersPlugin.debug("  Trade cancelled by another plugin");
					event.setCancelled(true);
					return;
				}
				if (plugin.purchasing.containsValue(villager.getEntityId())) {
					ShopkeepersPlugin.debug("  Villager already in use!");
					plugin.sendMessage(event.getPlayer(), Settings.msgShopInUse);
					event.setCancelled(true);
					return;
				}
				// set the trade recipe list (also prevent shopkeepers adding their own recipes by refreshing them with our list)
				shopkeeper.updateRecipes();
				plugin.purchasing.put(event.getPlayer().getName(), villager.getEntityId());
				ShopkeepersPlugin.debug("  Trade window opened");
			} else if (Settings.disableOtherVillagers && shopkeeper == null) {
				// don't allow trading with other villagers
				ShopkeepersPlugin.debug("  Non-shopkeeper, trade prevented");
				event.setCancelled(true);
			} else if (shopkeeper == null) {
				ShopkeepersPlugin.debug("  Non-shopkeeper");
			}
		}
	}
	
	@EventHandler
	void onInventoryClose(InventoryCloseEvent event) {
		String name = event.getPlayer().getName();
		if (plugin.editing.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed editor window");
			int entityId = plugin.editing.remove(name);
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(entityId);
			if (shopkeeper != null) {
				if (event.getInventory().getTitle().equals(Settings.editorTitle)) {
					shopkeeper.onEditorClose(event);
					plugin.closeTradingForShopkeeper(entityId);
				}
			}
		}
		if (plugin.purchasing.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed trade window");
			plugin.purchasing.remove(name);
		}
	}
	
	@EventHandler
	void onEntityDamage(EntityDamageEvent event) {
		// don't allow damaging shopkeepers!
		if (plugin.activeShopkeepers.containsKey(event.getEntity().getEntityId())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	void onInventoryClick(InventoryClickEvent event) {
		// shopkeeper editor click
		if (event.getInventory().getTitle().equals(Settings.editorTitle)) {
			if (plugin.editing.containsKey(event.getWhoClicked().getName())) {
				// get the shopkeeper being edited
				int entityId = plugin.editing.get(event.getWhoClicked().getName());
				Shopkeeper shopkeeper = plugin.activeShopkeepers.get(entityId);
				if (shopkeeper != null) {
					// editor click
					EditorClickResult result = shopkeeper.onEditorClick(event);
					if (result == EditorClickResult.DELETE_SHOPKEEPER) {
						// close inventories
						event.getWhoClicked().closeInventory();
						plugin.editing.remove(event.getWhoClicked().getName());
						plugin.closeTradingForShopkeeper(entityId);
						
						// return egg
						if (Settings.deletingPlayerShopReturnsEgg && shopkeeper instanceof PlayerShopkeeper) {
							event.getWhoClicked().getInventory().addItem(new ItemStack(Material.MONSTER_EGG, 1, (short)120));
						}
						
						// remove shopkeeper
						plugin.activeShopkeepers.remove(entityId);
						plugin.allShopkeepersByChunk.get(shopkeeper.getChunk()).remove(shopkeeper);
						plugin.save();
					} else if (result == EditorClickResult.DONE_EDITING) {
						// end the editing session
						event.getWhoClicked().closeInventory();
						plugin.editing.remove(event.getWhoClicked().getName());
						plugin.closeTradingForShopkeeper(entityId);
						plugin.save();
					} else if (result == EditorClickResult.SAVE_AND_CONTINUE) {
						plugin.save();
					}
				} else {
					event.setCancelled(true);
					event.getWhoClicked().closeInventory();
				}
			} else {
				event.setCancelled(true);
				event.getWhoClicked().closeInventory();
			}
		}
		// purchase click
		if (event.getInventory().getName().equals("mob.villager") && event.getRawSlot() == 2 && plugin.purchasing.containsKey(event.getWhoClicked().getName())) {
			int entityId = plugin.purchasing.get(event.getWhoClicked().getName());
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(entityId);
			if (shopkeeper != null) {
				shopkeeper.onPurchaseClick(event);
			}
		}
	}
	
	@EventHandler
	void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		
		// prevent opening shop chests
		if (event.hasBlock() && event.getClickedBlock().getType() == Material.CHEST) {
			Block block = event.getClickedBlock();
			
			// check for protected chest
			if (!event.getPlayer().hasPermission("shopkeeper.bypass")) {
				if (plugin.isChestProtected(player, block)) {
					event.setCancelled(true);
					return;
				}
				for (BlockFace face : plugin.faces) {
					if (block.getRelative(face).getType() == Material.CHEST) {
						if (plugin.isChestProtected(player, block.getRelative(face))) {
							event.setCancelled(true);
							return;
						}				
					}
				}
			}
		}
		
		// check for player shop spawn
		if (Settings.createPlayerShopWithEgg && player.hasPermission("shopkeeper.player") && player.getGameMode() != GameMode.CREATIVE) {
			String playerName = player.getName();
			ItemStack inHand = player.getItemInHand();
			if (inHand != null && inHand.getType() == Material.MONSTER_EGG && inHand.getDurability() == 120) {
				event.setCancelled(true);
				if (event.getAction() == Action.RIGHT_CLICK_AIR) {
					// cycle shop options
					int option = 0;
					if (plugin.selectedShopType.containsKey(playerName)) {
						option = plugin.selectedShopType.get(playerName) + 1;
						if (option > 2) {
							option = 0;
						}
					}
					if (option == 1 && !Settings.allowPlayerBookShop) {
						option = 2;
					}
					plugin.selectedShopType.put(playerName, option);
					if (option == 0) {
						plugin.sendMessage(player, Settings.msgSelectedNormalShop);
					} else if (option == 1) {
						plugin.sendMessage(player, Settings.msgSelectedBookShop);
					} else if (option == 2) {
						plugin.sendMessage(player, Settings.msgSelectedBuyShop);
					}
				} else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					Block block = event.getClickedBlock();
					if (block.getType() == Material.CHEST && (!plugin.selectedChest.containsKey(playerName) || !plugin.selectedChest.get(playerName).equals(block))) {
						// select chest
						plugin.selectedChest.put(playerName, event.getClickedBlock());
						plugin.sendMessage(player, Settings.msgSelectedChest);
					} else {
						Block chest = plugin.selectedChest.get(playerName);
						if (chest == null) {
							plugin.sendMessage(player, Settings.msgMustSelectChest);
						} else if ((int)chest.getLocation().distance(block.getLocation()) > Settings.maxChestDistance) {
							plugin.sendMessage(player, Settings.msgChestTooFar);
						} else {
							// create player shopkeeper
							int option = 0;
							if (plugin.selectedShopType.containsKey(playerName)) {
								option = plugin.selectedShopType.get(playerName);
							}
							Shopkeeper shopkeeper = plugin.createNewPlayerShopkeeper(player, chest, block.getLocation().add(0, 1.5, 0), 0, option);
							if (shopkeeper != null) {
								// send message
								if (option == 0) {
									plugin.sendMessage(player, Settings.msgPlayerShopCreated);
								} else if (option == 1) {
									plugin.sendMessage(player, Settings.msgBookShopCreated);
								} else if (option == 2) {
									plugin.sendMessage(player, Settings.msgBuyShopCreated);
								} else {
									return;
								}
								// remove egg
								inHand.setAmount(inHand.getAmount() - 1);
								if (inHand.getAmount() > 0) {
									player.setItemInHand(inHand);
								} else {
									player.setItemInHand(null);
								}
							}
							// clear selection vars
							plugin.selectedShopType.remove(playerName);
							plugin.selectedChest.remove(playerName);
						}
					}
				}
				
			}
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	void onChunkLoad(ChunkLoadEvent event) {
		plugin.loadShopkeepersInChunk(event.getChunk());
	}

	@EventHandler
	void onChunkUnload(ChunkUnloadEvent event) {
		List<Shopkeeper> shopkeepers = plugin.allShopkeepersByChunk.get(event.getWorld().getName() + "," + event.getChunk().getX() + "," + event.getChunk().getZ());
		if (shopkeepers != null) {
			ShopkeepersPlugin.debug("Unloading " + shopkeepers.size() + " shopkeepers in chunk " + event.getChunk().getX() + "," + event.getChunk().getZ());
			for (Shopkeeper shopkeeper : shopkeepers) {
				if (shopkeeper.isActive()) {
					plugin.activeShopkeepers.remove(shopkeeper.getEntityId());
					shopkeeper.remove();
				}
			}
		}
	}
	
	@EventHandler
	void onWorldLoad(WorldLoadEvent event) {
		for (Chunk chunk : event.getWorld().getLoadedChunks()) {
			plugin.loadShopkeepersInChunk(chunk);
		}
	}
	
	@EventHandler
	void onWorldUnload(WorldUnloadEvent event) {
		String worldName = event.getWorld().getName();
		Iterator<Shopkeeper> iter = plugin.activeShopkeepers.values().iterator();
		int count = 0;
		while (iter.hasNext()) {
			Shopkeeper shopkeeper = iter.next();
			if (shopkeeper.getWorldName().equals(worldName)) {
				shopkeeper.remove();
				iter.remove();
				count++;
			}
		}
		ShopkeepersPlugin.debug("Unloaded " + count + " shopkeepers in unloaded world " + worldName);
	}
	
	@EventHandler
	void onPlayerQuit(PlayerQuitEvent event) {
		String name = event.getPlayer().getName();
		plugin.editing.remove(name);
		plugin.purchasing.remove(name);
		plugin.selectedShopType.remove(name);
		plugin.selectedChest.remove(name);
	}
	
}
