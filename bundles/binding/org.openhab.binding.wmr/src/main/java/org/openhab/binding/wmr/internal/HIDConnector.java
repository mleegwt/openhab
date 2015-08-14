package org.openhab.binding.wmr.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codeminders.hidapi.HIDDevice;
import com.codeminders.hidapi.HIDManager;

/**
 * @author CSo
 *
 */
public class HIDConnector extends Thread {

	private static final String HID_LIBRARY = "hidapi";

	private static final Logger logger = LoggerFactory
			.getLogger(HIDConnector.class);

	/**
	 * @return
	 */
	public static boolean loadHIDLibrary() {
		logger.debug("Trying to load native library {} ...", HID_LIBRARY);
		return ClassPathLibraryLoaderCustom.loadNativeHIDLibrary();
	}

	protected HIDDevice hidDevice;

	protected HIDManager hidManager;

	protected long lastRequestTimer;

	private ArrayList<IWmrListener> listeners = new ArrayList<IWmrListener>();

	protected ByteBuffer stationBuffer = ByteBuffer.allocate(100);

	/**
	 * Constructor to init connector
	 */
	public HIDConnector() {
		super();
		this.setDaemon(true);
		this.setName("WMR HID Connection");
	}

	/**
	 * Add a WMR Listener
	 * @param listener
	 */
	public void addListener(IWmrListener listener) {
		listeners.add(listener);
	}

	/**
	 * Close the HID connection
	 * @throws IOException
	 */
	protected void close() throws IOException {

		logger.debug("Close WMR connection ...");

		if (hidDevice != null) {
			hidDevice.close();
		}
	}

	/**
	 * Open the HID connection
	 * @throws IOException
	 */
	protected void connect() throws IOException {

		hidManager = HIDManager.getInstance();
		hidDevice = hidManager.openById(
				WmrConstants.HID_VENDOR, WmrConstants.HID_PRODUCT, null);

		if (hidDevice == null) {
			throw(new IOException("Could'nt open weather station HID device"));
		}

		logger.info("Connected to WMR HID device ...");
	}

	/**
	 * Parse the received frame from hid device
	 * @param data
	 */
	protected void parseFrame(byte[] data) {

		if(logger.isTraceEnabled()) {
			logger.trace("Received WMR frame {} ...", WmrUtils.byteArrayToHex(data));
		}
		
		Map<String, Object> results = WmrUtils.parse(data);

		for (IWmrListener listener : listeners) {
			listener.dataReceived(results);
		}
	}

	/**
	 * Parse the low-level response from the hid device
	 * @param bytes Number of received bytes
	 * @param buffer Received data
	 * @throws IOException
	 */
	protected void parseResponse(int bytes, byte[] buffer) throws IOException {

		int count = buffer[0];

		if(count > 0) {
			stationBuffer.put(buffer, 1, count);
		}

		int startPos = -1;
		int endPos = -1;

		for (int i = 0; i < stationBuffer.position()-1; i++) {

			if(startPos == -1) {
				if(stationBuffer.get(i) == (byte)0xFF && 
						stationBuffer.get(i+1) == (byte)0xFF) {
					startPos = i+2;
				}
			}

			if(i>1 && startPos > 0) {
				if(stationBuffer.get(i) == (byte)0xFF && 
						stationBuffer.get(i+1) == (byte)0xFF) {
					endPos = i;
				}
			}
		}

		if(startPos > -1 && endPos > -1 && startPos < endPos) {

			int len = endPos - startPos;
			int lastPos = stationBuffer.position();
			byte buff[] = new byte[len];

			// extract current frame to buffer
			stationBuffer.position(startPos);
			stationBuffer.limit(endPos);
			stationBuffer.get(buff);

			// parse received byte array
			parseFrame(buff);

			// compact buffer
			stationBuffer.position(endPos);
			stationBuffer.limit(lastPos);
			stationBuffer.compact();
		}
	}

	/**
	 * Remove a WMR Listener
	 * @param listener
	 */
	public void removeListener(IWmrListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Request data from hid device
	 * @throws IOException
	 */
	private void requestData() throws IOException {

		// request every x seconds from weather station
		if(System.currentTimeMillis() - lastRequestTimer > 70 * 1000) {

			// request data from weather station
			hidDevice.write(WmrConstants.HID_CMD_INIT);
			hidDevice.write(WmrConstants.HID_CMD_REQUEST);

			// set last request time
			lastRequestTimer = System.currentTimeMillis();
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {

		byte[] responseBuffer = new byte[9];

		// start hid connection
		try {
			connect();
		} catch (IOException e) {
			logger.error("An IO exeption has occured while connecting!", e);
		}

		while (!isInterrupted() && hidDevice != null) {

			try {
				// request data every n seconds
				requestData();	

				// read hid data
				int responseBytes =	hidDevice.readTimeout(responseBuffer, 10 * 1000);

				parseResponse(responseBytes, responseBuffer);
			} catch (Exception e) {
				
				if(hidDevice == null) {
					try {
						logger.info("Try to reconnect to WMR HID device ...");
						connect();
						
					} catch (IOException e1) {
						logger.error("An exception has occured while reconnecting hid device!", e);
						hidDevice = null;
					}
				}
				
				logger.error("An exception has occured in thread loop!", e);
				stationBuffer.clear();
			}
		}

		// close hid connection after thread end
		try {
			close();
		} catch (IOException e) {
			logger.error("An IO exeption has occured while closing!", e);
		}
	}
}
