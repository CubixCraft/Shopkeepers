package com.nisovin.shopkeepers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

class ChestProtectListener implements Listener {

	ShopkeepersPlugin plugin;
	
	ChestProtectListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler(ignoreCancelled=true)
	void onBlockBreak(BlockBreakEvent event) {
		if (event.getBlock().getType() == Material.CHEST) {
			Player player = event.getPlayer();
			Block block = event.getBlock();
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
	}
	
	@EventHandler(ignoreCancelled=true)
	void onBlockPlace(BlockPlaceEvent event) {
		if (event.getBlock().getType() == Material.CHEST) {
			Player player = event.getPlayer();
			Block block = event.getBlock();
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
	
}
