#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder

/*
* createDnsModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Function to create DNS
*/
def createDns() {
    println "createDnsModule.groovy: createDns"

    def env = System.getenv()
    
    def utilModule = new UtilityModule()
    utilModule.showDnsEnvParams()

    def eventsModule = new EventsModule()
    def eventName = 'CREATE_DNS_RECORD'
    def context_map

    try {
        def AWS_KEY= env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET= env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION= env['AWS_DEFAULT_REGION']

        def props = JSON.getAllProperties() 

        def serviceConfig = props["serviceConfig"]
        context_map = props["context_map"]
        def isCertificateAvailable = props["isCertificateAvailable"]
        def certificateDetails = props["certificateDetails"]

        eventsModule.sendStartedEvent(eventName, 'dns creation started', context_map)
        def publiZoneRecord = [:]
        def zone = "private"
        def certId

        if(isCertificateAvailable){
            certId = getAssetId(certificateDetails, 'certificate', props["environmentLogicalId"])
        } else {
            certId = getAssetId(certificateDetails.CertificateArn, 'certificate', props["environmentLogicalId"])
        }
        JSON.setValueToPropertiesFile("certId", certId)
        def environmentInfo = props["environmentInfo"]
        if (environmentInfo['is_public_endpoint'] == "true" || environmentInfo['is_public_endpoint'] == true) {
            zone = "public"
            publiZoneRecord = checkAndCreateDNSRecord('public', context_map, props["domainName"], props["dnsName"], serviceConfig, eventName, props["environmentLogicalId"], props['fqdn'])
            println "publiZoneRecord is ${publiZoneRecord}"
        }
        if(publiZoneRecord.size() == 0){
            publiZoneRecord['recordID'] = null
        }
        def recordDetails = checkAndCreateDNSRecord('private', context_map, props["domainName"], props["dnsName"], serviceConfig, eventName, props["environmentLogicalId"], props['fqdn'])
        println "Record details: $recordDetails"

        eventsModule.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap(serviceConfig['type'], 'aws', props["domainName"], 'dns_record', props["username"], props["domainName"], props["dnsName"], zone, certId, recordDetails.recordType, recordDetails.recordID, publiZoneRecord.recordID, environmentInfo['is_public_endpoint'], props["zoneId"]))
        
        def providerId = props["endpoint"]
        if(serviceConfig['type'] == 'website'){
            providerId = 'https://' + props["endpoint"]
        }
        eventsModule.sendCompletedEvent('UPDATE_ASSET', null, generateAssetMapForUpdate('aws', providerId, 'endpoint_url', props["username"], recordDetails.fqdn))
        eventsModule.sendCompletedEvent(eventName, 'dns creation completed', context_map)

        eventsModule.sendCompletedEvent('CREATE_DNS_REQUEST', 'Your custom domain is now ready! ', context_map)

    } catch(ex) {
        eventsModule.sendFailureEvent(eventName, 'Failed while creating DNS record ', context_map)
        eventsModule.sendFailureEvent('CREATE_DNS_REQUEST', 'Failed while creating DNS record ', context_map)
        println "Something went wrong while creating DNS: " + ex
        throw new Exception("Something went wrong while creating DNS: ", ex)
    }
}

/*
* Get asset ID of provided providerId
* @param providerId
* @param assetType
* @param environment
*/
def getAssetId(providerId, assetType, environment) {
    println "createDnsModule.groovy: getAssetId"
    
    try {
        def serviceMetadataModule = new ServiceMetadataLoader()
        def assetsList = serviceMetadataModule.loadAssetInfo(assetType, environment)
        for (asset in assetsList) {
            if (asset.provider_id == providerId) {
                return asset.id
            }
        }
    } catch(ex) {
        println "Something went wrong while getting asset ID: " + ex
        throw new Exception("Something went wrong while getting asset ID: ", ex)
    }
}

/*
* check if dns exist and then create DNS record
*/
def checkAndCreateDNSRecord(dnsEndpointType, context_map, domainName, dnsName, config, eventName, environment, fqdn) {
    println "createDnsModule.groovy: checkAndCreateDNSRecord"

    def route53Module = new AWSRoute53Module()
    def eventsModule = new EventsModule()
    def utilModule = new UtilityModule()
    def serviceMetadataModule = new ServiceMetadataLoader()
    Number noOfRetries
    Number timeout
    def credsId = null
    
    try{
        def props = JSON.getAllProperties() 
        long MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        /*
        * Assuming temporary role to access route53 services
        */
        def env = System.getenv()
        def AWS_KEY= env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET= env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION= env['AWS_DEFAULT_REGION']
        
        def Configs = props["configData"]
        def region = props["deploymentRegion"]
        def endpointConfType = props["endpointConfType"]
        def zoneId
        if(dnsEndpointType == 'public'){
            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE
        } else {
            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PRIVATEZONE
        }
        def recordZoneId = route53Module.getHostedZoneId(region, endpointConfType, props['endpointType'])
        println "recordZoneId: $recordZoneId"
        // aws configure
        /*
        * Assuming temporary role to access route53 services
        */
        credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
        credsId = route53Module.assumeTempRole(credsId, AWS_REGION)

        domainName = domainName + '.'
        // get the endpoint from dns_record, if exist
        def getAssetList = serviceMetadataModule.loadAssetInfo('dns_record', props["environmentLogicalId"])
        println "getAssetList : $getAssetList"
        def endpoint = getAssetList ? getAssetList[0].metadata.endpoint : props['endpoint']
        endpoint = endpoint.replace('https://', '')
        endpoint = endpoint + '.'
        println "endpoint: $endpoint"
        def dnsExist = checkIfRecordExists(domainName, zoneId, credsId, endpoint);
        def recordData
        println "dnsExist is ${dnsExist}"
        if (!dnsExist) {
            def recordResponse = [:]
            def count = 0
            
            if (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName]) {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].TIMEOUT_IN_MINS).toLong()
            } else {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS).toLong()
            }

            while (recordResponse == [:]) {
                if(count > noOfRetries) {
                    context_map['timedOut'] = true
                    throw new Exception("Error while executing createDNSRecord")
                } else if (count > 0) {
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    println "sleepTime: $sleepTime"
                    sleep(sleepTime)
                }
                count++
                try{
                    def fqdnName = "${fqdn}.jazz.t-mobile.com"
                    recordResponse = createARecord(fqdnName, zoneId, recordZoneId, dnsName, credsId);
                    println "recordResponse: $recordResponse"
                } catch(ex){
                    println "exception occured while creating DNS: " + ex
                    recordResponse.error == 'apiError'
                    throw new Exception("exception occured while creating DNS: ", ex)
                }
            }
            println "DNS record create response: $recordResponse"
            
            if (recordResponse != [:]) {
                def eName = "APPROVE_DNS_REQ"
                def requestDetails
                def denied = false
                count = 0

                if (Configs.JAZZ.DNS.RETRY_SETTINGS[eName]) {
                    noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS[eName].NO_OF_RETRIES).toLong()
                    timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS[eName].TIMEOUT_IN_MINS).toLong()
                } else {
                    noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES).toLong()
                    timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS).toLong()
                }

                def requestStatus = null
                while(requestStatus == null) {
                    if(count > noOfRetries) {
                        context_map['timedOut'] = true
                        eventsModule.sendFailureEvent('CREATE_DNS_RECORD', 'DNS approval request is timed out ', context_map)
                        throw new Exception("TimedOut while getting approval")
                    } else if (count > 0) {
                        def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                        println "sleepTime: $sleepTime"
                        sleep(sleepTime)
                    }
                    count++
                    try{
                        requestDetails = route53Module.getRequestStatus(recordResponse.ChangeInfo.Id, credsId)
                        println "requestDetails: $requestDetails"
                        if(requestDetails.ChangeInfo.Status == 'INSYNC'){
                            requestStatus = "APPROVED"
                        }
                    } catch(ex){
                        println "Failed while getting records: " + ex
                        recordData = null
                    }
                }
                
                if(requestStatus != null){
                    recordData = getDetailsOfRecord(domainName, zoneId, credsId);
                    recordData = recordData[0]
                    println "recordData: $recordData"
                    recordData.fqdn = fqdn
                    recordData.recordID = recordResponse.ChangeInfo.Id
                }
                println "DNS record details: $recordData"
                return recordData
            } else {
                context_map['duplicate'] = true
                eventsModule.sendFailureEvent('CREATE_DNS_RECORD', 'Error while creating DNS Record ', context_map)
                println "Error while creating DNS Record"
                throw new Exception("Error while creating DNS Record")
            }
        } else {
            recordData = getDetailsOfRecord(domainName, zoneId, credsId);
            recordData = recordData[0]
            println "recordData: ${recordData}"
            recordData.fqdn = fqdn
            return recordData
        }
    } catch(ex){
        eventsModule.sendFailureEvent('CREATE_DNS_RECORD', 'Error while creating DNS Record ', context_map)
        println "Error while creating DNS Record: " + ex.message
        throw new Exception("Error while creating DNS Record: ", ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Function to get record Details
*/
def getDetailsOfRecord (domainName, zoneId, credsId) {
    println "createDnsModule.groovy: getDetailsOfRecord"

    try {
        def Configs = JSON.getValueFromPropertiesFile("configData")
        def props = JSON.getAllProperties()
        long MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        def responseJSON = '';
        def route53Module = new AWSRoute53Module();

        def retryCount = 0
        def noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES
        def timeout = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS

        while(responseJSON == '') {
            if (retryCount > noOfRetries) {
                throw new Exception("Error while checking Record")
            } else if (retryCount > 0) {
                def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                println "sleepTime: $sleepTime"
                sleep(sleepTime)
            }
            retryCount++
            println "retryCount is now : $retryCount"
            responseJSON = route53Module.getRecordDetails(domainName, zoneId, credsId)
            println "responseJSON in parent: ${responseJSON}"
            if(responseJSON === 'Throttling error') {
                responseJSON = ''
            }
        }
        return responseJSON;

    } catch(ex) {
        println "Error while getting details of Record: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }
}

/*
* Function to create a record
*/
def createARecord (fqdnName, zoneId, recordZoneId, dnsName, credsId) {
    println "createDnsModule.groovy: createARecord"

    try {
        def Configs = JSON.getValueFromPropertiesFile("configData")
        def props = JSON.getAllProperties()
        long MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        def responseJSON = '';
        def route53Module = new AWSRoute53Module();

        def retryCount = 0
        def noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES
        def timeout = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS

        while(responseJSON == '') {
            if (retryCount > noOfRetries) {
                throw new Exception("Error while checking Record")
            } else if (retryCount > 0) {
                def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                println "sleepTime: $sleepTime"
                sleep(sleepTime)
            }
            retryCount++
            println "retryCount is now : $retryCount"
            responseJSON = route53Module.createRecord(fqdnName, zoneId, recordZoneId, dnsName, credsId)
            println "responseJSON in parent: ${responseJSON}"
            if(responseJSON === 'Throttling error') {
                responseJSON = ''
            }
        }
        return responseJSON;

    } catch(ex) {
        println "Error while creating Record: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }
}

/*
* Function to check if a record exists
*/
def checkIfRecordExists (domainName, zoneId, credsId, endpoint) {
    println "createDnsModule.groovy: checkIfRecordExists"

    try {
        def Configs = JSON.getValueFromPropertiesFile("configData")
        def props = JSON.getAllProperties()
        long MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        def responseJSON = '';
        def route53Module = new AWSRoute53Module();

        def retryCount = 0
        def noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES
        def timeout = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS

        while(responseJSON == '') {
            if (retryCount > noOfRetries) {
                throw new Exception("Error while checking Record")
            } else if (retryCount > 0) {
                def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                println "sleepTime: $sleepTime"
                sleep(sleepTime)
            }
            retryCount++
            println "retryCount is now : $retryCount"
            responseJSON = route53Module.checkIfRecordExists(domainName, zoneId, credsId, endpoint)
            println "responseJSON in parent: ${responseJSON}"
            if(responseJSON === 'Throttling error') {
                responseJSON = ''
            }
        }
        return responseJSON;

    } catch(ex) {
        println "Error while checking Record: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }

}

/*
* Function to generate Asset Map
*/
def generateAssetMap(service_type, provider, providerId, type, created_by, fqdn, fqdn_value, zone, cert_id, record_type, privateRecordId, publicRecordId, isPublicEndpoint, hostedZoneId) {
	def serviceCtxMap = [
		service_type: service_type,
		provider: provider,
		provider_id: providerId,
		type: type,
		created_by: created_by,
        metadata: [
            fqdn: fqdn,
            endpoint: fqdn_value,
            zone: zone,
            cert_id: cert_id,
            dns_record_type: record_type,
            privateRecordId: privateRecordId,
            publicRecordId: publicRecordId,
            isPublicEndpoint: isPublicEndpoint,
            hostedZoneId: hostedZoneId
        ]
    ]
    return serviceCtxMap;
}

def generateAssetMapForUpdate(provider, providerId, type, created_by, custom_domain) {

    def request_id = [:]
    def env = System.getenv()
    def requestId= env['REQUEST_ID']
    request_id[requestId] = [customDomain: custom_domain]
    def serviceCtxMap = [
        provider: provider,
        provider_id: providerId,
        type: type,
        created_by: created_by,
        metadata: [
            requests: request_id
        ]
    ]

    return serviceCtxMap;
}