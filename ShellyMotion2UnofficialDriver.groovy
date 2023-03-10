/**
 *   
 *  Shelly Motion 2 Sensor
 *
 *  Copyright © 2018-2019 Scott Grayban
 *  Copyright © 2020 Allterco Robotics US
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Hubitat is the Trademark and intellectual Property of Hubitat Inc.
 * Shelly is the Trademark and Intellectual Property of Allterco Robotics Ltd
 *  
 *-------------------------------------------------------------------------------------------------------------------
 *
 * See all the Shelly Products at https://shelly.cloud/
 *
 *  Changes:
 *  
 *  1.0.0 - Initial code - Unofficial Custom Driver - Code borrowed and modified to support the Shelly Motion 2 Devices
 *              Removed the Check FW and Upgrade features. /Corey
 *  1.0.1 - Added Presence for HealthStatus of Sensor
 *
 */

/**        
* MOTION 2 WEB ACTION TRIGGERS
*   MOTION DETECTED IN DARK
*   MOTION DETECTED IN TWILIGHT
*   MOTION DETECTED IN BRIGHT
*   END OF MOTION DETECTED
*   TAMPER ALARM DETECTED
*   END OF TAMPER ALARM
*   OVER TEMPERATURE
*   UNDER TEMPERATURE
*   DARK CONDITION
*   TWILIGHT CONDITION
*   BRIGHT CONDITION
*/

import groovy.json.*
import groovy.transform.Field

def setVersion(){
	state.Version = "1.0.0"
	state.InternalName = "ShellyMotion2UnofficialDriver"
}

metadata {
	definition (
		name: "Shelly Motion 2 Sensor",
		namespace: "ShellyUSA-Custom",
		author: "Scott Grayban / Corey J Cleric"
		)
	{
        capability "Refresh"
        capability "Polling"
        capability "SignalStrength"
        capability "Initialize"
        capability "MotionSensor"
        capability "PresenceSensor"
        capability "Battery"
        capability "TamperAlert"
        capability "TemperatureMeasurement"
        capability "IlluminanceMeasurement"

        command "RebootDevice"
         
        attribute "WiFiSignal", "string"
        attribute "illuminancename", "string"
	}

	preferences {

	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
    input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
}

def initialize() {
	log.info "Shelly Motion 2 IP ${ip} initialized."
    state.motioncounter = 0
    state.tampercounter = 0
    state.motion = false
    state.tamper = false
    state.motiontimestamp = 0
    state.temperature = 0
    state.tempunit = ""
    state.update = false
    state.battery = 0
    state.illuminance = ""
    state.illuminancename = ""
    sendEvent(name: "presence", value: "unknown")
    unschedule()
    runIn(1,getSettings)
    runEvery30Minutes(setUpdate)
    runEvery1Minute(getMotionStatus)
}


def setUpdate() { state.update = true }

def installed() {
    log.debug "Shelly Motion 2 IP ${ip} installed."
    state.DeviceName = "NotSet"
}

def uninstalled() {
    unschedule()
    log.debug "Shelly Motion 2 IP ${ip} uninstalled."
}

def updated() {
    if (txtEnable) log.info "Shelly Motion 2 IP ${ip} preferences updated."
    log.warn "Shelly Motion 2 IP ${ip} debug logging is: ${debugOutput == true}"
    //unschedule()
    dbCleanUp()
    if (debugOutput) runIn(1800,logsOff) //Off in 30 minutes
    if (debugParse) runIn(300,logsOff) //Off in 5 minutes
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    refresh()
}

private dbCleanUp() { state.clear() }

def refresh(){
    logDebug "Shelly Motion 2 IP ${ip} refresh."
    getMotionStatus()
}

def getMotionStatus() {
    def params = [uri: "http://${username}:${password}@${ip}/status"]
        
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Shelly Motion 2 IP ${ip} response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "Shelly Motion 2 IP ${ip} params: ${params}"
        logJSON "Shelly Motion 2 IP ${ip} response contentType: ${resp.contentType}"
	    logJSON "Shelly Motion 2 IP ${ip} response data: ${resp.data}"

        if (getDataValue("presence") != "present") sendEvent(name: "presence", value: "present")      
      
        //log.info "Shelly Motion 2 IP ${ip} ${state.motiontimestamp} ${obs.sensor.timestamp} ${state.motion} ${obs.sensor.motion} "
        
        if (state.motiontimestamp != obs.sensor.timestamp && state.motion != obs.sensor.motion && obs.sensor.motion == true) {
            state.motiontimestamp = obs.sensor.timestamp
            state.motion = obs.sensor.motion
            sendEvent(name: "motion", value: "active") 
            state.motioncounter++ 
            sendEvent(name: "motioncounter", value: state.motioncounter)
            }
        else if (state.motion != obs.sensor.motion && obs.sensor.motion == false) {
            sendEvent(name: "motion", value: "inactive")
            state.motion = obs.sensor.motion
            }
        if ( state.tamper != obs.sensor.vibration && obs.sensor.vibration == true ) { 
               sendEvent(name: "tamper", value: "detected")
               state.tampercounter++ 
               sendEvent(name: "tampercounter", value: state.tampercounter)
            } else if (obs.sensor.vibration == false) {
               sendEvent(name: "tamper", value: "clear")
               state.tamper = obs.sensor.vibration
            }
            
        
        if (state.illuminance != obs.lux.value || state.update) {
            state.illuminance = obs.lux.value
            sendEvent(name: "illuminance", value: state.illuminance, unit: "lx")    
            }
        if (state.illuminancename != obs.lux.illumination) {
            state.illuminancename = obs.lux.illumination
            sendEvent(name: "illuminancename", value: state.illuminancename)
            }
        mytemp = (obs.tmp.value.toFloat() * (9/5) + 32)
        mytemp = String.format("%.1f", mytemp).toFloat()
        // mytemp = celsiusToFahrenheit(((Math.round(obs.tmp.value.toFloat()) * 10) / 10))
        if (state.temperature != mytemp || state.update) {
            //state.temperature = celsiusToFahrenheit(mytemp)
            //state.tempunit = obs.tmp.units
            state.temperature = mytemp
            state.tempunit = "F"
            sendEvent(name: "temperature", value: state.temperature, unit: "°${state.tempunit}")
            }
        if (state.battery != obs.bat.value  || state.update) {
            state.battery = obs.bat.value
            sendEvent(name: "battery", value: state.battery, unit: "%")
            }
        state.update = false
       
/*
-30 dBm Excellent | -67 dBm     Good | -70 dBm  Poor | -80 dBm  Weak | -90 dBm  Dead
*/

        if (signal <= 0 && signal >= -70) {
            sendEvent(name:  "WiFiSignal", value: "<font color='green'>Excellent</font>", isStateChange: true);
        } else
        if (signal < -70 && signal >= -80) {
            sendEvent(name:  "WiFiSignal", value: "<font color='green'>Good</font>", isStateChange: true);
        } else
        if (signal < -80 && signal >= -90) {
            sendEvent(name: "WiFiSignal", value: "<font color='yellow'>Poor</font>", isStateChange: true);
        } else 
        if (signal < -90 && signal >= -100) {
            sendEvent(name: "WiFiSignal", value: "<font color='red'>Weak</font>", isStateChange: true);
        }
        state.rssi = obs.wifi_sta.rssi
        sendEvent(name: "rssi", value: state.rssi)

} // End try
       } catch (e) {
           if (getDataValue("presence") != "not present") sendEvent(name: "presence", value: "not present")      
           log.error "Shelly Motion 2 IP ${ip} something went wrong: $e"
       }
    
} // End getMotionStatus

    
def getSettings(){

    logDebug "Shelly Motion 2 IP ${ip} get settings called"
    //getSettings()
    def params = [uri: "http://${username}:${password}@${ip}/settings"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Shelly Motion 2 IP ${ip} response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "Shelly Motion 2 IP ${ip} params: ${params}"
        logJSON "Shelly Motion 2 IP ${ip} response contentType: ${resp.contentType}"
	    logJSON "Shelly Motion 2 IP ${ip} response data: ${resp.data}"
        
        if (getDataValue("presence") != "present") sendEvent(name: "presence", value: "present") 
        
        state.sensitivity = obs.motion.sensitivity
        state.blindtimeminutes = obs.motion.blind_time_minutes
        state.pulsecount = obs.motion.pulsecount
        state.operatingmode = obs.motion.operatingmode
        state.enabled = obs.motion.enabled

        state.ledstatusdisabled = obs.led_status_disabled
        state.tampersensitivity = obs.tamper_sensitivity
        state.darkthreshold = obs.dark_threshold
        state.twilightthreshold = obs.twilight_threshold
        
        updateDataValue("Device Name", obs.name)
        updateDataValue("FW Version", obs.fw)
        updateDataValue("Device Type", obs.device.type)
        updateDataValue("Hostname", obs.device.hostname)
        updateDataValue("MAC", obs.device.mac)
        updateDataValue("SSID", obs.wifi_sta.ssid)
        updateDataValue("Timezone", obs.timezone)
        //updateDataValue("Daylight Savings", obs.tz_dst)
       
        
    } // End try
       } catch (e) {
        if (getDataValue("presence") != "not present") sendEvent(name: "presence", value: "not present")     
        log.error "Shelly Motion 2 IP ${ip} something went wrong: $e"
       }
    
} // End Device Info


// Parse incoming device messages to generate events
def parse(String description) {
    log.info "Shelly Motion 2 IP ${ip} recieved callback message."
    getMotionStatus()
}


def ping() {
	logDebug "Shelly Motion 2 IP ${ip} recieved ping."
	poll()
}

def logsOff(){
	log.warn "Shelly Motion 2 IP ${ip} debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

private logJSON(msg) {
	if (settings?.debugParse || settings?.debugParse == null) {
	log.info "Shelly Motion 2 IP ${ip} $msg"
	}
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "Shelly Motion 2 IP ${ip} $msg"
	}
}

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	logDebug "Shelly Motion 2 IP ${ip} Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Shelly Motion 2 IP ${ip} Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Shelly Motion 2 IP ${ip} executing 'poll'" //RK
	refresh()
}


def RebootDevice() {
    if (txtEnable) log.info "Shelly Motion 2 IP ${ip} rebooting device"
    def params = [uri: "http://${username}:${password}@${ip}/reboot"]
    try {
        httpGet(params) {
            resp -> resp.headers.each {
            logDebug "Shelly Motion 2 IP ${ip} response: ${it.name} : ${it.value}"
        }
    } // End try
    } catch (e) { log.error "Shelly Motion 2 IP ${ip} something went wrong: $e" }
}

