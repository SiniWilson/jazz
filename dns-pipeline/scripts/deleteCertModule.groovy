#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder

/*
* deleteCertModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Function to delete Certificate
*/
def deleteCertificate() {
    println "deleteCertModule.groovy: deleteCertificate"

    def env = System.getenv()
    
    def utilModule = new UtilityModule()
    utilModule.showDnsEnvParams()

    def route53Module = new AWSRoute53Module()
    def serviceMetadataModule = new ServiceMetadataLoader()
    def eventsModule = new EventsModule()
    def acmModule = new AWSAcmModule()
    def awsAccountCreds = null
    def context_map
    def eventName
    def props
    def AWS_KEY
    def AWS_SECRET
    def AWS_REGION
    long MINUTES_TO_MILLISECONDS
    long NO_OF_RETRIES

    try {
        AWS_KEY = env['AWS_302890901340_ACCESS_KEY']
        AWS_SECRET = env['AWS_302890901340_SECRET_KEY']
        AWS_REGION = env['AWS_DEFAULT_REGION']

        props = JSON.getAllProperties() 
        MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        NO_OF_RETRIES = props["NO_OF_RETRIES"]

        def accountDetails = props["accountDetails"]
        def regionValue = props["deploymentRegion"]
        awsAccountCreds = generateCreds(accountDetails, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
        JSON.setValueToPropertiesFile("awsAccountCreds", awsAccountCreds)
    } catch (ex) {
        println "Something went wrong while configuring aws account " + ex
        throw new Exception("Something went wrong while configuring aws account ", ex)
    }
    
    try {
        def Configs = props["configData"]
        def endpointConfType = props["endpointConfType"]
        def currentCertArn = null
        def isPublicEndpoint
        def certRegion = props["certRegion"]
        context_map = props["context_map"]
        def currentFqdn = props["currentFqdn"]
        def region = props["deploymentRegion"]
        def provider
        def providerId
        def getAssetList = serviceMetadataModule.loadAssetInfo('certificate', props["environmentLogicalId"])
        def getCustomAsset = serviceMetadataModule.loadAssetInfo('custom_domain', props["environmentLogicalId"])

        for (eachAsset in getAssetList) {
            println "eachAsset: $eachAsset"
            if (eachAsset.status == 'active' && eachAsset.metadata && eachAsset.metadata != null && eachAsset.metadata.fqdn == currentFqdn) {
                currentCertArn = eachAsset.provider_id
                provider = eachAsset.provider
                isPublicEndpoint = eachAsset.metadata.isPublicEndpoint
            }
        }

        for (eachCustomAsset in getCustomAsset) {
            println "eachCustomAsset: $eachCustomAsset"
            if (eachCustomAsset.status == 'active' && eachCustomAsset.metadata && eachCustomAsset.metadata != null && eachCustomAsset.metadata.fqdn == currentFqdn) {
                provider = eachCustomAsset.provider
                providerId = eachCustomAsset.provider_id

            }
        }

        if (providerId != '') {
            archiveAsset(provider, providerId, 'custom_domain')
        }

        if (currentCertArn != '') {
            archiveAsset(provider, currentCertArn, 'certificate')
        }
        
        eventName = "DETACH_CERTIFICATE"
        if (props['endpointType'] == 'api') {
            eventsModule.sendStartedEvent(eventName, 'started to detach certificate', context_map)
            def endpointConfig = getEndpointConfigType(props["apiId"], region, awsAccountCreds)
            if (endpointConfig == 'REGIONAL') {
                certRegion = region
            }

            sh("aws apigateway delete-domain-name --domain-name ${currentFqdn} --region ${region} --profile ${awsAccountCreds}")
            eventsModule.sendCompletedEvent(eventName, 'certificate is detached from endpoint', context_map)
        }

        /*
        * delete certificate
        */
        eventName = "DELETE_CERTIFICATE"
        println "currentCertArn: $currentCertArn"
        eventsModule.sendStartedEvent(eventName, 'started to delete certificate', context_map)
        if (currentCertArn != null) {
            def certRecordDetails = acmModule.getCertRecordDetails(currentCertArn, certRegion, awsAccountCreds)
            println "certRecordDetails is ${certRecordDetails}"

            /*
            * Delete certificate record entries from Route53
            */
            def credsId = null
            def requestStatus = null
            def zoneId
            if(isPublicEndpoint == true){
                zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE
            } else {
                zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PRIVATEZONE
            }
            try {
                /*
                * Assuming temporary role to access route53 services
                */
                credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
                credsId = route53Module.assumeTempRole(credsId, AWS_REGION)
                def recordResponse = deleteCertificateRecord(certRecordDetails.name, certRecordDetails.value, zoneId, credsId)
                println "recordResponse: $recordResponse"
                
                def count = 0;
                while(requestStatus == null) {
                    if(count > NO_OF_RETRIES) {
                        throw new Exception("Error while deleting certificate record")
                    } else if (count > 0) {
                        sleep(MINUTES_TO_MILLISECONDS)
                    }
                    count++
                    def requestDetails = route53Module.getRequestStatus(recordResponse.ChangeInfo.Id, credsId)
                    println "requestDetails: $requestDetails"
                    if(requestDetails.ChangeInfo.Status == 'INSYNC'){
                        requestStatus = "APPROVED"
                    }
                }
            } catch(ex) {
                println "Something went wrong deleting certificate record " + ex
                throw new Exception("Something went wrong deleting certificate record ", ex)
            } finally {
                utilModule.resetAWSProfile(credsId)
            }

            Number noOfRetries
            Number timeout
            noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.CERT_SETTINGS.NO_OF_RETRIES).toLong()
            timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.CERT_SETTINGS.TIMEOUT_IN_MINS).toLong()

            // Delete certificate
            def certDel = null
            count = 0
            while(certDel == null) {
                if(count > noOfRetries) {
                    println "error occourred while deleting certificate"
                    throw new Exception("error occourred while deleting certificate")
                } else if (count > 0){
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    sleep(sleepTime)
                }
                count++
                def deletionResult = acmModule.deleteCertificate(currentCertArn, certRegion, awsAccountCreds)
                println "deletionResult: $deletionResult"
                if(deletionResult == "success") {
                    certDel = "success"
                }
            }
            
        } else {
            println "Certificate details were not available"
        }
        
        eventsModule.sendCompletedEvent(eventName, 'certificate is deleted from endpoint', context_map)
    } catch(ex) {
        println "ex is " + ex
        eventsModule.sendFailureEvent(eventName, 'Failed while deleting certificate ', context_map)
        eventsModule.sendFailureEvent('CREATE_DNS_REQUEST', 'Failed while deleting certificate ', context_map)
        println "Something went wrong while deleting certificate: " + ex
        throw new Exception("Something went wrong while deleting certificate: ", ex)
    } finally {
        utilModule.resetAWSProfile(awsAccountCreds)
    }
}

/*
* Function to delete a certificate record
*/
def deleteCertificateRecord (name, value, zoneId, credsId) {
    println "In deleteCertModule.groovy: deleteCertificateRecord"

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
            responseJSON = route53Module.deleteCertificateRecord(name, value, zoneId, credsId)
            println "responseJSON in parent: ${responseJSON}"
            if(responseJSON === 'Throttling error') {
                responseJSON = ''
            }
        }
        return responseJSON;
    } catch(ex) {
        println "Error while deleting Certificate Record: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }
}

/**
* Archive the assets of a service
* @param serviceType
*/
def archiveAsset(provider, providerId, type) {
    println "In deleteCertModule.groovy: archiveAsset"
    
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
    println "In deleteCertModule.groovy: generateAssetMap"

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

/*
* Function to get endpoint Config Type
* @param apiId
* @param region
*/
def getEndpointConfigType(apiId, region, awsAccountCreds) {
    println "deleteCertModule.groovy: getEndpointConfigType"

    try {
        def getApiDetails = sh("aws apigateway get-rest-api --rest-api-id ${apiId} --region ${region} --profile ${awsAccountCreds}")
        def apiDetails = JSON.parseJson(getApiDetails)

        println "APIGateway details: $apiDetails"
        return apiDetails.endpointConfiguration.types[0]
    } catch(ex) {
        println "Something went wrong while getting endpoint Config Type: " + ex
        throw new Exception("Something went wrong while getting endpoint Config Type: ", ex)
    }
}

/*
* Function to generate Creds based on account
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy:generateCreds"

    def utilModule = new UtilityModule();
    def credsId = null;
    def deploymentAccountCreds = null;

    credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
    /*
    * Getting temporary credentials for cross account role if not primary account
    */
    deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, AWS_REGION);
    return deploymentAccountCreds
}