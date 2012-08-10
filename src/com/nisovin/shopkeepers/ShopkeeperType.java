package com.nisovin.shopkeepers;

/**
 * Type of shopkeeper.
 *
 */
public enum ShopkeeperType {

	/**
	 * A admin shopkeeper, which has infinite supply and does not store income anywhere.
	 */
	ADMIN,
	
	/**
	 * A normal player shopkeeper that uses a chest for the item supply and to store income.
	 */
	PLAYER_NORMAL,
	
	/**
	 * A player shopkeeper that sells books, using a chest for the written book source, 
	 * a supply of empty book & quills, and to store income.
	 */
	PLAYER_BOOK,
	
	/**
	 * A player shopkeeper that buys items instead of selling, using a chest as a source
	 * for the currency and to deposit items.
	 */
	PLAYER_BUY
	
}
