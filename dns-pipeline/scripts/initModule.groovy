#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder


/*
* initModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Initializing the service data
*/
def initialize() {
    println "initModule.groovy: initialize"

    try {
        def env = System.getenv()
        /*
        * Initialize all the incoming pipeline parameters
        */
        JSON.setValueToPropertiesFile("fqdn", env.FQDN)
        JSON.setValueToPropertiesFile('environmentLogicalId', env.ENVIRONMENT)
        JSON.setValueToPropertiesFile("endpoint", env.ENDPOINT)
        JSON.setValueToPropertiesFile("endpointType", env.ENDPOINT_TYPE)
        JSON.setValueToPropertiesFile("serviceId", env.SERVICE_ID)
        /*
        * Initialize global variables required in the pipeline
        */
        def domainName = "${env.FQDN}.jazz.t-mobile.com"
        def certRegion = 'us-east-1'
        def configData = JSON.getValueFromPropertiesFile("configData")

        JSON.setValueToPropertiesFile("domainName", domainName)
        JSON.setValueToPropertiesFile("certRegion", certRegion)
        JSON.setValueToPropertiesFile("noOfRetries", configData.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES)
        JSON.setValueToPropertiesFile("timeout", configData.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS)
        JSON.setValueToPropertiesFile("MINUTES_TO_MILLISECONDS", 60000)
        JSON.setValueToPropertiesFile("NO_OF_RETRIES", 4)
    } catch(ex) {
        println "Something went wrong while initializing: " + ex
        throw new Exception("Something went wrong while initializing: ", ex)
    }
}

/*
* Function to initialize and setup all the modules
*/
def initializeModules() {
    println "initModule.groovy: initializeModules"
    
    try {
        def utilModule = new UtilityModule()
        utilModule.showDnsEnvParams()
        
        
        def loginModule = new Login()
        def configLoaderModule = new ConfigLoader()
        def serviceMetadaLoader = new ServiceMetadataLoader()
        /*
        * Initializing and setting up the modules
        */
        loginModule.getAuthToken()
        configLoaderModule.getConfigData()
        initialize()
        serviceMetadaLoader.getServiceDetails()
        setServiceDetails()

        getEnvironmentBranchName()
    } catch(ex) {
        println "Something went wrong while initializing the modules: " + ex
        throw new Exception("Something went wrong while initializing the modules: ", ex)
    }
}

/*
* Getting environment branch name and setting it in properties file
*/
def getEnvironmentBranchName() {
    println "initModule.groovy: getEnvironmentBranchName"

    try {
        def envModule = new EnvironmentDeploymentMetadataLoader();
        def environment = JSON.getValueFromPropertiesFile("environmentLogicalId")
        println "environment: $environment"
        def branchName = envModule.getEnvironmentBranchName(environment)
        JSON.setValueToPropertiesFile('repoBranch', branchName)
        // the following method returns the ID and also stores it in the properties.json file
        def envGuid = envModule.getEnvironmentId()
        println "envGuid: $envGuid"
        /*
        * Validate the service inputs
        */
        validatServiceInputs()
    } catch(ex) {
        println "Something went wrong while retrieiving environment branch: " + ex
        throw new Exception("Something went wrong while retrieiving environment branch: ", ex)
    }
}

/*
* Setting up serviceName and domain
*/
def setServiceDetails() {
    println "initModule.groovy: setServiceDetails"

    def config = JSON.getValueFromPropertiesFile('serviceConfig')
    JSON.setValueToPropertiesFile("serviceName", config["service"])
    JSON.setValueToPropertiesFile("serviceDomain", config["domain"])
}

/*
* Validating service Inputs
*/
def validatServiceInputs() {
    println "initModule.groovy: validatServiceInputs"

    try {
        def utilModule = new UtilityModule()
        def route53Module = new AWSRoute53Module()
        def environment = JSON.getValueFromPropertiesFile("environmentLogicalId")
        def envDeploymentTargets = getEnvironmentDeploymentAccount()

        def domainName = JSON.getValueFromPropertiesFile("domainName")
        def configData = JSON.getValueFromPropertiesFile("configData")
        def config = JSON.getValueFromPropertiesFile('serviceConfig')

        def username = "";
        if(config['owner']) {
            username =  config['owner'];
        }
        JSON.setValueToPropertiesFile("username", username);
        def context_map = [
            created_by : username,
            fqdn: domainName,
            approver: configData.JAZZ.DNS.OWNER
        ]
        if(envDeploymentTargets == null){
            throw new Exception("Deployment account information is not set for the environment: ${environment}, please set them by logging into Jazz UI.")
        } else {
            if(envDeploymentTargets.isEmpty()){
                throw new Exception("Deployment account information is not set for the environment: ${environment}, please set them by logging into Jazz UI.")
            } else {
                if(envDeploymentTargets[0].containsKey('region') && envDeploymentTargets[0].containsKey('account')){
                    /*
                    * Getting the required deployment account and region information
                    */
                    def regionData = envDeploymentTargets[0].region
                    JSON.setValueToPropertiesFile('region', regionData)

                    config['account'] = envDeploymentTargets[0].account
                    config['region'] = regionData
                    JSON.setValueToPropertiesFile('serviceConfig', config)

                    JSON.setValueToPropertiesFile('deploymentRegion', regionData)
                    def zoneId = route53Module.getHostedZoneId(regionData, null, 'website') //by default keeping cloudfront hosted zoneId. Since it is same for edge-optimized endpoints and websites with cloudfront.amazonaws.com
                    JSON.setValueToPropertiesFile("zoneId", zoneId);
                    
                    def accountDetails = utilModule.getAccountInfo();
                    JSON.setValueToPropertiesFile("accountDetails", accountDetails);                    
                    
                    JSON.setValueToPropertiesFile("context_map", context_map)
                } else {
                    throw new Exception("Deployment account information is not valid for the environment: ${environment}, please set them by logging into Jazz UI.")
                }
            }
        }

        /*
        * Validating incoming pipeline parameters
        */
        validateParameters(context_map)
    } catch (ex) {
        println "Something went wrong while validating service inputs: " + ex
        throw new Exception("Something went wrong while validating service inputs: ", ex)
    }
}

/*
* Validating incoming pipeline parameters
*/
def validateParameters(context_map) {
    println "initModule.groovy: validateParameters"

    def eventsModule = new EventsModule()
    eventsModule.sendStartedEvent('VALIDATE_DNS_INPUT', 'Request validation started', context_map)
    try {
        def props = JSON.getAllProperties()
        if  (props['environmentLogicalId'] == "") {
            eventsModule.sendFailureEvent('VALIDATE_DNS_INPUT', 'Request validation failed - environment is not available in the request', context_map)
            println "environment is not available"
            throw new Exception("environment is not available")
        } 

        if  (props['endpointType'] == "") {
            eventsModule.sendFailureEvent('VALIDATE_DNS_INPUT', 'Request validation failed - endpoint_type is not available in the request', context_map)
            println "endpoint_type is not available"
            throw new Exception("endpoint_type is not available")
        }

        if  (props['endpoint'] == "") {
            eventsModule.sendFailureEvent('VALIDATE_DNS_INPUT', 'Request validation failed - endpoint is not available in the request', context_map)
            println "endpoint is not available"
            throw new Exception("endpoint is not available")

        } else {
            if (props['endpointType'] == 'api') {
                def endpointAsset = props['endpoint']
                endpointAsset = endpointAsset.split("/");
                endpointAsset = endpointAsset[0]+endpointAsset[1]+endpointAsset[2]
                endpointAsset = endpointAsset.replace(/:/,"://");
                def domain = endpointAsset;
                if (domain.startsWith('http://')) {
                    domain = domain - 'http://'
                }
                else if (domain.startsWith('https://')) {
                    domain = domain - 'https://'
                }
                def apiId = domain.tokenize(".")[0]
                JSON.setValueToPropertiesFile("apiId", apiId)
                JSON.setValueToPropertiesFile("endpointAsset", endpointAsset)
            } else {
                def endpointAsset = props['endpoint']
                JSON.setValueToPropertiesFile("endpointAsset", endpointAsset)
            }
        }

        if (props['fqdn'] == "") {
            eventsModule.sendFailureEvent('VALIDATE_DNS_INPUT', 'Request validation failed - fqdn is not available in the request', context_map)
            println "fqdn is not available"
            throw new Exception("fqdn is not available")
        }

        eventsModule.sendCompletedEvent('VALIDATE_DNS_INPUT', 'Request validation completed', context_map)

    } catch(ex) {
        println "Pipeline parameters are not valid: " + ex
        throw new Exception("Pipeline parameters are not valid: ", ex)
    }
}

/*
* Gets target deployment_account information for the environment.
*/
def getEnvironmentDeploymentAccount() {
    println "initModule.groovy: getEnvironmentDeploymentAccount"

    def envModule = new EnvironmentDeploymentMetadataLoader()
    def environmentInfo =  envModule.getEnvironmentInfo()
    return environmentInfo['deployment_accounts']
}