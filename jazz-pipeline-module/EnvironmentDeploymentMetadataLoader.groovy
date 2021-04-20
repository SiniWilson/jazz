#!groovy?
/*
* EnvironmentDeploymentMetadataLoader.groovy
* This module will load all environment and deployment related  metadata from respective  tables or Apis 
* get it ready for Jenkins builds. It loads data for all service types. 
* @author: Saurav Dutta
* @version: 2.0
*/

import groovy.json.JsonSlurperClassic
import groovy.json.JsonParserType
import groovy.json.JsonOutput
import groovy.transform.Field
import java.text.*;
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

//Setting environment logical id for future reference
def setEnvironmentLogicalId(envLogicalId) {
	println "EnvironmentDeploymentMetadataLoader.groovy: setEnvironmentLogicalId"
	JSON.setValueToPropertiesFile('environmentLogicalId', envLogicalId)
}

/*
* Function to set the user_pipeline flag to true in environment
*/
def updateEnvironmentUserPipelineFlag(flag) {
	println "In EnvironmentDeploymentMetadataLoader:groovy:updateEnvironmentUserPipelineFlag"

	def props = JSON.getAllProperties();
	def serviceConfig = props['serviceConfig'];
	try {
		if(flag == true) {
			def params = [
				"user_pipeline": true
			]
			def payload = JSON.objectToJsonString(params)
			//println "payload...$payload"
			
			def resObj = sh("curl -X PUT -k -v -H 'Content-Type:application/json' -H 'Authorization: ${props['authToken']}' 'Jazz-Service-ID: ${props['serviceId']}' '${props['apiBaseUrl']}/environments/${props['environmentId']}?service=${serviceConfig['service']}&domain=${serviceConfig['domain']}' -d '${payload}'", true);
			resObj = JSON.parseJson(resObj);
			//println "resObj: ${resObj}"

		}
	
	} catch(ex) {
		println "error while setting environment user_pipeline " + ex.toString()
		throw new Exception('error while setting environment user_pipeline', ex);
	}
}

/*
* Getting the environment logicalId with guid
*/
def getEnvironmentLogicalData(env) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvironmentLogicalData"

	def props = JSON.getAllProperties()
	def environmentOutput
	def environmentLogicalId
	def API_ENVIRONMENT_QUERY_URL = "${props['apiBaseUrl']}/environments?environmentId=${env}"
	println "API_ENVIRONMENT_QUERY_URL $API_ENVIRONMENT_QUERY_URL"

	try {
		def envResp = sh("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: ${props['authToken']}' -H 'Jazz-Service-ID: ${props['serviceId']}' '$API_ENVIRONMENT_QUERY_URL'", true)		
		environmentOutput = JSON.parseJson(envResp);

	} catch(ex) {
		println "error while fetching environment logical id " + ex.toString()
		throw new Exception('error while fetching environment logical id', ex);
	}

	if(environmentOutput != null && environmentOutput.data != null && environmentOutput.data.environment != null) {	
		environmentLogicalId = environmentOutput.data.environment[0].logical_id
		println "environmentLogicalId: $environmentLogicalId"
		return environmentLogicalId
	}
}

/*
* Getting the environment logical ID
*/
def getEnvironmentLogicalId() {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvironmentLogicalId"

	def props = JSON.getAllProperties()
	def env = System.getenv()
	def serviceConfig = props['serviceConfig']

	def environmentOutput
	def API_ENVIRONMENT_QUERY_URL = "${props['apiBaseUrl']}/environments?service=${serviceConfig['service']}&domain=${serviceConfig['domain']}"
	println "API_ENVIRONMENT_QUERY_URL $API_ENVIRONMENT_QUERY_URL"

	try {
		def envResp = sh("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: ${props['authToken']}' -H 'Jazz-Service-ID: ${props['serviceId']}' '$API_ENVIRONMENT_QUERY_URL'", true)		
		environmentOutput = JSON.parseJson(envResp);

	} catch(ex) {
		println "error while fetching environment logical id " + ex.toString()
		throw new Exception('error while fetching environment logical id', ex);
	}
	
	def environmentLogicalId
	def environmentInfo
	if(environmentOutput != null && environmentOutput.data != null && environmentOutput.data.environment != null) {	
		for(environment in environmentOutput.data.environment) {
			if(environment.physical_id && environment.physical_id.equals(props.repoBranch)){
				environmentLogicalId = environment.logical_id
				environmentInfo = environment
				break;
			}
		}
	}

	JSON.setValueToPropertiesFile('environmentLogicalId', environmentLogicalId)
	JSON.setValueToPropertiesFile('environmentInfo', environmentInfo)

	return environmentLogicalId
}

//Getting environmentId
def getEnvironmentId() {
	println "EnvironmentDeploymentMetadataLoader.groovy: getEnvironmentId"
	
	def props = JSON.getAllProperties()
	def env = System.getenv()
	def serviceConfig = props['serviceConfig']
	
	def API_ENVIRONMENT_QUERY_URL = "${props['apiBaseUrl']}/environments?service=${serviceConfig['service']}&domain=${serviceConfig['domain']}"
	println "API_ENVIRONMENT_QUERY_URL $API_ENVIRONMENT_QUERY_URL"
	def environmentId
	def environmentOutput
	def environmentInfo

	try {
		def envResp = sh("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: ${props['authToken']}' -H 'Jazz-Service-ID: ${props['serviceId']}' '$API_ENVIRONMENT_QUERY_URL'", true)		
		environmentOutput = JSON.parseJson(envResp);

	} catch(ex) {
		println "error while fetching environment id " + ex
		throw new Exception('error while fetching environment id', ex);
	}	
	if(environmentOutput != null && environmentOutput.data != null && environmentOutput.data.environment != null) {	
		for(environment in environmentOutput.data.environment) {
			if(environment.physical_id && environment.physical_id.equals(props.repoBranch)){
				if (props.environmentLogicalId) {
					// if environmentLogicalId is already available, it can help with picking up the correct environment entry
					// Helps with distinguishing stg & prod environments that share same physical_id
					println "Environment logicalId exists, finding match with logicalId & physicalId"
					if (environment.logical_id && environment.logical_id.equals(props.environmentLogicalId)) {
						environmentId = environment.id
						environmentInfo = environment
						break;
					}
				} else {
					println "Environment logicalId is null, found match with physicalId"
					// if g_environment_logical_id is not available yet, match with physical_id is the only condition that works here.
					environmentId = environment.id
					environmentInfo = environment
					JSON.setValueToPropertiesFile('environmentLogicalId', environment.logical_id)
					break;
				}				
			}
		}
	}
	
	println "environmentId: $environmentId"
	JSON.setValueToPropertiesFile('environmentId', environmentId)
	JSON.setValueToPropertiesFile('environmentInfo', environmentInfo)
	return environmentId
}

/*
* Gives the latest archived environment logical id
*/
def getLatestEnvironmentLogicalIdPendingArchival() {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getLatestEnvironmentLogicalIdPendingArchival"

	def environmentInfo = JSON.getValueFromPropertiesFile('environmentInfo')
	
	def archivedEnvironments 
	def latestArchivedEnvironment
	def latestdeploymentAccounts
	if(!environmentInfo){
		println "getLatestEnvironmentLogicalIdPendingArchival: environmentInfo not available - getting it!"
		environmentInfo = getEnvironmentInfo()
	}
	def envLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
	if (environmentInfo && environmentInfo["metadata"] && environmentInfo["metadata"]["archived_environments"]) {
		archivedEnvironments =  environmentInfo["metadata"]["archived_environments"]
		archivedEnvironments = sort(archivedEnvironments)
		def keys = archivedEnvironments.keySet() as List
		def oldEnvInfo = archivedEnvironments[keys[0]]
		println "oldEnvInfo : $oldEnvInfo"
		if (oldEnvInfo.status == 'pending_archival') {
			latestArchivedEnvironment = keys[0]
			latestdeploymentAccounts = oldEnvInfo.deployment_accounts
		}		
	}
	def currentDeploymentAccount = environmentInfo.deployment_accounts
	if ((latestArchivedEnvironment && latestArchivedEnvironment != envLogicalId) || 
		(latestdeploymentAccounts && currentDeploymentAccount && latestdeploymentAccounts.size() > 0 && currentDeploymentAccount.size() > 0
		&& ( latestdeploymentAccounts[0].account != currentDeploymentAccount[0].account || latestdeploymentAccounts[0].region != currentDeploymentAccount[0].region ))) {
			println "Changed environment logical id, account or region"
			println "latestArchivedEnvironment : $latestArchivedEnvironment"
			JSON.setValueToPropertiesFile('archiveEnvironmentId', latestArchivedEnvironment)
	}
}

/*
* Method to sort the data based on timestamp
*/
def sort(items) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:sort"
	def format = "yyyy-MM-dd HH:mm:ss"
	def itemsSorted = items.sort {a, b -> 
		def val1 = a.value.created.replace("T", " ");
		def val2 = b.value.created.replace("T", " ");
		def x = new SimpleDateFormat(format).parse(val1)
		def y = new SimpleDateFormat(format).parse(val2)
		y <=> x}
	return itemsSorted
}

/*
* Gets environment info for a service name and domain
*/
def getEnvironmentInfo(multipleEnv = false) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvironmentInfo"

	def env = System.getenv()
	def environmentId = env.ENVIRONMENT_ID
	def props = JSON.getAllProperties()
	def serviceConfig = props.serviceConfig
	def environmentInfo = props.environmentInfo
	def environmentOutput
	println("environmentInfo: ${environmentInfo} multipleEnv: ${multipleEnv}")
	if(!environmentInfo || multipleEnv) {
		try {
			def envResp = sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: ${props['authToken']}' -H 'Jazz-Service-ID: ${props['serviceId']}' '${props['apiBaseUrl']}/environments?service=${serviceConfig['service']}&domain=${serviceConfig['domain']}'", true)
			environmentOutput = JSON.parseJson(envResp);
		} catch(ex) {
			println "error while getting environment info: " + ex.toString()
			throw new Exception("error while getting environment info", ex)
		}
		if (environmentOutput != null && environmentOutput.data != null && environmentOutput.data.environment != null) {
			println("props: ${props.environmentId}, ${props.environmentLogicalId}")
			for (environment in environmentOutput.data.environment) {
				println("processing environment: ${environment.id}, ${environment.logical_id}, ${environment.physical_id}")
				/*
				* for delete pipeline we are matching with environmentId (Guid)
				*/
				if(multipleEnv && environment.id && environment.id.equals(props.environmentId)) {
					println "environment is: ${environment.id}, ${environment.logical_id}, ${environment.physical_id} - setting environmentInfo - 1"
					environmentInfo = environment
					JSON.setValueToPropertiesFile('environmentInfo', environment)
					break;					
				} else if (environment.logical_id && environment.logical_id.equals(props.environmentLogicalId)) {
					println "environment is: ${environment.id}, ${environment.logical_id}, ${environment.physical_id} - setting environmentInfo - 2"
					environmentInfo = environment
					JSON.setValueToPropertiesFile('environmentInfo', environment)
					break;					
				} else if (environment.id && environment.id.equals(props.environmentLogicalId)) {
					// TODO: Will this ever be true? Why would envId be same as logicalId. Remove after validation.
					println "environment is: ${environment.id}, ${environment.logical_id}, ${environment.physical_id} - SHOULDN'T GET HERE!"
					environmentInfo = environment
					JSON.setValueToPropertiesFile('environmentInfo', environment)
					break;
				}
			}
		}
	}
	if (!environmentInfo)
	{
		println("NO environment FOUND")
	}
	else
	{
		println("environment is FOUND: ${environmentInfo.id}, ${environmentInfo.logical_id}, ${environmentInfo.physical_id}")
	}	
	return environmentInfo;
}

/**
 * Method to create an environment for a service if the env logical id doesn't exist
 */
def createPromotedEnvironment(environmentLogicalId, createdBy, serviceBranch) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:createPromotedEnvironment"

	def authToken = JSON.getValueFromPropertiesFile('authToken')
	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def serviceName = JSON.getValueFromPropertiesFile('serviceName')
	def serviceId = JSON.getValueFromPropertiesFile('serviceId')
	def props = JSON.getAllProperties()

	def envLogicalId
	def isAvailable = checkIfEnvironmentAvailable(environmentLogicalId)
	if(!isAvailable) { // create a new environment
		try {
			
			def params = [
				  "service": serviceName,
				  "domain": serviceDomain,
				  "status": "deployment_started",
				  "created_by": createdBy,
				  "physical_id": serviceBranch,
				  "logical_id": environmentLogicalId
			]
			def payload = JSON.objectToJsonString(params)
			println "payload...$payload"

			def resObj = sh("curl -X POST -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' 'Jazz-Service-ID: $serviceId' '${props['apiBaseUrl']}/environments' -d '$payload'", true);
			resObj = JSON.parseJson(resObj);
			println "resObj $resObj"

			if(resObj && resObj.data && resObj.data.result && resObj.data.result == "success") {
				envLogicalId = environmentLogicalId
				JSON.setValueToPropertiesFile('environmentLogicalId', envLogicalId)
				
			} else {
				println "invalid response from environment API"
				throw new Exception("invalid response from environment API")
			}
				
		} catch (ex) {
			println "Could not create environment for logical_id due to: " + ex.toString()
			throw new Exception("Could not create environment for logical_id", ex)
		}
		
	} else {
		envLogicalId = environmentLogicalId
		JSON.setValueToPropertiesFile('environmentLogicalId', envLogicalId)
		
	}	
}

/**
 * Method to check an env is created with same logical id
 * @param environment = logical_id
 */
def checkIfEnvironmentAvailable(environmentLogicalId) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:checkIfEnvironmentAvailable"

	def authToken = JSON.getValueFromPropertiesFile('authToken')
	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def serviceName = JSON.getValueFromPropertiesFile('serviceName')
	def serviceId = JSON.getValueFromPropertiesFile('serviceId')
	
	def props = JSON.getAllProperties()
	def isAvailable = false
	try {
		if(environmentLogicalId) {
			def API_ENVIRONMENT_QUERY_URL = "${props['apiBaseUrl']}/environments?service=" + serviceName + "&domain=" + serviceDomain
			println "API_ENVIRONMENT_QUERY_URL is $API_ENVIRONMENT_QUERY_URL"
			def getEnvironments

			getEnvironments = sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: $serviceId' '${props['apiBaseUrl']}/environments?domain=$serviceDomain&service=$serviceName'", true)
			getEnvironments = JSON.parseJson(getEnvironments)
			println "getEnvironments $getEnvironments"

			if(getEnvironments && getEnvironments.data && getEnvironments.data.environment) {	
				for(environment in getEnvironments.data.environment) {
					if(environment.logical_id && environment.logical_id.equals(environmentLogicalId)){
						isAvailable = true
						break;
					}
				}
			}
		}
		println "isAvailable is $isAvailable"
		return isAvailable
	
	} catch(ex) {
		println "checkIfEnvironmentAvailable Failed for: " + ex.toString()
		throw new Exception("checkIfEnvironmentAvailable Failed", ex)
	}	
}

/*
* Gets list of all environment guids for a given service name and domain
*/
def getEnvironmentGuids() {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvironmentGuids"

	def authToken = JSON.getValueFromPropertiesFile('authToken')
	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def serviceName = JSON.getValueFromPropertiesFile('serviceName')
	def serviceId = JSON.getValueFromPropertiesFile('serviceId')
	
	def props = JSON.getAllProperties()
	def envGuIds = []
	JSON.setValueToPropertiesFile('environmentIdList', envGuIds)
	def environmentData

	try {
		environmentData = sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: $serviceId' '${props['apiBaseUrl']}/environments?domain=$serviceDomain&service=$serviceName'", true)
		environmentData = JSON.parseJson(environmentData)
		
	} catch(ex) {
		println "error while getting environment logical ids: " + ex.toString()
		throw new Exception("error while getting environment logical ids", ex)
	}

	try {
		if(environmentData && environmentData.data && environmentData.data.environment) {
			def envCollection = environmentData.data.environment
			if(envCollection.size() > 0){
				for(_env in envCollection){
					if( _env.status != 'archived' ){
						envGuIds.push(_env.id)
					}
				}
			}
		}
		println "envGuIds are $envGuIds"
		JSON.setValueToPropertiesFile('environmentIdList', envGuIds)
		return envGuIds
	}
	catch(e){
		println "error occured while fetching environment id list: " + e.toString()
		return envGuIds
	}
}

/*
* Gets environment logical ids for a service name and domain
*/
def getEnvironmentLogicalIds() {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvironmentLogicalIds"

	def authToken = JSON.getValueFromPropertiesFile('authToken')
	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def serviceName = JSON.getValueFromPropertiesFile('serviceName')
	def serviceId = JSON.getValueFromPropertiesFile('serviceId')
	
	def props = JSON.getAllProperties()
	def envLogicalIds = []
	JSON.setValueToPropertiesFile('environmentIdList', envLogicalIds)
	def environmentData

	try {
		environmentData = sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: $serviceId' '${props['apiBaseUrl']}/environments?domain=$serviceDomain&service=$serviceName'", true)
		environmentData = JSON.parseJson(environmentData)
		
	} catch(ex) {
		println "error while getting environment logical ids: " + ex.toString()
		throw new Exception("error while getting environment logical ids", ex)
	}

	try {
		if(environmentData && environmentData.data && environmentData.data.environment) {
			def envCollection = environmentData.data.environment
			if(envCollection.size() > 0){
				for(_env in envCollection){
					if( _env.status != 'archived' ){
						envLogicalIds.push(_env.logical_id)
					}
				}
			}
		}
		println "envLogicalIds are $envLogicalIds"
		JSON.setValueToPropertiesFile('environmentIdList', envLogicalIds)
		return envLogicalIds
	}
	catch(e){
		println "error occured while fetching environment id list: " + e.toString()
		return envLogicalIds
	}
}

/*
* Function to set the env logical id
*/
def setEnvLogicalId(env) {
	JSON.setValueToPropertiesFile('environmentLogicalId', env)
}

/*
* Gets environment branch name with env guid for a given service name and domain
*/
def getEnvBranchNameByGuid(envGuid) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvBranchNameByGuid"

	def authToken = JSON.getValueFromPropertiesFile('authToken')
	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def serviceName = JSON.getValueFromPropertiesFile('serviceName')
	def serviceId = JSON.getValueFromPropertiesFile('serviceId')

	def props = JSON.getAllProperties()
	def branchName
	def environmentData
	try {
		environmentData = sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: $serviceId' '${props['apiBaseUrl']}/environments?domain=$serviceDomain&service=$serviceName'", true)
		environmentData = JSON.parseJson(environmentData)
		
	} catch(ex) {
		println "error while getting environment branch name: " + ex.toString()
		throw new Exception("error while getting environment branch name", ex);
	}

	try {
		if(environmentData && environmentData.data && environmentData.data.environment) {
			def envCollection = environmentData.data.environment
			if(envCollection.size() > 0){
				for(_env in envCollection){
					if(_env.id == envGuid) {
						branchName = _env.physical_id
					}
				}
			}
		}
		println "branchName is $branchName"
		return branchName
	}
	catch(e){
		println "error occured while finding branch name of $logicalId: " + e.toString()
		return branchName
	}
}

/*
* Gets environment branch name for a service name and domain
*/
def getEnvironmentBranchName(logicalId) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:getEnvironmentBranchName"

	def authToken = JSON.getValueFromPropertiesFile('authToken')
	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def serviceName = JSON.getValueFromPropertiesFile('serviceName')
	def serviceId = JSON.getValueFromPropertiesFile('serviceId')

	def props = JSON.getAllProperties()
	def branchName
	def environmentData
	try {
		environmentData = sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' -H 'Jazz-Service-ID: $serviceId' '${props['apiBaseUrl']}/environments?domain=$serviceDomain&service=$serviceName'", true)
		environmentData = JSON.parseJson(environmentData)
		
	} catch(ex) {
		println "error while getting environment branch name: " + ex.toString()
		throw new Exception("error while getting environment branch name", ex);
	}

	try {
		if(environmentData && environmentData.data && environmentData.data.environment) {
			def envCollection = environmentData.data.environment
			if(envCollection.size() > 0){
				for(_env in envCollection){
					if( _env.logical_id == logicalId ){
						branchName = _env.physical_id
					}
				}
			}
		}
		println "branchName is $branchName"
		return branchName
	}
	catch(e){
		println "error occured while finding branch name of $logicalId: " + e.toString()
		return branchName
	}
}

def generateEnvironmentMap(status, metadata=null, deploymentDescriptor=null, environmentEndpoint=null) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:generateEnvironmentMap"

	def props = JSON.getAllProperties()
	def envs = System.getenv()
	def serviceConfig = props.serviceConfig
	
	def serviceCtxMap = [
		status: status,
		domain: serviceConfig.domain,
		branch: props.repoBranch,
		logical_id: props.environmentLogicalId,	
		environment_id: props.environmentId
	]
	if(environmentEndpoint != null) {
		serviceCtxMap.endpoint = environmentEndpoint
	}
	if (deploymentDescriptor != null) {
		serviceCtxMap.deployment_descriptor = deploymentDescriptor
	}
	if (metadata != null) {
		serviceCtxMap.metadata = metadata
	}
	// println "serviceCtxMap: $serviceCtxMap"
	return serviceCtxMap;
}

/*
* Generate map entry for deployment
*/
def generateDeploymentMap(status) {
	println "In EnvironmentDeploymentMetadataLoader.groovy:generateDeploymentMap"

	def props = JSON.getAllProperties()
	def envs = System.getenv()
	def serviceConfig = props.serviceConfig
	def pipelineId = envs.CI_PIPELINE_ID
	def pipelineUrl = envs.CI_PIPELINE_URL
	def environmentInfo = props['environmentInfo']
	
	if(((serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ||
		(environmentInfo && environmentInfo.user_pipeline instanceof Boolean && environmentInfo.user_pipeline )) &&
		envs.USER_CI_PIPELINE_URL && envs.USER_CI_PIPELINE_ID) {
		pipelineUrl =  envs.USER_CI_PIPELINE_URL
		pipelineId =  envs.USER_CI_PIPELINE_ID
	}
	commitOwner = sh("cd ${PROPS.WORKING_DIRECTORY}/${envs.REPO_NAME};git show -s --format='%ae' ${props.commitSha}" ).trim()
	currentStage = envs.CI_JOB_STAGE

	def serviceCtxMap = [
		environment_logical_id: props.environmentLogicalId,
		environment_id: props.environmentId,
		status: status,		
		provider_build_url: pipelineUrl,
		provider_build_id: pipelineId,
		scm_branch: props.repoBranch,
		request_id: props.REQUEST_ID,
		scm_commit_hash: props.commitSha,
		scm_url: serviceConfig.repository,
		triggered_by: commitOwner,
		current_stage: currentStage
	]
	return serviceCtxMap;
}

/*
* Generate map entry for delete deployment
*/
def generateDeleteDeploymentMap() {
	println "In EnvironmentDeploymentMetadataLoader.groovy:generateDeleteDeploymentMap"

	def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
	def providerBuildId = JSON.getValueFromPropertiesFile('providerBuildId')
	def buildUrl = JSON.getValueFromPropertiesFile('buildUrl')
	
	def serviceCtxMap = [
		domain: serviceDomain,
    	provider_build_url:  buildUrl,
    	provider_build_id: providerBuildId
 	]
	println "serviceCtxMap $serviceCtxMap"
	return serviceCtxMap;
}
