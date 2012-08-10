package com.nisovin.shopkeepers;

/**
 * Tells the main plugin what to do after an inventory slot has been clicked
 * in a shopkeeper editor window.
 *
 */
public enum EditorClickResult {

	/**
	 * Do nothing.
	 */
	NOTHING,
	
	/**
	 * Some changes have occured, so save, but editing will continue.
	 */
	SAVE_AND_CONTINUE,
	
	/**
	 * Done editing, so save and close the editor window.
	 */
	DONE_EDITING,
	
	/**
	 * Delete this shopkeeper and save.
	 */
	DELETE_SHOPKEEPER
	
}
