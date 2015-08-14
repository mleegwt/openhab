/**
* Copyright (c) 2010-2015, openHAB.org and others.
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*/
package org.openhab.binding.wmr.internal;

import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.wmr.internal.WmrConstants.WIND_DIR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to encode weather station telegrams 
 * 
 * @author Christian Sowada
 * @since 1.7.0
 */
public class WmrUtils {

	private static final Logger logger = LoggerFactory
			.getLogger(WmrUtils.class);

	
	/**
	 * @param a
	 * @return
	 */
	protected static String byteArrayToHex(byte[] a) {
		StringBuilder sb = new StringBuilder(a.length * 2);
		for(byte b: a) {
			sb.append(String.format("%02x", b & 0xff)).append(' ');
		}
		
		return sb.toString();
	}
	
	/**
	 * Convert inch to millimeter
	 * @param value
	 * @return
	 */
	private static float convertIn2Mm(float value) {
		value = value / 100f * 25.4f;
		value = Math.round(value * 10f) / 10f;
		return (value);
	}
	
	private static boolean isNegative(int data) {
		return data != 0;
	}

	protected static boolean isValid(byte[] data) {
		return isValid(data, data.length);
	}
	
	protected static boolean isValid(byte[] data, int len) {
		
		if(data.length < len) {
			return false;
		}
		
		int crc = toInt(data[len-2]) + toInt(data[len-1])*256;
		int crc2 = 0;
		for (int i = 0; i < len-2; i++) {
			crc2 += toInt(data[i]);
		}
		return crc == crc2;
	}
	
	protected static Map<String, Object> parse(byte[] data) {

		if(data.length > 2) {
			
			switch (data[1]) {
			
			case WmrConstants.TYPE_ANEMOMETER:
				logger.debug("TYPE_ANEMOMETER");
				return parseWind(data);
				
			case WmrConstants.TYPE_THERMOMETER:
				logger.debug("TYPE_THERMOMETER");
				return parseThermometer(data);
				
			case WmrConstants.TYPE_BAROMETER:
				logger.debug("TYPE_BAROMETER");
				return parseBarometer(data);
				
			case WmrConstants.TYPE_RAINFALL:
				logger.debug("TYPE_RAINFALL");
				return parseRainfall(data);
				
			default:
				break;
			}
		}
		return new HashMap<String, Object>();
	}
	
	private static Map<String, Object> parseWind(byte[] data) {
		
		Map<String, Object> result = new HashMap<String, Object>();
		
		if(!isValid(data, 11)) {
			logger.debug("Invalid anemometer data ...");
			return result;
		}
		
		boolean lowBattery = toInt(data[0]) / 64 == 0 ? false : true;
		
		// wind speed gust (m/s)
		float windGust = (256f * (toInt(data[5]) % 16) + toInt(data[4])) / 10f;
		
		// wind speed average (m/s)
		float windAverage =	(16f * toInt(data[6]) + toInt(data[5]) / 16) / 10f;
		
		// wind direction (deg)
		int windTmpDir = data[2] % 16;
		int windDirection =	Math.round(windTmpDir * 22.5f);

		WIND_DIR[] values = WmrConstants.WIND_DIR.values();
		
		String windDirectionStr = (String) (windTmpDir < values.length ? 
				values[windTmpDir] : "Unknown value");

		// wind chill
		int chillSign = toInt(data[8]) / 16;	
		Float windChill = Float.NaN;
		
		if((chillSign & 0x2) == 0) {
			windChill = (float) toInt(data[7]);
			if(isNegative(chillSign / 8)) {
				// negativ value
				windChill *= -1;
			}
		}

		result.put("wind.lowbatt", lowBattery);
		result.put("wind.gust", windGust);
		result.put("wind.average", windAverage);
		result.put("wind.direction", windDirection);
		result.put("wind.direction.text", windDirectionStr);
		result.put("wind.chill", windChill);
		
		logger.trace("Low Battery:         {}", lowBattery);
		logger.trace("Wind chill:          {}", windChill);
		logger.trace("Wind Gust:           {} m/s", windGust);
		logger.trace("Wind Average:        {} m/s", windAverage);
		logger.trace("Wind direction:      {} deg", windDirection);
		logger.trace("Wind direction text: {} deg", windDirectionStr);
		
		return result;
	}
	
	private static Map<String, Object> parseBarometer(byte[] data) {
		
		Map<String, Object> result = new HashMap<String, Object>();
		
		if(!isValid(data, 8)) {
			logger.debug("Invalid barometer data ...");
			return result;
		}
		
		// absolute pressure (mb)
		int pressureAbsolute = 256 * (toInt(data[3]) % 16) + toInt(data[2]);
		
		// relative pressure (mb)
		int pressureRelative = 256 * (toInt(data[5]) % 16) + toInt(data[4]);
		
		// forecast weather
		int weatherForecast = toInt(data[3]) / 16;
		
		// get previous weather
		int weatherPrevious = toInt(data[5]) / 16;
		
		result.put("barometer.absolute", pressureAbsolute);
		result.put("barometer.relative", pressureRelative);
		result.put("weather.forecast", weatherForecast);
		result.put("weather.previous", weatherPrevious);
		
		logger.trace("Pressure Abs.:    {} mb", pressureAbsolute);
		logger.trace("Pressure Rel.:    {} mb", pressureRelative);
		logger.trace("Weather Forecast: {}", weatherForecast);
		logger.trace("Weather Previous: {}", weatherPrevious);
		
		return result;
	}
	
	private static Map<String, Object> parseRainfall(byte[] data) {
		
		Map<String, Object> result = new HashMap<String, Object>();
		if(!isValid(data, 17)) {
			logger.debug("Invalid rain sensor data ...");
			return result;
		}
		
		boolean lowBattery = toInt(data[0]) / 64 == 0 ? false : true;
		
		// rainfall rate (mm/h)
		float rainRate = convertIn2Mm(256f * toInt(data[3]) + toInt(data[2]));
		
		// recent (mm)
		float rainRecent = convertIn2Mm(256f * toInt(data[5]) + toInt(data[4]));
		
		// rainfall for day (mm)
		float rainDay = convertIn2Mm(256f * toInt(data[7]) + toInt(data[6]));
		
		//rainfall since reset (mm)
		float rainReset = convertIn2Mm(256f * toInt(data[9]) + toInt(data[8]));
		
		result.put("rain.lowbatt", lowBattery);
		result.put("rain.rate", rainRate);
		result.put("rain.recent", rainRecent);
		result.put("rain.day", rainDay);
		result.put("rain.reset", rainReset);
		
		logger.trace("Low Battery:      {}", lowBattery);
		logger.trace("Rain rate:        {} mm/h", rainRate);
		logger.trace("Rain recent:      {} mm", rainRecent);
		logger.trace("Rain day:         {} mm/day", rainDay);
		logger.trace("Rain since reset: {} mm/reset", rainReset);
		
		return result;
	}
	
	private static Map<String, Object> parseThermometer(byte[] data) {
		
		Map<String, Object> result = new HashMap<String, Object>();
		
		if(!isValid(data, 12)) {
			logger.debug("Invalid thermometer data ...");
			return result;
		}
		
		boolean lowBattery = toInt(data[0]) / 64 == 0 ? false : true;
		
		// sensor id
		int sensor = toInt(data[2]) % 16;		
		
		// temperature (°C)
		float temperature = (256f * (toInt(data[4]) % 16) + toInt(data[3])) / 10f;
		if(isNegative(toInt(data[4]) / 16)) {
			temperature *= -1;
		}
		
		// humidity (%)
		int humidity = toInt(data[5]) % 100;
		
		// dewpoint (°C)
		float dewpoint = (256f * (toInt(data[7]) % 16) + toInt(data[6])) / 10f;
		if(isNegative(toInt(data[7]) / 16)) {
			dewpoint *= -1;
		}
		
		String no = "";
		if(sensor == 0) {
			no = "indoor";
		} else if(sensor == 1) {
			no = "outdoor";
		} else if(sensor == 2) {
			no = "outdoor2";
		} else if(sensor == 4) {
			no = "outdoor3";
		}
		
		result.put("temp." + no + ".lowbatt", lowBattery);
		result.put("temp." + no + ".temperature", temperature);
		result.put("temp." + no + ".humidity", humidity);
		result.put("temp." + no + ".dewpoint", dewpoint);
		
		logger.trace("Low Battery: {}", lowBattery);
		logger.trace("Sensor:      {}", sensor);
		logger.trace("Temperature: {} °C", temperature);
		logger.trace("Humidity:    {} %", humidity);
		logger.trace("Dew Point:   {} °C", dewpoint);
		
		return result;
	}
	
	private static int toInt(byte value) {
		return ((int) value & 0xFF);
	}
}
