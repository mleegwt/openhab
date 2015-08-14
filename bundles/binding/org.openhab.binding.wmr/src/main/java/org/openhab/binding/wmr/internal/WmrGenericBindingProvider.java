/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.wmr.WmrBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Christian Sowada
 * @since 1.7.0
 */
public class WmrGenericBindingProvider extends AbstractGenericBindingProvider implements WmrBindingProvider {

	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory
			.getLogger(WmrGenericBindingProvider.class);

	/**
	 * This is an internal data structure to store information from the binding
	 * config strings.
	 */
	class WmrBindingConfig implements BindingConfig {
		public HashMap<String, Object> map = new HashMap<String, Object>();
	}

	/* (non-Javadoc)
	 * @see org.openhab.model.item.binding.BindingConfigReader#getBindingType()
	 */
	@Override
	public String getBindingType() {
		return "wmr";
	}

	/* (non-Javadoc)
	 * @see org.openhab.binding.ebus.EBusBindingProvider#getItemName(java.lang.String)
	 */
	@Override
	public List<String> getItemNames(String id) {

		ArrayList<String> list = new ArrayList<String>();

		// Search for id and class
		for (Entry<String, BindingConfig> entry : bindingConfigs.entrySet()) {
			WmrBindingConfig cfg = (WmrBindingConfig) entry.getValue();
			
			if(cfg.map.containsKey("id")) {
				if(StringUtils.equals((String) cfg.map.get("id"), id)) {
					list.add(entry.getKey());
				}
			}
		}

		return list;
	}

	/* (non-Javadoc)
	 * @see org.openhab.model.item.binding.AbstractGenericBindingProvider#processBindingConfiguration(java.lang.String, org.openhab.core.items.Item, java.lang.String)
	 */
	@Override
	public void processBindingConfiguration(String context, Item item,
			String bindingConfig) throws BindingConfigParseException {

		super.processBindingConfiguration(context, item, bindingConfig);

		WmrBindingConfig config = new WmrBindingConfig();
		for (String set : bindingConfig.trim().split(",")) {
			String[] configParts = set.split(":");
			if (configParts.length > 2) {
				throw new BindingConfigParseException("WMR binding configuration must not contain more than two parts");
			}

			configParts[0] = configParts[0].trim().toLowerCase();
			configParts[1] = configParts[1].trim();


			config.map.put(configParts[0], configParts[1]);
			
		}

		addBindingConfig(item, config);
	}

	/**
	 * Simple get a value from binding configuration or use default value.
	 * @param itemName
	 * @param type
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected <T> T get(String itemName, String type, T defaultValue) {
		WmrBindingConfig bindingConfig = (WmrBindingConfig) bindingConfigs.get(itemName);
		if(bindingConfig != null) {
			if(bindingConfig.map.containsKey(type)) {
				return (T) bindingConfig.map.get(type);
			}
		}
		return defaultValue;
	}

	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		// noop
	}
}