#!groovy?

import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*


static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Get Service Details by ID
*/
def getServiceDetails(serviceId = null) {
    println "In ServiceMetadataLoader.groovy:getServiceDetails"
    try {
        def env = System.getenv()
        serviceId = serviceId ?: env.SERVICE_ID
        JSON.setValueToPropertiesFile('serviceId', serviceId) 
        def authToken = JSON.getValueFromPropertiesFile('authToken')
        def apiUrl = "${env.API_BASE_URL}/services/${serviceId}"
        def resp = sh ("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' '$apiUrl' ", true)
        
        if(resp) {
            def serviceDataObj = JSON.parseJson(resp)
            if(serviceDataObj && serviceDataObj.data && serviceDataObj.data.data) {
                def serviceConfigObj = serviceDataObj.data.data                
                def serviceConfig = [:]
                JSON.setValueToPropertiesFile('serviceMetadata',  serviceConfigObj.metadata)
                serviceConfig << serviceConfigObj.metadata
                serviceConfigObj.remove('metadata')
                serviceConfig << serviceConfigObj
                JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)
                def categoryList = ['manage', 'code', 'deploy']
                if (!serviceConfig.scmManaged) {
                    categoryList = ['manage', 'deploy']
                }
                
                JSON.setValueToPropertiesFile('categoryList', categoryList)
                } else {
                println "No service details defined in the service catalog."
                throw new Exception("No service details defined in the service catalog.")
            }
        } else {
            println "No service details defined in the service catalog."
            throw new Exception("No service details defined in the service catalog.")
        }	
    } catch(ex){
        println "error occured while fetching service metadata: " + ex.message
        throw new Exception("error occured while fetching service metadata", ex)
    }
}

/**
 * Load the service metadata from Catalog
 *
 */
def loadServiceMetaData() {
    println "In ServiceMetadataLoader.groovy:loadServiceMetaData"
    try {
        def apiBaseUrl = JSON.getValueFromPropertiesFile('apiBaseUrl')
        def authToken = JSON.getValueFromPropertiesFile('authToken')
        def serviceName = JSON.getValueFromPropertiesFile('serviceName')
        def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
        def apiUrl = "$apiBaseUrl/services?domain=${serviceDomain}&service=${serviceName}"
        def serviceData = sh ("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' '$apiUrl' ", true)
        if(serviceData) {
            def serviceDataObj = JSON.parseJson(serviceData)
            if(serviceDataObj && serviceDataObj.data && serviceDataObj.data.services) {
                def servicesArr = serviceDataObj.data.services
                servicesArr.each { serviceConfigObj ->
                    if (serviceConfigObj.service == serviceName && serviceConfigObj.domain == serviceDomain ) {
                        def serviceConfig = [:]
                        serviceConfig << serviceConfigObj.metadata
                        serviceConfigObj.remove('metadata'  )
                        serviceConfig << serviceConfigObj
                        JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)
                        def categoryList = ['manage', 'code', 'deploy']
                        if (!serviceConfig.scmManaged) {
                            categoryList = ['manage', 'deploy']
                        }
                        JSON.setValueToPropertiesFile('categoryList', categoryList)
                        JSON.setValueToPropertiesFile('serviceId', serviceConfig.id)
                    }
                }
                if(!JSON.getValueFromPropertiesFile('serviceId')) {
                    throw new Exception("Could not fetch service metadata")
                }
            } else {
                println "No service details defined in the service catalog."
                throw new Exception("No service details defined in the service catalog.")
            }
        } else {
            println "No service details defined in the service catalog."
            throw new Exception("No service details defined in the service catalog.")
        }
    }
    catch(e){
        println "error occured while fetching service metadata: " + e.getMessage()
        throw new Exception("error occured while fetching service metadata", e)
    }
}


def getServiceIdByUsingRepositoryUrl() {
    def env = System.getenv()
    try {
        def configData = JSON.getValueFromPropertiesFile('configData')        
        def utilModule = new UtilityModule()
        def credsId = utilModule.configureAWSProfile(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)
        def serviceRepoUrl = env.REPO_URL
        serviceRepoUrl = serviceRepoUrl.toLowerCase()
        def tableName = "${configData.INSTANCE_PREFIX}_services"
        def indexName = "ServicesRepositoryIndex"
        def serviceObj = sh("aws --region ${configData.AWS.DEFAULTS.REGION} dynamodb query --table-name $tableName --index-name $indexName --key-condition-expression 'SERVICE_REPOSITORY = :serviceRepositoryUrl' --expression-attribute-values '{\":serviceRepositoryUrl\": {\"S\":\"$serviceRepoUrl\"}}' --profile $credsId --output json")
        if(serviceObj) {
            def serviceDetails = JSON.parseJson(serviceObj)
            if(serviceDetails && serviceDetails.Items && serviceDetails.Items.size() > 0){
                def serviceId = serviceDetails.Items[0].SERVICE_ID.S
                return serviceId
            } else {
                println "No service is defined in the service catalog by using the repository url ${env.REPO_URL}."
                throw new Exception("No service is defined in the service catalog by using the repository url ${env.REPO_URL}.")
            }
        } else {
            println "No service is defined in the service catalog by using the repository url ${env.REPO_URL}."
            throw new Exception("No service is defined in the service catalog by using the repository url ${env.REPO_URL}.")
        }
    } catch (ex) {
        println "Exception occured while fetching the service id from the service catalog by using the repository url ${env.REPO_URL}.${ex.message}"
        throw new Exception("Exception occured while fetching the service id from the service catalog by using the repository url ${env.REPO_URL}.", ex)
    }    
}

def getServiceDetailsByServiceRepoUrl() {
    try {
        def serviceId = getServiceIdByUsingRepositoryUrl ()
        if (serviceId) getServiceDetails(serviceId)
        else {
            println "Unable to find the service id ${ex.message}"
            throw new Exception("Unable to find the service id.")
        }
    } catch (ex) {
        println "Unable to find the service id.${ex.message}"
        throw new Exception("Unable to find the service id.", ex)
    }    
}
 
/**
 * Loads the s3 asset details if the service is 'website'
 *
 */
def getS3BucketNameForService(credsId) {
    println "In ServiceMetadataLoader.groovy:getS3BucketNameForService"
    def s3BucketName = null
    try {
        def utilModule = new UtilityModule()
        def assets = loadAssetInfo("s3", null)        
        for (asset in assets) {
            if(asset.provider_id){
                def providerId = asset.provider_id                       
                // Fix for backward compatibility: Newer S3 assets will have ARNs
                def bucketName = providerId.replaceAll("arn:aws:s3:::","");
                // Fix for backward compatibility: Older S3 assets will use this format - s3://
                bucketName = bucketName.replaceAll("s3://","")
                bucketName = bucketName.replaceAll("/.*\$","") 
                def status = utilModule.isS3BucketAccessibleByTheCurrentAccountCredential(bucketName, credsId) 
                println "status-------returned: $status"
                if (status) {
                    println "Bucket exists- ${bucketName}. Checking it has access with current creds"
                    def isBucketExists = utilModule.checkIfBucketExists(bucketName, credsId)
                    if (isBucketExists) {
                        s3BucketName = bucketName
                        println "Able to access the bucket- ${s3BucketName} - with current creds"
                        break; 
                    }                    
                }         
            } 
        }
    } catch (ex) {
        println "Exception occured in getS3BucketNameForService: ${ex.message}"
        throw new Exception("Exception occured in getS3BucketNameForService", ex)
    }
    
    return s3BucketName    
}

 
/**
 * Load the assets metadata from assets catalog given a asset type
 * @param type - Asset types (s3, lambda, apigatway etc.)
 */

def loadAssetInfo(type, environment) {
    println "In ServiceMetadataLoader.groovy:loadAssetInfo"
    def Configs = JSON.getValueFromPropertiesFile('configData')
    def authToken = JSON.getValueFromPropertiesFile('authToken')
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig');
    def serviceName = serviceConfig.service
    def serviceDomain = serviceConfig.domain
    def serviceId = JSON.getValueFromPropertiesFile('serviceId')
    def assetInfoResult = []
    failIt = false
    // TODO: Get all pages. Highly likely that a service won't have more than 100 assets of a specific asset type.
    def url = "https://${Configs.AWS.API.HOST_NAMES.PROD}/api/jazz/assets?limit=100&offset=0&service=" + serviceName;

    if(serviceDomain) {
        url += "&domain=" + serviceDomain
    }
    if(environment) {
        url += "&environment=" + environment
    }
    if(type) {
        url += "&type=" + type
    }
    println "Assets query url: " + url
    try {
        def assetInfo = sh ("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: $serviceId' '$url' ", true)
        println "assetInfo: $assetInfo"
        if(assetInfo) {
            def assetInfoObj = JSON.parseJson(assetInfo)
            println "Asset: $assetInfoObj"	
            if(verifyApiStatus(assetInfoObj)) {
                failIt = true
                throw new Exception("Exception occured while fetching asset metadata") 
            }
            if(assetInfoObj && assetInfoObj.data && assetInfoObj.data.assets) {
                assetInfoResult = assetInfoObj.data.assets
            }
        }
        JSON.setValueToPropertiesFile('serviceAssetConfig', assetInfoResult)
        return assetInfoResult
    } catch(e){
        println "error occured while fetching asset metadata: " + e.getMessage()
        JSON.setValueToPropertiesFile('serviceAssetConfig', [])
        if(failIt) throw new Exception("Exception occured while fetching asset metadata") 
        else {
            return []
        }
    }
}

def verifyApiStatus(assetInfoObj) {
    println "In ServiceMetadataLoader.groovy:verifyApiStatus"
    def servererror = false
    if(assetInfoObj.containsKey("message")) {
        if(assetInfoObj.message == "Internal Server Error") {
            servererror = true
        }
    }
    if(assetInfoObj.containsKey("errorType")) {
        if(assetInfoObj.errorType == "InternalServerError") {
            servererror = true
        }
    }
    return servererror
}

/**
* Update the asset status of provided assetId
*/
def updateAssetStatus(assetId, status, serviceId) {
    println "In ServiceMetadataLoader.groovy:updateAssetStatus"
    def Configs = JSON.getValueFromPropertiesFile('configData')
    def url = "https://${Configs.AWS.API.HOST_NAMES.PROD}/api/jazz/assets/${assetId}"
    def body = JSON.objectToJsonString([status: status])
    def authToken = JSON.getValueFromPropertiesFile('authToken')

    def assetRes = sh ("curl  -X PUT  -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: ${serviceId}' -d '${body}' '$url' ", true)

    if (assetRes) {
        def responseJSON = JSON.parseJson(assetRes)
        println "Asset response after updating the status: $responseJSON"
    } else {
        println "couldn't update the asset status"
    }
}

/**
 * Get the provider Id of a given service, asset type and environment
 *
 */
def getAssetProviderId(type, environment) {
    println "In ServiceMetadataLoader.groovy:getAssetProviderId"
    def providerId = null
    try {
        def data = loadAssetInfo(type, environment)
        if(data && data[0] && data[0].provider_id) {
            providerId = data[0].provider_id
        }
        println "$type: $providerId"
        JSON.setValueToPropertiesFile('assetProviderId', providerId)
        return providerId
    } catch (ex) {
        println "error while getting provider id." + ex.message
        throw new Exception("error while getting provider id", ex)
    }
}

/**
 * Set Service ID
 * @return      
 */
def setServiceId(serviceId) {
    println "In ServiceMetadataLoader.groovy:setServiceId"
    JSON.setValueToPropertiesFile('serviceId', serviceId)
}

/**
 * Set Service Type
 * @return      
 */
def setServiceType(serviceType) {
    println "In ServiceMetadataLoader.groovy:setServiceType"
    JSON.setValueToPropertiesFile('serviceType', serviceType)
}

/**
 * Set Domain
 * @return      
 */
def setDomain(domain) {
    println "In ServiceMetadataLoader.groovy:setDomain"
    JSON.setValueToPropertiesFile('serviceDomain', domain)
}

/**
 * Set Service
 * @return      
 */
def setService(service) {
    println "In ServiceMetadataLoader.groovy:setService"
    JSON.setValueToPropertiesFile('serviceName', service)
}

/**
 * Get API type
 * @return      
 */
def getEndpointType() {
    println "In ServiceMetadataLoader.groovy:getEndpointType"
    if(g_public){
        return 'public'
    } 
    return 'private'
}

/**
 * Get Service Id
 * @return      
 */
def getServiceId() {
    println "In ServiceMetadataLoader.groovy:getServiceId"
    return JSON.getValueFromPropertiesFile('serviceId')
}

/**
 * Get Service Repository
 * @return      
 */
def getServiceRepository() {
    println "In ServiceMetadataLoader.groovy:getServiceRepository"
    return JSON.getValueFromPropertiesFile('serviceConfig').repository
}

/**
 * Get Service Type
 * @return      
 */
def getServiceType() {
    println "In ServiceMetadataLoader.groovy:getServiceType"
    return JSON.getValueFromPropertiesFile('serviceConfig').type
}

def getDeploymentDescriptor() {
    println "In ServiceMetadataLoader.groovy:getDeploymentDescriptor"
    return JSON.getValueFromPropertiesFile('serviceConfig').deployment_descriptor
}

/**
 * Get Domain
 * @return      
 */
def getDomain() {
    println "In ServiceMetadataLoader.groovy:getDomain"
    return JSON.getValueFromPropertiesFile('serviceDomain')
}

/**
 * Get Service
 * @return      
 */
def getService() {
    println "In ServiceMetadataLoader.groovy:getService"
    return JSON.getValueFromPropertiesFile('serviceName')
}

/**
 * Get Owner
 * @return      
 */
def getOwner() {
    println "In ServiceMetadataLoader.groovy:getOwner"
    return JSON.getValueFromPropertiesFile('serviceConfig').created_by
}

/**
 * Get Runtime
 * @return      
 */
def getRuntime() {
    println "In ServiceMetadataLoader.groovy:getRuntime"
    return JSON.getValueFromPropertiesFile('serviceConfig').runtime
}


/**
 * Get Deployment Targets
 * @return      
 */
def getDeploymentTargets() {
    println "In ServiceMetadataLoader.groovy:getDeploymentTargets"
    return JSON.getValueFromPropertiesFile('serviceConfig').deployment_targets
}

/**
 * Get Application Name
 */
def getApplicationName() {
    println "In ServiceMetadataLoader.groovy:getApplicationName"
    return JSON.getValueFromPropertiesFile('serviceConfig').appName
}

/**
 * Get Application Id
 */
def getApplicationId() {
    println "In ServiceMetadataLoader.groovy:getApplicationId"
    return JSON.getValueFromPropertiesFile('serviceConfig').appId
}

/**
 * Get Application AkmId
 */
def getApplicationAkmId() {
    println "In ServiceMetadataLoader.groovy:getApplicationAkmId"
    return JSON.getValueFromPropertiesFile('serviceConfig').akmId
}

/**
	* Get Service Status
	*@return
	*/
def getStatus() {
    println "In ServiceMetadataLoader.groovy:getStatus"
    return JSON.getValueFromPropertiesFile('serviceConfig').status
}

/**
	* Get SCM type
	*@return
	*/
def getScmType() {
    println "In ServiceMetadataLoader.groovy:getScmType"
    return JSON.getValueFromPropertiesFile('serviceConfig').scmType
}
