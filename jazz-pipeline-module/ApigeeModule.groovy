#!groovy
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*
import java.util.regex.Matcher
import groovy.transform.Field


/*
* Set the Default values used for QCP
*
* directory as the apigee-module or its parent.
*/ 
def setDefaultValues(configLoader){
    println "In ApigeeModule.groovy:setDefaultValues"
    def env = System.getenv()
    def buildNumber = env.CI_PIPELINE_IID
    JSON.setValueToPropertiesFile('apigeeConfig', configLoader.APIGEE)
    JSON.setValueToPropertiesFile('apiversion', "${configLoader.APIGEE.BUILD_VERSION}.${buildNumber}")
    JSON.setValueToPropertiesFile('apigeeModuleRoot', getModuleRoot())
}

/**
 * Create and deploy an Apigee API proxy
 *
 * @param swaggerFile The path to the swagger file for the API.
 * @param arn ARN object containing the full name as well as the component values.
 * @param envKey The type of deployment. e.g. PROD or TEST.
 * @param environmentID The id used to differentiate proxies from different branches in the same domain.
 * @param config The config metadata.
 * @param contextMap The base context to be used for event logging.
 * @return String with the url of the deployed API proxy.
 */
String deploy(swaggerFile, arn, envKey, environmentID, apiPath) {
    println "In ApigeeModule.groovy:deploy"
    
    setDefaultValues(JSON.getValueFromPropertiesFile('configData'))
    def contextMap = []
    def env = System.getenv()
    def apigeeDeployUser = env.APIGEE_DEPLOY_USER
    def apigeeDeployPassword = env.APIGEE_DEPLOY_PASSWORD
    def events = new EventsModule()
    def apigeeConfig = JSON.getValueFromPropertiesFile('apigeeConfig')
    def config = JSON.getValueFromPropertiesFile('serviceConfig')
    def apigeeModuleRoot = JSON.getValueFromPropertiesFile('apigeeModuleRoot')
    def apiversion = JSON.getValueFromPropertiesFile('apiversion')

    def email = config['created_by']
    def deployEnv = apigeeConfig.API_ENDPOINTS[envKey]
    def apigeeContextMap = [:]
    apigeeContextMap.putAll(contextMap)
    apigeeContextMap.putAll(deployEnv)
    def hostUrl = getHostUrl(swaggerFile, deployEnv, config['service'], environmentID)
    def templateValues = getTemplateValues(swaggerFile, environmentID, config['domain'], config['service'])

    def functionName = arn.functionName
    try {
        events.sendStartedEvent('APIGEE_API_PROXY_GEN', 'Creating Apigee API proxy configuration', apigeeContextMap)
        proxygen(templateValues)
        events.sendCompletedEvent('APIGEE_API_PROXY_GEN', 'Completed Apigee API proxy configuration', apigeeContextMap)
    } catch(e) {
        events.sendFailureEvent('APIGEE_API_PROXY_GEN', e.getMessage(), apigeeContextMap)
        throw new Exception("Failure during proxy generation: ${e}")
    }
    // echo out the projects: Build Version and Apiversion Number
    println "Build Version is ${apigeeConfig.BUILD_VERSION}"
    println "Apiversion Number is ${apiversion}"
    println "environmentID is ${environmentID}"

    try {
    // call the script bundle.sh which will build the proxy files and upload them to artifactory. Note artifactory url is set in the script.
        events.sendStartedEvent('APIGEE_API_PROXY_BUILD', 'Creating Apigee API proxy bundle', apigeeContextMap)
        println sh("cd ${apigeeModuleRoot}/gen/CoreAPI/build/;bash bundle.sh ${templateValues.ProxyName} v1 ${functionName} ${apiversion} ${email} ${environmentID}", true)
        events.sendCompletedEvent('APIGEE_API_PROXY_BUILD', 'Completed Apigee API proxy bundle', apigeeContextMap)
    } catch (e) {
        events.sendFailureEvent('APIGEE_API_PROXY_BUILD', e.getMessage(), apigeeContextMap)
        throw new Exception("Error in APIGEE_API_PROXY_BUILD: ${e}")
    }

    try {
        events.sendStartedEvent('APIGEE_API_PROXY_DEPLOY', 'Deploying Apigee API proxy bundle', apigeeContextMap)
        println sh("cd ${apigeeModuleRoot}/gen/CoreAPI;bash build/deploy.sh https://${deployEnv.MGMT_HOST} ${deployEnv.MGMT_ORG} ${deployEnv.MGMT_ENV} ${templateValues.ProxyName} ${apiversion} ${apigeeDeployUser} ${apigeeDeployPassword} v1", true)
        events.sendCompletedEvent('APIGEE_API_PROXY_DEPLOY', 'Completed Apigee API proxy bundle deployment', apigeeContextMap)
    } catch (e) {
        events.sendFailureEvent('APIGEE_API_PROXY_DEPLOY', e.getMessage(), apigeeContextMap)
        throw new Exception("Error in APIGEE_API_PROXY_DEPLOY: ${e}")
    }

    return hostUrl
}

/**
 * Delete a previous deployed API proxy from Apigee
 *
 * @param swaggerFile The swagger file for the related API
 * @param envKey The type of deployment. e.g. PROD or TEST.
 * @param environmentID The id used to differentiate proxies from different branches in the same domain.
 * @param config The config metadata.
 */
def delete(swaggerFile, envKey, environmentID, apigeeDeployUser, apigeeDeployPassword) {
    def events = new EventsModule()
    println "In ApigeeModule.groovy:delete"

    setDefaultValues(JSON.getValueFromPropertiesFile('configData'))
    def apigeeConfig = JSON.getValueFromPropertiesFile('apigeeConfig')
    def config = JSON.getValueFromPropertiesFile('serviceConfig')
    def apigeeModuleRoot = JSON.getValueFromPropertiesFile('apigeeModuleRoot')
    def templateValues = getTemplateValues(swaggerFile, environmentID, config['domain'], config['service'])
    def deployEnv = apigeeConfig.API_ENDPOINTS[envKey]
    try {
        events.sendStartedEvent('APIGEE_API_PROXY_DELETE', 'Deleting Apigee API proxy.')
        println sh("bash ${apigeeModuleRoot}/build/delete.sh https://${deployEnv.MGMT_HOST} ${deployEnv.MGMT_ORG} ${deployEnv.MGMT_ENV} ${templateValues.ProxyName} ${apigeeDeployUser} ${apigeeDeployPassword}", true)
        events.sendCompletedEvent('APIGEE_API_PROXY_DELETE', 'Completed deletion of Apigee API proxy.')
    } catch (e) {
        events.sendFailureEvent('APIGEE_API_PROXY_DELETE', e.getMessage())
        throw new Exception("Error in APIGEE_API_PROXY_DELETE: ${e}")
    }
}


/**
 * Determine the path to the root of the apigee module files.
 *
 * @return String with the full path to the apigee directory.
 */
String getModuleRoot() {
    println "In ApigeeModule.groovy:getModuleRoot"
    println sh("pwd;ls -la WorkingDirectory/jazz-pipeline-module", true)
    return "WorkingDirectory/jazz-pipeline-module/apigee"
    //sh("cp -r WorkingDirectory/jazz-pipeline-module/apigee ApigeeRepo/")

    //return "ApigeeRepo"
}

/**
 * Construct the url of the newly created API proxy.
 *
 * @param deployEnv The deployment env used for this deployment.
 * @param swaggerFile The swagger file for this deployment.
 * @param service Name of the service being deployed.
 * @environmentID Environment's unique id
 * @return String with the url for the deployed API proxy.
 */
String getHostUrl(swaggerFile, deployEnv, service, environmentID) {
    println "In ApigeeModule.groovy:getHostUrl"
    def swaggerObj = JSON.readFile(swaggerFile)
    def domain = swaggerObj.basePath.substring(1)

    return "https://${deployEnv.SERVICE_HOSTNAME}/${domain}/${service}"
}

/**
 * Populate an map with values to be used when replacing tokens in templates.
 *
 * @param swaggerFile The swagger file to be used in order to parse values.
 * @param environmentID The id used to differentiate proxies from different branches in the same domain.
 * @domain domain name for the service
 * @return Map with the template token replacement values.
 */
def getTemplateValues(swaggerFile, environmentID, domain, service) {
    println "In ApigeeModule.groovy:getTemplateValues"
    def apigeeModuleRoot = JSON.getValueFromPropertiesFile('apigeeModuleRoot')
    def swaggerObj = JSON.readFile(swaggerFile)
    def apigeeSwaggerRoot = "${apigeeModuleRoot}/tmp"
    def apigeeSwagger = "${apigeeSwaggerRoot}/swagger.json"
    sh("rm -rf '${apigeeSwaggerRoot}'")
    sh("mkdir '${apigeeSwaggerRoot}'")

    // pre-process swagger
    println "Modifying swagger paths for Apigee use"
    def serviceName = "/" + service
    def modPaths = [:]
    for (path in swaggerObj.paths) {
        if (!path.key.startsWith(serviceName)) {
            throw new Exception("error - ${path.key} does not start with ${serviceName}!")
        }

        def key = path.key.substring(serviceName.size())
        if (key == "") {
            key = '/'
        }
        modPaths.put(key, path.value)
    }
    // We dont need to modify the swagger basepath and paths for Apigee
    swaggerObj.basePath += serviceName
    swaggerObj.paths = modPaths
    def out = JsonOutput.prettyPrint(JsonOutput.toJson(swaggerObj))
    println out
    File file = new File(apigeeSwagger)
    file.write(out)
    //JSON.writeFile(apigeeSwagger, out)
    println "Swagger file modification complete."

    // Splitting the serviceName string & grabbing the servicename only from the path
    def proxyName = "${domain}-${serviceName.split("/")[1]}-${environmentID}"
    println "proxyName = ${proxyName}"
    def rootPath = sh("pwd", true).trim()
    def result = [
        SwaggerFile : rootPath + "/" + apigeeSwagger,
        ProxyName : proxyName,
        ProxyDescription : "API proxy for ${proxyName}",
        TargetDescription : swaggerObj.info.title
    ]
    return result;
}

/**
 * Run the proxygen process to generate the api proxy definition files.
 *
 * @param templateValues A Map of token name, replacement values to use when filling templates.
 */
def proxygen(templateValues) {
    println "In ApigeeModule.groovy:proxygen"
    def apigeeConfig = JSON.getValueFromPropertiesFile('apigeeConfig')
    def apigeeModuleRoot = JSON.getValueFromPropertiesFile('apigeeModuleRoot')
    sh("rm -rf '${apigeeModuleRoot}/gen'")
    sh("rm -rf '${apigeeModuleRoot}/templates/CoreAPI'")
    sh("mkdir ${apigeeModuleRoot}/templates/CoreAPI")
    if (apigeeConfig.USE_SECURE) {
        sh("cp -r ${apigeeModuleRoot}/templates/coreAPI_secure/* ${apigeeModuleRoot}/templates/CoreAPI", true)
    } else {
        sh("cp -r ${apigeeModuleRoot}/templates/coreAPI_default/* ${apigeeModuleRoot}/templates/CoreAPI", true)
    }
    applyTemplate("${apigeeModuleRoot}/proxygen_template.properties", "${apigeeModuleRoot}", "proxygen.properties", templateValues)
    println sh("cd ${apigeeModuleRoot};java -cp ProxyGen.jar com.tmobile.apigee.ProxyCodeGen proxygen.properties", true)
    println sh("mv ${apigeeModuleRoot}/gen/CoreAPI/Proxies/* ${apigeeModuleRoot}/gen/CoreAPI", true)
    sh("mkdir ${apigeeModuleRoot}/gen/CoreAPI/build")
    println sh("cp ${apigeeModuleRoot}/build/* ${apigeeModuleRoot}/gen/CoreAPI/build", true)
}

/**
 * Replace all token values in a template file with the corresponding value
 * from a token:value map.
 *
 * @param templateFile The full path to the template file.
 * @param targetDir The directory into which filled template will be written.
 * @param targetFile The file name for the filled template.
 * @param valueMap A map of token:value pairs used to replace tokens in the template file.
 */
def applyTemplate(templateFile, targetDir, targetFile, valueMap) {
    println "In ApigeeModule.groovy:applyTemplate"
    template = new File("${templateFile}").getText('UTF-8')
    def filledIn = replaceAll(template, valueMap)
    def modTargetFile = targetFile

    while (modTargetFile.startsWith('/')) {
        modTargetFile = modTargetFile.substring(1)
    }
    File file = new File(targetDir + "/" + modTargetFile)
    file.write(filledIn)
    //JSON.writeFile(targetDir + "/" + modTargetFile, filledIn)
}

/**
 * Replace all elements of a token in a string using a list of token:value pairs.
 *
 * @param templateString The string containing tokens to replace.
 * @param valueMap A map of token:value pairs used to replace tokens in the template file.
 * @return String containing the templateString with all tokens present in the valueMap replaced with their values.
 */
String replaceAll(templateString, valueMap) {
    println "In ApigeeModule.groovy:replaceAll"
    String result = templateString
    for (kvp in valueMap) {
        def tokenString = '\\{\\{' + kvp.key + '\\}\\}';
        result = result.replaceAll(tokenString, Matcher.quoteReplacement(kvp.value))
    }
    return result
}