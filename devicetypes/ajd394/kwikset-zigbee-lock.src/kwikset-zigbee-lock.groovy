metadata {
	// Automatically generated. Make future change here.
	definition (name: "Kwikset Zigbee Lock", namespace: "ajd394", author: "Andrew DiPrinzio") {
		capability "Polling"
		capability "Actuator"
		capability "Lock"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Lock Codes"

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0101,0402", outClusters: "0019"
	}

	// UI tile definitions
	tiles {
		standardTile("lock", "device.lock", width: 2, height: 2, canChangeIcon: true) {
			state "locked", label: '${name}', action: "lock.unlock", icon: "st.locks.lock.locked", backgroundColor:"#79b821", nextState:"unlocking"
			state "unlocked", label: '${name}', action: "lock.lock", icon: "st.locks.lock.unlocked", backgroundColor:"#ffa81e", nextState:"locking"
			state "locking", label: '${name}', action: "lock.unlock", icon: "st.locks.lock.locked", backgroundColor:"#79b821", nextState:"unlocking"
			state "unlocking", label: '${name}', action: "lock.lock", icon: "st.locks.lock.unlocked", backgroundColor:"#ffa81e", nextState:"locking"
		}
		standardTile("refresh", "device.lock", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		valueTile("temperature", "device.temperature") {
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
		main(["lock"])
		details(["lock", "refresh", "temperature"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.info description

	Map map = [:]
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	} else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
	log.debug "Parse returned $map"
	def result = map ? createEvent(map) : null


	return result
}

// Commands to device

def lock() {
	sendEvent(name: "lock", value: "locking")
	"st cmd 0x${device.deviceNetworkId} 2 0x${clust.LOCK} 0 {}"
}

def unlock() {
	sendEvent(name: "lock", value: "unlocked")
	"st cmd 0x${device.deviceNetworkId} 2 0x${clust.LOCK} 1 {}"
}

def poll() {
	log.debug "polling"
	return refresh()

}

def refresh() {
	log.debug "sending refresh command"
	//REMOVE debug only
	configure()
	[
		"st rattr 0x${device.deviceNetworkId} 2 0x${clust.LOCK} 0x0000", "delay 200",
		//"st rattr 0x${device.deviceNetworkId} 1 0x${clust.TEMP} 0x0000",
	]
}

def configure() {
	String zigbeeId = swapEndianHex(device.zigbeeId)
	log.debug "Confuguring Reporting, and Bindings."
	def configCmds = [
		//lock
		"zcl global send-me-a-report 0x${clust.LOCK} 0x0000 0x${types.ENUM8} 5 3600 {}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 2", "delay 1500",

		//TEMP
		//"zcl global send-me-a-report 0x${clust.TEMP} 0x0000 0x${types.INT16S} 300 3600 {6400}", "delay 200",
		//"send 0x${device.deviceNetworkId} 1 1", "delay 1500",

		//bind
		"zdo bind 0x${device.deviceNetworkId} 1 2 0x${clust.LOCK} {${device.zigbeeId}} {}", "delay 200",
		//"zdo bind 0x${device.deviceNetworkId} 1 1 0x${clust.TEMP} {${device.zigbeeId}} {}", "delay 200",

	]
	//return configCmds + refresh() // send refresh cmds as part of config
    return configCmds
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	log.debug "Desc Map: $descMap"

	Map resultMap = [:]
	if (descMap.cluster == "0101" && descMap.attrId == "0000") {
		def value = getLockStatus(descMap.value)
		resultMap = [name: "lock", value: value]
	}

	return resultMap
}

def getLockStatus(value) {
	def status = Integer.parseInt(value, 16)
	if(status == 0 || status == 1){
		return "locked"
	} else {
		return "unlocked"
	}

}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def msg = zigbee.parse(description)
	log.debug "msg ${msg}"
	if (shouldProcessMessage(msg)) {
		switch(msg.clusterId) {
			/*case 0x0402:
				// temp is last 2 data values. reverse to swap endian
				String temp = msg.data[-2..-1].reverse().collect { msg.hex1(it) }.join()
				def value = getTemperature(temp)
				resultMap = getTemperatureResult(value)
				break
				*/
			case 0x0101:
				log.debug 'lock unknown data'
				if(msg.command == 0x00){
						if(msg.data[0]== 0x00){
							resultMap = [name: "lock", value: 'locked']
						}
				}
				break
		}
	}
	return resultMap
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	// 0x07 is bind message
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
		cluster.command == 0x0B ||
		cluster.command == 0x07 ||
		(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}

def getTemperature(value) {
	def celsius = Integer.parseInt(value, 16).shortValue() / 100
	if(getTemperatureScale() == "C"){
		return celsius
	} else {
		return celsiusToFahrenheit(celsius) as Integer
	}
}

def uninstalled() {

	log.debug "uninstalled()"

	response("zcl rftd")

}

private Map getTemperatureResult(value) {
	log.debug 'TEMP'
	def linkText = getLinkText(device)
	if (tempOffset) {
		def offset = tempOffset as int
		def v = value as int
		value = v + offset
	}
	def descriptionText = "${linkText} was ${value}°${temperatureScale}"
	return [
		name: 'temperature',
		value: value,
		descriptionText: descriptionText
	]
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}

private getTypes(){
	[
		INT8U: "20",
		INT16U: "21",
		INT8S: "28",
		INT16S: "29",
		ENUM8:"30"
	]
}

private getClust(){
	[
		BASIC:"0000",
		PWRCFG:"0001",
		IDENTIY:"0003",
		GROUPS: "0004",
		SCENES: "0005",
		ALARMS: "0009",
		LOCK: "0101",
		TEMP: "0402"
	]
}
