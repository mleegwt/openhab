package org.openhab.binding.wmr.internal;

import java.util.Map;

public interface IWmrListener {

	/**
	 * Called if a data received from weather station
	 * @param data The data as map
	 */
	public void dataReceived(Map<String, Object> data);
	
}
