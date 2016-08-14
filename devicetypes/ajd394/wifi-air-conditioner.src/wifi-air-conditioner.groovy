/**
 *  WiFi Air Conditioner
 *
 *  Copyright 2016 Andrew DiPrinzio
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
 */
preferences {
    input("confIpAddr", "string", title:"Enter IP address",
        required:true, displayDuringSetup:true)
    input("confTcpPort", "number", title:"Enter TCP port",
          required:true, displayDuringSetup:true)
}
 
 /**
 * Attributes: fanMode, temperature, coolingSetpoint 
 *
 */
 
metadata {
	definition (name: "WiFi Air Conditioner", namespace: "ajd394", author: "Andrew DiPrinzio") {
		//Device Tags
        capability "Actuator"
        capability "Sensor"
        
        //Management Capabilities
		capability "Refresh"
        capability "Configuration"
        
        //Device Capabilities
		capability "Temperature Measurement"
		capability "Thermostat Cooling Setpoint"
        
        attribute "fanMode", "enum", ["off","low","med","high"]
        command "toggleFanMode"
        command "setFanMode", ["string"]
        command "incrementSetpoint"
        command "decrementSetpoint"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
		// TODO: define your main and details tiles here
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°', unit:"F",
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
        
		standardTile("fanMode", "device.fanMode", inactiveLabel: false, decoration: "flat") {
        	state "off", label:"Off", action:"toggleFanMode", icon:"st.Appliances.appliances11"
			state "low", label:"Low", action:"toggleFanMode", icon:"st.Appliances.appliances11"
			state "med", label:"Med", action:"toggleFanMode", icon:"st.Appliances.appliances11"
			state "high", label:"High", action:"toggleFanMode", icon:"st.Appliances.appliances11"
		}
        
        valueTile("coolingSetpoint", "device.coolingSetpoint", inactiveLabel: false, decoration: "flat") {
			state "coolingSetpoint", label:'${currentValue}° Set', unit:"F", backgroundColor:"#ffffff"
		}
        
        standardTile("coolLevelUp", "device.coolingSetpoint", canChangeIcon: false, inactiveLabel: false, decoration: "flat") {
            state "coolLevelUp", action:"incrementSetpoint", backgroundColor:"#d04e00", icon:"st.thermostat.thermostat-up"
        }
        standardTile("coolLevelDown", "device.coolingSetpoint", canChangeIcon: false, inactiveLabel: false, decoration: "flat") {
            state "coolLevelDown", action:"decrementSetpoint", backgroundColor: "#1e9cbb", icon:"st.thermostat.thermostat-down"
        }
        
        standardTile("refresh", "device.temperature", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
        main(["temperature"])
        details(["temperature","coolingSetpoint","fanMode","coolLevelUp","coolLevelDown","refresh"])
	}
}

//variables
def getFanModes(){["off","low","med","high"]}
def getDevicePOSTTokens(){["fanMode":"__SL_P_UFN","coolingSetpoint":"__SL_P_USP"]}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing message"
    def msg = parseLanMessage(description)
    
    def result = [];

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
	//log.info status
    if(status == 200){
        if(json.fanMode){
        	def modeInt = getFanModes()[json.fanMode.toInteger()];
            result.add(createEvent(name: "fanMode", value: "$modeInt"))
            log.info "Updating fan mode to: $modeInt"
        }
        if(json.coolingSetpoint){
            result.add(createEvent(name: "coolingSetpoint", value: "$json.coolingSetpoint"))
            log.info "Updating coolingSetpoint to: $json.coolingSetpoint"
        }
        if(json.temperature){
            result.add(createEvent(name: "temperature", value: "$json.temperature"))
            log.info "Updating temperature to: $json.temperature"
        }
   }else if(status == 302){
        log.info "Successful Post " + stripPath(msg.headers.location)
        sendHubCommand(getAttr(stripPath(msg.headers.location)))
    }else{
        log.debug "Error: Bad Message Recieved"
        log.debug msg
    }
    return result
}

//handle mgmnt. commands

def refresh() {
	log.debug "Executing 'refresh'"
    updateDNI()    
    return getAttr("all")
}

def configure(){
	log.debug "Executing 'configure'"
    
    //sendEvent(name: "fanMode", value: "off")
    //sendEvent(name: "coolingSetpoint", value: "75")
    //sendEvent(name: "temperature", value: "75")
    
  	return refresh()
}

//run after prefs
def updated() {
	log.info "$device.displayName updated with settings: ${settings.inspect()}"

    state.dni = createDNI(settings.confIpAddr, settings.confTcpPort)
    state.hostAddress = "${settings.confIpAddr}:${settings.confTcpPort}"
    
    updateDNI()
    //setDefaults()
}

// handle device commands

//setters

def toggleFanMode(){
	log.debug "Executing 'toggleFanMode'"
    
   	def modeOrder = getFanModes()
    def next = (modeOrder.indexOf(device.currentValue("fanMode")) + 1) % 4
    setFanMode(next)
}	

def setFanMode(mode){
	def name = "fanMode"
    return setAttr("$name",mode)
}

def incrementSetpoint(){
	def name = "coolingSetpoint"
	def val = device.currentValue("$name")
	return setAttr("$name",val+1)
}

def decrementSetpoint(){
	def name = "coolingSetpoint";
	def val = device.currentValue("$name")
	return setAttr("$name",val-1)
}

//helper

private def getAttr(attrName){
	def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: formPath(attrName),
        headers: [
            HOST: getHostAddress()
        ]
    )
    return result
}

private def setAttr(name,value){
	//"builds" query string format
    def token = getDevicePOSTTokens()[name]
	def body = "$token=$value"
    def path = formPath(name)
    
	def result = new physicalgraph.device.HubAction(
        method: "POST",
        path: path,
        headers: [
            HOST: getHostAddress(),
            "Content-Type":"application/x-www-form-urlencoded"
        ],
        body: body
    )
    log.debug result

    return result
}

private String formPath(attrName) {
    return "/$attrName" + ".json"
}

private String stripPath(path) {
    return path[1..-6]
}

private String createDNI(ipaddr, port) { 
    log.info "createDNI(${ipaddr}, ${port})"

    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    def hexPort = String.format('%04X', port.toInteger())

    return "${hexIp}:${hexPort}"
}

private updateDNI() { 
	log.trace "UPDATE DNI"
    if (device.deviceNetworkId != state.dni) {
        device.deviceNetworkId = state.dni
    }
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}