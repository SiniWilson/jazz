#!groovy?
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*

/** AWSApiGatewayModule.groovy module */

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/**
 * Delete API Gateway Resources
 * @params apiId: API Id
 * @params resourceId: resource Id
 * @params stage: stage
 * @params credsId: Credential Id
 */
def deleteApiGatewayResources(apiId, resourceId, stage, credsId){
	println "In AWSApigatwayModule.groovy:deleteApiGatewayResources"
	println "Resource Id: $resourceId"
	println "Stage: $stage"
	try{
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
		def statusJson = sh("aws apigateway delete-resource --rest-api-id ${apiId} --resource-id ${resourceId} --region ${serviceConfig.region} --output json --profile ${credsId}", true)
		println "API Resource Delete results: $statusJson"
		def deploymentStatusJson = sh("aws apigateway create-deployment --rest-api-id ${apiId} --region ${serviceConfig.region} --stage-name $stage --description 'API deployment after resource clean up' --output json --profile ${credsId}", true)
		println "API Resource Deployment after delete results: $deploymentStatusJson"
	} catch (ex) {
		println "API gateway resource deletion failed: " + ex.message
		throw new Exception("API gateway resource deletion failed!", ex)
	}
}


/**
 * Find the resource Id of the service deployed in API gateway
 * @params apiId: API Id
 * @params resourcepath:  resource path
 * @params credsId: Credential Id
 */
def findResourceId(apiId, resourcePath, credsId) {
	println "In AWSApiGatewayModule.groovy:findResourceId"
	def resourceId = null
	try {
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
		def outputStr = sh("aws apigateway get-resources --rest-api-id ${apiId} --region ${serviceConfig.region} --output json --profile ${credsId}", true)
		def list = JSON.jsonParse(outputStr)
		for (items in list["items"]) {
			if(items["path"] == resourcePath) {
				resourceId = items["id"]
			}
		}
		return resourceId
	} catch(ex) {
		println "findResourceId Failed: " + ex.message
		throw new Exception("findResourceId Failed", ex)
	}
}


/*
* Get the configuration of the endpoint if it is REGIONAL or not
* @params apiId: API ID
* @params credsId: Credential ID
*/
def getEndpointConfigType(apiId, credsId) {
	println "In AWSApiGatewayModule.groovy:getEndpointConfigType"
	try {
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
		def getApiDetails = sh("aws apigateway get-rest-api --rest-api-id ${apiId} --region ${serviceConfig.region} --profile ${credsId}", true)
		def apiDetails = JSON.jsonParse(getApiDetails)
		println "apiDetails: $apiDetails"
		return apiDetails.endpointConfiguration.types[0]
	} catch(ex) {
		println "getEndpointConfigType failed:" + ex.message
		throw new Exception("getEndpointConfigType failed", ex)
	}
	
}

/**
 * Get Api Id
 * @params environmentId: Environment Id
 * @params configs: AWS account data
 */
def getApiId(environmentId, configs) {
	println "In AWSApiGatewayModule.groovy:getApiId"

	try {
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
		def environmentInfo = JSON.getValueFromPropertiesFile('environmentInfo')
		def endpointType = getEndpointType(environmentInfo['is_public_endpoint'])
		def apiId
		if(environmentId.equals('prod')){
			if (endpointType.equals("public")) {
				apiId = getApiIdNameMapping(configs.PROD.PUBLIC, serviceConfig['domain'], serviceConfig['service'], "ID")
			} else {
				apiId = getApiIdNameMapping(configs.PROD.PRIVATE, serviceConfig['domain'], serviceConfig['service'], "ID")
			}
		} else if(environmentId.equals('stg')){
			if (endpointType.equals("public")) {
				apiId = getApiIdNameMapping(configs.STG.PUBLIC, serviceConfig['domain'], serviceConfig['service'], "ID")
			} else {
				apiId = getApiIdNameMapping(configs.STG.PRIVATE, serviceConfig['domain'], serviceConfig['service'], "ID")
			}
		} else {
			if (endpointType.equals("public")) {
				apiId = getApiIdNameMapping(configs.DEV.PUBLIC, serviceConfig['domain'], serviceConfig['service'], "ID")
			} else {
				apiId = getApiIdNameMapping(configs.DEV.PRIVATE, serviceConfig['domain'], serviceConfig['service'], "ID")
			}
		}
		return apiId
	} catch (ex) {
		println "getApiId failed:" + ex.message
		throw new Exception("getApiId failed", ex)
	}
}

/*
* Get Endpoint type
* @params publicEndpoint: service endpoint type
*/
def getEndpointType(publicEndpoint) {
	println "In AWSApiGatewayModule.groovy:getEndpointType"
	try {
		if(publicEndpoint) {
			return 'public'
		}
		return 'private'
	} catch (ex) {
		println "getEndpointType failed:" + ex.message
		throw new Exception("getEndpointType failed", ex)
	}
}

/*
* Get the required account Information
* @params serviceConfig: service config data
* @params configLoader: admin config data
*/
def getAccountInfo(serviceConfig, configLoader) {
	println "In AWSApiGatewayModule.groovy:getAccountInfo"
	def deploymentAccount = JSON.getValueFromPropertiesFile('deploymentAccount')
	println "aws accounts: ${configLoader.AWS.ACCOUNTS}"
	println "deploymentAccount: ${deploymentAccount}"
	try {
		def dataObj = {};
		for (item in configLoader.AWS.ACCOUNTS) {
			if(item.ACCOUNTID == deploymentAccount){
				dataObj = item
			}
		}
		return dataObj;
	} catch (ex) {
		println "getAccountInfo failed:" + ex.message
		throw new Exception("getAccountInfo failed", ex)
	}
}


/*
* Get API Gateway Information
* @params stage: stage
* @params key: key (ID/DNS_HOSTNAME/NAME/AD_AUTHORIZER)
*/
def getApiGatewayInfo(stage, key) {
	println "In AWSApiGatewayModule.groovy:getApiGatewayInfo"
	try {
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
		println "serviceConfig: $serviceConfig"
		def configData = JSON.getValueFromPropertiesFile('configData')
		println "configData: $configData"
		def accountDetails = getAccountInfo(serviceConfig, configData) // hardcoded account
		println "accountDetails: $accountDetails"
		def deploymentRegion = JSON.getValueFromPropertiesFile('deploymentRegion')
		def gatewayValue
		for (item in accountDetails.REGIONS) {
			if(item.REGION == deploymentRegion){
				gatewayValue = item.API_GATEWAY
			}
		}
		if (stage && stage == 'stg') {
			return getApiIdNameMapping(gatewayValue.STG.PRIVATE, serviceConfig['domain'], serviceConfig['service'], key)
		} else if (stage && stage == 'prod') {
			return getApiIdNameMapping(gatewayValue.PROD.PRIVATE, serviceConfig['domain'], serviceConfig['service'], key)
		} else if (stage && stage == 'integration') {
			return getApiIdNameMapping(gatewayValue.INTEGRATION.INTERAL, serviceConfig['domain'], serviceConfig['service'], key)
		} else {
			return getApiIdNameMapping(gatewayValue.DEV.PRIVATE, serviceConfig['domain'], serviceConfig['service'], key)
		}
	} catch (ex) {
		println "getApiGatewayInfo failed:" + ex.message
		throw new Exception("getApiGatewayInfo failed", ex)
	}
}

/*
* Get API Id Name Mapping
* @params apiIdMapping: API Id mapping
* @params namespace: Domain
* @params service: Service
* @params key: key (ID/DNS_HOSTNAME/NAME/AD_AUTHORIZER)
*/
def getApiIdNameMapping(apiIdMapping, namespace, service, key) {
	println "In AWSApiGatewayModule.groovy:getApiIdNameMapping"
	try {
		def apiId
		if (!apiIdMapping) {
			throw new Exception("No mapping document provided to lookup API ${key} !")
		}
		if (apiIdMapping["${namespace}_${service}"]) {
			apiId = apiIdMapping["${namespace}_${service}"][key];
		} else if (apiIdMapping["${namespace}_*"]) {
			apiId = apiIdMapping["${namespace}_*"][key];
		} else {
			apiId = apiIdMapping["*"][key];
		}
		JSON.setValueToPropertiesFile("apiId:${key}", apiId);
		return apiId;
	} catch (ex) {
		println "getApiIdNameMapping failed:" + ex.message
		throw new Exception("getApiIdNameMapping failed", ex)
	}
}
