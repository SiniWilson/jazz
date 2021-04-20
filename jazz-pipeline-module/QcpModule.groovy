#!groovy?

/*
* QcpModule.groovy
* @author: Saurav Dutta
* @version: 2.0
*/

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import common.util.Json as JSON
import static common.util.Shell.sh as sh

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/*
* Set the Default values used for QCP
*
* Note - serviceId, applicationNameData are for future use
*/ 
def initialize(versionNumber = null){
    println "In QcpModule.groovy:setDefaultValues"
        
    /*
    * setting the default values
    */
    def configData = JSON.getValueFromPropertiesFile('configData')
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def serviceId = serviceConfig.id
    def service = serviceConfig.service
    def domain = serviceConfig.domain
    def akmIdValue = serviceConfig.akmId
    def applicationNameData = serviceConfig.appName
    
    akmId = configData.QCP.DEFAULTS.AKMID
    applicationName = configData.QCP.DEFAULTS.APPLICATION_NAME
    version = configData.QCP.DEFAULTS.VERSION

    if(akmIdValue && akmIdValue.trim()) {
        akmId = akmIdValue.trim()
    }

    if(domain && domain.trim() && service && service.trim()) {
        /*
        * using jazz.$userNamespace.$userServiceName as the naming convention
        */
        applicationName = "${applicationName}.${domain}.${service}"
    }

    if(versionNumber && versionNumber.trim()){
        version = versionNumber
    }

    JSON.setValueToPropertiesFile('akmId', akmId)
    JSON.setValueToPropertiesFile('applicationName', applicationName)
    JSON.setValueToPropertiesFile('version', version)
}


/*
* Prepare payload and send event to QCP
* 
* type - QCP deployment event type (Post/Pre)
* status - deployment status (success/fail)
* env - jazz environment (prod/stg/dev)
* gitlabJobId - $CI_JOB_ID (gitlab pipeline job id)
*/
def sendQCPEvent(type, status){
    println "In QcpModule.groovy:sendQCPEvent"

    try {
         def props = JSON.getAllProperties()
         def env = System.getenv()
         def environmentLogicalId = props.environmentLogicalId
        if(type == null || !type.trim() || status == null || !status.trim() || environmentLogicalId == null || !environmentLogicalId.trim()) {
            println "Error! Information required to send QCP event is incomplete/unavailable, cannot send QCP event.."
        } else {
           
            def applicationData = [:]
            def qcpData = [:]

            qcpData['type'] = type
            qcpData['environment'] = traslateEnvforQCP(environmentLogicalId)
            qcpData['status'] = status
            def serviceMap = applicationData.get("${props.applicationName}", [:])
            serviceMap['version'] = "${props.version}.${env.CI_JOB_ID}"
            serviceMap['akmid'] = props.akmId
            serviceMap['branch'] = props.repoBranch
            qcpData['applications'] = applicationData

            def jsonData = JSON.objectToJsonString(qcpData)
            def postresponse = sendRequest(jsonData)
            if( postresponse?.data?.success ){
                println "Successfully sent qcp event: ${postresponse.message}"
            } else {
                println "Failed to send qcp event: ${postresponse.message}"
            }
        }
    } catch(ex) {
        println "sendQCPEvent failed: " + ex.message
    }
}

/*
* Sends event to QCP endpoint
*/
def sendRequest(data) {
    println "In QcpModule.groovy:sendRequest"

    def apiResponse = null
    def configData = JSON.getValueFromPropertiesFile('configData')
    def auth = getAuthentication(configData.QCP.CREDENTIALS.USERNAME, configData.QCP.CREDENTIALS.PASSWORD)
    if(auth) {
        try {
            apiResponse = sh("curl -X POST -k -v -H 'Content-Type:application/json' -H 'Authorization: Basic $auth' '${configData.QCP.ENDPOINT}' -d '${data}'", true)
            apiResponse = JSON.parseJson(apiResponse);
        } catch (ex){
            println "Error occurred while posting event to QCP: " + ex.message
            apiResponse = null // making sure apiResponse returned is null
        }
    } else {
        println "Failed while getting auth token to send QCP event"
    }
    return apiResponse
}

/**
* Converts the username and password into base64 string
*/
def getAuthentication(username, password) {
    println "In QcpModule.groovy:getAuthentication"

    def basicToken = null
    if( username != null && password != null) {
        def credentials = "${username}:${password}"
        basicToken = credentials.bytes.encodeBase64().toString()
    } else {
        println "Username/Password cannot be empty"
    }
    return basicToken
}

/*
* Translate jazz environment to QCP environment
*/ 
def traslateEnvforQCP(env) {
    println "In QcpModule.groovy:traslateEnvforQCP"

    def qEnv = null
    if(env == "prod"){
        qEnv = "prod"
    } else if(env == "stg") {
        qEnv = "stg01"
    } else {
        qEnv = "dev01"
    }
    return qEnv
}