/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr.internal;

import java.math.BigDecimal;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openhab.binding.wmr.WmrBindingProvider;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Christian Sowada
 * @since 1.7.0
 */
public class WmrBinding extends AbstractBinding<WmrBindingProvider> implements ManagedService, IWmrListener {

	private static final Logger logger = LoggerFactory
			.getLogger(WmrBinding.class);


	private HIDConnector connector;

	/* (non-Javadoc)
	 * @see org.osgi.service.cm.ManagedService#updated(java.util.Dictionary)
	 */
	@Override
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {

		logger.info("Update WMR Binding configuration ...");

		// stop last thread if active
		if(connector != null && connector.isAlive()) {
			connector.interrupt();
		}
		
		HIDConnector.loadHIDLibrary();
		connector = new HIDConnector();
		
		// add event listener
		connector.addListener(this);

		// start thread
		connector.start();

	}

	/* (non-Javadoc)
	 * @see org.openhab.core.binding.AbstractBinding#activate()
	 */
	public void activate() {
		super.activate();
		logger.debug("WMR binding has been started.");
	}

	/* (non-Javadoc)
	 * @see org.openhab.core.binding.AbstractBinding#deactivate()
	 */
	public void deactivate() {
		super.deactivate();

		logger.debug("WMR binding has been stopped.");

		if(connector != null && connector.isAlive()) {
			connector.interrupt();
			connector = null;
		}
	}

	@Override
	public void dataReceived(Map<String, Object> data) {
		
		for (WmrBindingProvider provider : providers) {
			
			for (Entry<String, Object> entry : data.entrySet()) {
				List<String> itemNames = provider.getItemNames(entry.getKey());
				
				BigDecimal b = NumberUtils.toBigDecimal(entry.getValue());
				State state = StateUtils.convertToState(
						b != null ? b : entry.getValue());
				
				for (String itemName : itemNames) {
					eventPublisher.postUpdate(itemName, state);
				}
			}
		}
	}
	
}