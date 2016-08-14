metadata {
	definition (name: "Kwikset Zigbee Lock", namespace: "ajd394", author: "Andrew DiPrinzio") {
		capability "Actuator"
		capability "Lock"
		capability "Refresh"
		capability "Lock Codes"

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0101", outClusters: "000A,0019"
	}

	// UI tile definitions
	tiles(scale: 2) {
	multiAttributeTile(name:"toggle", type:"generic", width:6, height:4){
		tileAttribute ("device.lock", key:"PRIMARY_CONTROL") {
			attributeState "locked", label:'locked', action:"lock.unlock", icon:"st.locks.lock.locked", backgroundColor:"#79b821", nextState:"unlocking"
			attributeState "unlocked", label:'unlocked', action:"lock.lock", icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff", nextState:"locking"
			attributeState "unknown", label:"unknown", action:"lock.lock", icon:"st.locks.lock.unknown", backgroundColor:"#ffffff", nextState:"locking"
			attributeState "locking", label:'locking', icon:"st.locks.lock.locked", backgroundColor:"#79b821"
			attributeState "unlocking", label:'unlocking', icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff"
		}
	}
	standardTile("lock", "device.lock", inactiveLabel:false, decoration:"flat", width:2, height:2) {
		state "default", label:'lock', action:"lock.lock", icon:"st.locks.lock.locked", nextState:"locking"
	}
	standardTile("unlock", "device.lock", inactiveLabel:false, decoration:"flat", width:2, height:2) {
		state "default", label:'unlock', action:"lock.unlock", icon:"st.locks.lock.unlocked", nextState:"unlocking"
	}
	standardTile("refresh", "device.refresh", inactiveLabel:false, decoration:"flat", width:2, height:2) {
		state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
	}

	main "toggle"
	details(["toggle", "lock", "unlock", "refresh"])
}
}

// Public methods
def installed() {
    log.trace "installed()"
}

def uninstalled() {
    log.trace "uninstalled()"
		response("zcl rftd") //TODO evaluate necessity
}

def configure() {
	String zigbeeId = swapEndianHex(device.zigbeeId)
	log.debug "Confuguring Reporting, and Bindings."
	def configCmds = [
		//lock
		"zcl global send-me-a-report 0x${clust.LOCK} 0x0000 0x${types.ENUM8} 5 3600 {}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 2", "delay 1500",
		//bind
		"zdo bind 0x${device.deviceNetworkId} 1 2 0x${clust.LOCK} {${device.zigbeeId}} {}", "delay 200",
	]
	//return configCmds + refresh() // send refresh cmds as part of config
    return configCmds
}

def refresh() {
	log.trace "sending refresh command"
	//REMOVE debug only
	//configure()
	[
		"st rattr 0x${device.deviceNetworkId} 2 0x${clust.LOCK} 0x${lock_attr.LOCKSTATE}", "delay 200",
		"st rattr 0x${device.deviceNetworkId} 2 0x${clust.BASIC} 0x0000",
	]
}

def parse(String description) {
    log.trace "parse() --- description: $description"

    Map map = [:]
    if (description?.startsWith('read attr -')) {
        map = parseReportAttributeMessage(description)
    }

    def result = map ? createEvent(map) : null
    log.debug "parse() --- returned: $result"
    return result
}

// Lock capability commands
def lock() {
	sendEvent(name: "lock", value: "locking")
	"st cmd 0x${device.deviceNetworkId} 2 0x${clust.LOCK} ${lock_cmd.LOCK} {}"
}

def unlock() {
	sendEvent(name: "lock", value: "unlocked")
	"st cmd 0x${device.deviceNetworkId} 2 0x${clust.LOCK} ${lock_cmd.UNLOCK} {}"
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	log.debug "Desc Map: $descMap"

	Map resultMap = [:]
	if (descMap.cluster == clust.LOCK && descMap.attrId == lock_attr.LOCKSTATE) {
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
			case 0x0101:
				if(msg.command == 0x00){
						if(msg.data[0]== 0x00){
							resultMap = [name: "lock", value: 'locked']
						}
				}else {
					log.debug 'lock unknown data'
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

//variables
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
		IDENTIY:"0003",
		GROUPS: "0004",
		SCENES: "0005",
		TIME: "000A",
		LOCK: "0101"
	]
}

private getLock_attr(){
	[
		LOCKSTATE:"0000"
	]
}

private getLock_cmd(){
	[
		LOCK: "0",
		UNLOCK: "1",
	]
}
