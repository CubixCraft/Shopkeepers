package com.nisovin.shopkeepers;

import net.minecraft.server.MerchantRecipe;

class ShopRecipe extends MerchantRecipe {

	public static ShopRecipe factory(org.bukkit.inventory.ItemStack cost1, org.bukkit.inventory.ItemStack cost2, org.bukkit.inventory.ItemStack result) {
		return new ShopRecipe(convertItemStack(cost1), convertItemStack(cost2), convertItemStack(result));
	}
	
	private ShopRecipe(net.minecraft.server.ItemStack cost1, net.minecraft.server.ItemStack cost2, net.minecraft.server.ItemStack result) {
		super(cost1, cost2, result);
	}
	
	@Override
	public int getUses() {
		return 0;
	}
	
	private static net.minecraft.server.ItemStack convertItemStack(org.bukkit.inventory.ItemStack item) {
		if (item == null) return null;
		return org.bukkit.craftbukkit.inventory.CraftItemStack.createNMSItemStack(item);
	}

}
