#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder


/*
* deleteDnsModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Function to delete DNS Record
*/
def deleteDnsRecord() {
    println "deleteDnsModule.groovy: deleteDnsRecord"

    def env = System.getenv()
    
    def utilModule = new UtilityModule()
    utilModule.showDnsEnvParams()

    def route53Module = new AWSRoute53Module()
    def serviceMetadataModule = new ServiceMetadataLoader()
    def eventsModule = new EventsModule()
    def eventName = 'DELETE_DNS_RECORD'
    def context_map
    
    try {
        def AWS_KEY= env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET= env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION= env['AWS_DEFAULT_REGION']

        def props = JSON.getAllProperties()
        def Configs = props["configData"]
        def endpointConfType = props["endpointConfType"]
        def region = props["deploymentRegion"]
        def currentFqdn = ''
        def isPublicEndpoint
        def endpoint
        def provider
        def providerId
        def serviceConfig = props["serviceConfig"]
        def environmentInfo = props["environmentInfo"]
        context_map = props["context_map"]

        eventsModule.sendStartedEvent(eventName, 'dns record deletion started', context_map)
        def getAssetList = serviceMetadataModule.loadAssetInfo('dns_record', props["environmentLogicalId"])

        for (eachAsset in getAssetList) {
            println "eachAsset: $eachAsset"
            if (eachAsset.status == 'active' && eachAsset.metadata && eachAsset.metadata != null && eachAsset.metadata.fqdn != props["domainName"]) {
                currentFqdn = eachAsset.metadata.fqdn
                provider = eachAsset.provider
                providerId = eachAsset.provider_id
                isPublicEndpoint = eachAsset.metadata.isPublicEndpoint
                endpoint = eachAsset.metadata.endpoint
                endpoint = endpoint.replace('https://', '')
            }
        }
        /*
        * update asset status
        */
        if(isPublicEndpoint == null){
            isPublicEndpoint = environmentInfo['is_public_endpoint']
        }
        if (providerId != '') {
            archiveAsset(provider, providerId, 'dns_record')
        }
        /*
        * delete DNS Record
        */
        JSON.setValueToPropertiesFile("currentFqdn", currentFqdn)
        def credsId = null
        def zoneId
        println "isPublicEndpoint: $isPublicEndpoint"
        if(isPublicEndpoint == true){
            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE
        } else {
            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PRIVATEZONE
        }
        def recordZoneId = route53Module.getHostedZoneId(region, endpointConfType, props['endpointType'])
        println "recordZoneId: $recordZoneId"
        println "endpoint: $endpoint"
        try {
            /*
            * Assuming temporary role to access route53 services
            */
            credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
            credsId = route53Module.assumeTempRole(credsId, AWS_REGION)
            deleteDNSRecord(currentFqdn, zoneId, recordZoneId, endpoint, credsId)

        } catch(ex){
            println "Something went wrong while deleting the record " + ex
            throw new Exception("Something went wrong while deleting the record ", ex)
        } finally {
            utilModule.resetAWSProfile(credsId)
        }
        
        eventsModule.sendCompletedEvent(eventName, 'dns record deletion completed', context_map)
    } catch(ex) {
        eventsModule.sendFailureEvent(eventName, 'Failed while deleting DNS record ', context_map)
        eventsModule.sendFailureEvent('CREATE_DNS_REQUEST', 'Failed while deleting DNS record ', context_map)
        println "Something went wrong while deleting DNS Record details: " + ex
        throw new Exception("Something went wrong while deleting DNS Record details: ", ex)
    }
}

/*
* Function to delete DNS Record
*/
def deleteDNSRecord (currentFqdn, zoneId, recordZoneId, endpoint, credsId) {
    println "In deleteDnsModule.groovy: deleteDNSRecord"

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
            responseJSON = route53Module.deleteDNSRecord(currentFqdn, zoneId, recordZoneId, endpoint, credsId)
            println "responseJSON in parent: ${responseJSON}"
            if(responseJSON === 'Throttling error') {
                responseJSON = ''
            }
        }
    } catch(ex) {
        println "Error while deleting DNS Record: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }
}

/**
* Archive the assets of a service
* @param serviceType
*/
def archiveAsset(provider, providerId, type) {
    println "In deleteDnsModule.groovy: archiveAsset"
    
    def props = JSON.getAllProperties()
    def serviceType = props['endpointType']
    def env = props["environmentLogicalId"]
    def eventsModule = new EventsModule()
    def serviceConfig = JSON.getValueFromPropertiesFile("serviceConfig")
    
    eventsModule.sendCompletedEvent('UPDATE_ASSET', "Environment cleanup for ${env} completed", generateAssetMap(serviceType, provider, providerId, type, serviceConfig['owner']))
}

/*
* Generate asset map
* @param serviceType
* @param provider
* @param providerId
* @param type
* @param createdBy
*/
def generateAssetMap(serviceType, provider, providerId, type, createdBy) {
    println "In deleteDnsModule.groovy: generateAssetMap"

    def serviceCtxMap = [
        status: 'archived',
        service_type: serviceType,
        provider: provider,
        provider_id: providerId,
        type: type,
        created_by: createdBy
    ]
    return serviceCtxMap
}
