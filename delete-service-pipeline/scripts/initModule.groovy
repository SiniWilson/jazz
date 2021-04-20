#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.Yaml as YAML
import common.util.File as FILE
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

    def env = System.getenv()
    
    JSON.setValueToPropertiesFile("serviceName", env.SERVICE_NAME)
    JSON.setValueToPropertiesFile("serviceDomain", env.DOMAIN)
    JSON.setValueToPropertiesFile("environmentId", env.ENVIRONMENT_ID)
    if(env.USER) {
        JSON.setValueToPropertiesFile("USER", env.USER)
    }
}

/*
* Function to initialize and setup all the modules
*/
def initializeModules() {
    println "initModule.groovy: initializeModules"

    def utilModule = new UtilityModule()
    utilModule.showDeleteEnvParams()

    def loginModule = new Login()
    def configLoaderModule = new ConfigLoader()
    def serviceMetadaLoader = new ServiceMetadataLoader()
    def env = System.getenv()
    def eventsModule = new EventsModule()
    /*
    * Initializing and setting up the modules
    */
    try {
        loginModule.getAuthToken()
        configLoaderModule.getConfigData()
        initialize()
        serviceMetadaLoader.loadServiceMetaData()

        LoadConfigs()
    } catch(ex) {
        /*
        * Sending DELETE FAILED event only in case of service deletion
        */
        /*
        * Handling login failure, service metadata failure events
        */
        if(env.ENVIRONMENT_ID == null) {
            JSON.setValueToPropertiesFile("environmentId", "NA")
        }
        println "Something went wrong: " + ex
        eventsModule.sendFailureEvent('INITIALIZE_DELETE_WORKFLOW', ex.message)
    }
}

/*
* Function to initialize and load configurations
*/
def LoadConfigs() {
    println "initModule.groovy: LoadConfigs"

    def jobContext = [:]
    def eventsModule
    def eventName = 'INITIALIZE_DELETE_WORKFLOW'
    def slackModule = new SlackModule()
    def env = System.getenv()
    
    if(env.ENVIRONMENT_ID != null){
        jobContext['ENVIRONMENT_ID'] = env.ENVIRONMENT_ID
    }
    try{
        def repoBaseUrl = env['REPO_BASE_URL']
        def serverlessConfigRepo = env['SERVERLESS_CONFIG_REPO']

        eventsModule = new EventsModule()
        eventsModule.sendStartedEvent(eventName)
        
        slackModule.sendSlackNotification(eventName, null, 'STARTED', jobContext)
        
        println "Getting service details.."
        getServiceDetails()

        def repoName = JSON.getValueFromPropertiesFile('scmRepoName')
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def runtime = serviceConfig.runtime
        
        //check if repo exists and store it in properties file
        def gitCreds = "${env.GITLAB_SVC_ACCT_USER}:${env.GITLAB_SVC_ACCT_PASSWORD}@"
        def repoUrl = "https://" + gitCreds + serviceConfig.repository.substring(8)
        def repoExists;
        def pingRepo = sh("git ls-remote --exit-code -h ${repoUrl} >/dev/null 2>&1;echo \$?")
        if (pingRepo.indexOf('0') > -1) {
            repoExists = true;
        } else {
            repoExists = false;
        }
        JSON.setValueToPropertiesFile('repoExists', repoExists)

        //Skip cloning repo in case BYORepo doesn't exist
        if (repoExists) {
            println "****** Cloning user repository: ${serviceConfig.repository} *****"
            cloneUserRepo(repoName, serviceConfig.repository)
        } else { //create empty project directory
            println "****** Repository: ${serviceConfig.repository} doesn't exist. It might have already been deleted. *****"
        }
        loadUserDefinedConfigs()

        if(serviceConfig.type == "api" || serviceConfig.type == "function") {
            println "***** Cloning serverless config repo and replacing appropriate file as per runtime *****"
            loadServerlessRepo(serverlessConfigRepo)
            loadServerlessConfig(runtime, repoName, jobContext)

            println "**** Update deployment config ****"
            updateServiceNameConfig(serviceConfig, repoName, jobContext)
        }

        if(serviceConfig.type == "api") {
            println "**** updateSwaggerConfig ****"
            updateSwaggerConfig(repoName, serviceConfig, jobContext)
        }
        
        eventsModule.sendCompletedEvent(eventName)
        eventName = 'VALIDATE_PRE_BUILD_CONF'
        validateServiceInputs(eventName, jobContext)

    } catch(ex) {
        /*
        * Sending DELETE FAILED event only in case of service deletion
        */
        if(env.ENVIRONMENT_ID == null) {
            JSON.setValueToPropertiesFile("environmentId", "NA")
        }
        println "Something went wrong: " + ex
        eventsModule.sendFailureEvent(eventName, ex.message)
        jobContext['EVENT'] = 'INITIALIZE_DELETE_WORKFLOW'
        jobContext['Error Message'] = 'Invalid project configuration'
        slackModule.sendSlackNotification(eventName, ex.message, 'FAILED', jobContext)
        throw new Exception("Something went wrong: ", ex)
    } finally {
        // Required at other stages
        JSON.setValueToPropertiesFile('jobContext', jobContext)
    }
}

/*
* Get service details like scmUrl, scmManage, etc.
*/
def getServiceDetails() {
    println "initModule.groovy: getServiceDetails"

    try {
        def configData = JSON.getValueFromPropertiesFile('configData')
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def scmManaged = serviceConfig.scmManaged;
        def serviceType = serviceConfig.type;

        if(serviceType.equals('api')){
            JSON.setValueToPropertiesFile("flowType", "API")
        } else if (serviceType.equals('function')) {
            JSON.setValueToPropertiesFile("flowType", "LAMBDA")
        } else if (serviceType.equals('website')) {
            JSON.setValueToPropertiesFile("flowType", "WEBSITE")
        } else if (serviceType.equals('sls-app')) {
            JSON.setValueToPropertiesFile("flowType", "SLSAPP")
        } else {
            println "Invalid project configuration"
            throw new Exception('Invalid project configuration')
        }
        
        def scmGitUrl = serviceConfig.repository

        if(scmGitUrl.contains('[Archived]')){
            prepareRepoDetails()
        } else {
            JSON.setValueToPropertiesFile("deleteRepo", true)
            scmGitUrl = scmGitUrl.replaceAll("\\w+@",'') // get rid of usernames if any. Ex: https://user@gitlab.com/repo.git => https://gitlab.com/repo.git
            def scmUrlLength = scmGitUrl.split('/')
            JSON.setValueToPropertiesFile("massagedScmGitUrl", scmGitUrl)

            def repoName = scmUrlLength[scmUrlLength.length - 1].split('\\.git')[0]
            JSON.setValueToPropertiesFile("scmRepoName", repoName)
            
            def scmHostname = scmGitUrl.replaceAll('(.*)://?(.*?)/(.*)', '$2') // interested in hostname. Ex: https://gitlab.com/repo.git => gitlab.com
            
            def scmType = serviceConfig.scmType
            if (configData.BYOR[scmType][scmHostname]) {
                def scmManagedRepoCredentialId = configData.BYOR[scmType][scmHostname].credentialId
                JSON.setValueToPropertiesFile("scmManagedRepoCredentialId", scmManagedRepoCredentialId)
            } else {
                if (scmManaged) {
                    println "Missing configurations for scmType: ${scmType} & scmHostname: ${scmHostname}. Cannot proceed forward for scmManaged services."
                    throw new Exception("Missing configurations for scmType: ${scmType} & scmHostname: ${scmHostname}. Cannot proceed forward for scmManaged services.")
                } else {
                    println "Missing configurations for scmType: ${scmType} & scmHostname: ${scmHostname}."
                }
            }
        }
        
    } catch(ex) {
        println "Something went wrong in getting service details: " + ex
        throw new Exception("Something went wrong in getting service details", ex)
    }
}

/*
* Function to prepare repo details manually in case repository is not avaialable or archived
*/
def prepareRepoDetails() {
    println "initModule.groovy: prepareRepoDetails"

    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    /*
    * creating the repoName and subdirectory with it
    */
    def repoName = serviceConfig['domain'] + '_' + serviceConfig['service']
    def resourcePath = "/" + serviceConfig['domain'] + "/v1/" + serviceConfig['service']
    sh("mkdir -p ${PROPS.WORKING_DIRECTORY}/${repoName}")

    JSON.setValueToPropertiesFile("deleteRepo", false)
    JSON.setValueToPropertiesFile("scmRepoName", repoName)
    JSON.setValueToPropertiesFile("resourcePath", resourcePath);
}

/**
* Replace the service name & Domain place holders in swagger file.
* @param  repoName
* @param  serviceConfig
* @return
*/
def updateSwaggerConfig(repoName, serviceConfig, jobContext) {
    println "initModule.groovy: updateSwaggerConfig"

    def eventsModule = new EventsModule()
    def slackModule = new SlackModule()
    eventsModule.sendStartedEvent('UPDATE_SWAGGER')
    try{
        def serviceName = serviceConfig.service
        def domain = serviceConfig.domain
        def isSwagger = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
        println "isSwagger: $isSwagger"

        if(isSwagger) {
            sh("sed -i -- 's/{service_name}/$serviceName/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
            sh("sed -i -- 's/{domain}/$domain/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
            println "successfully ran updateSwaggerConfig"
        }
        eventsModule.sendCompletedEvent('UPDATE_SWAGGER')
    } catch(ex) {
        println "Something went wrong in updateSwaggerConfig: " + ex
        eventsModule.sendFailureEvent('UPDATE_SWAGGER', ex.message)
        jobContext['EVENT'] = 'UPDATE_SWAGGER'
        jobContext['Error Message'] = 'updateSwaggerConfig Failed. ' + ex.message
        slackModule.sendSlackNotification('UPDATE_SWAGGER', ex.message, 'FAILED', jobContext)
        throw new Exception("Something went wrong in updateSwaggerConfig", ex)
    }
}

/*
* Function to clone user repository
* @param  repoName
* @param  repoUrl
*/
def cloneUserRepo(repoName, repoUrl) {
    def env = System.getenv()
    println "initModule.groovy: cloneUserRepo"

    def deleteRepo = JSON.getValueFromPropertiesFile('deleteRepo')
    def gitCreds = "${env.GITLAB_SVC_ACCT_USER}:${env.GITLAB_SVC_ACCT_PASSWORD}@"
    // remove https:// i.e. 8 characters from repoUrl and put the gitCreds there
    repoUrl = "https://" + gitCreds + repoUrl.substring(8)

    def eventsModule = new EventsModule()
    eventsModule.sendStartedEvent('GET_SERVICE_CODE')
    if(deleteRepo) {
        try{
            sh("git clone -b master --depth 1 ${repoUrl} ${PROPS.WORKING_DIRECTORY}/${repoName}")
            println "Successfully cloned upstream(user) repo"
            eventsModule.sendCompletedEvent('GET_SERVICE_CODE')
        } catch(ex) {
            prepareRepoDetails()

            println "Cloning user repo failed " + ex
            eventsModule.sendFailureEvent('GET_SERVICE_CODE', ex.message)
        }
    } else {
        println "User repo is archived"
        eventsModule.sendCompletedEvent('GET_SERVICE_CODE')
    }
}

/*
* Loading user defined configurations form deployment-env.yml file of the user repo
*/
def loadUserDefinedConfigs() {
    println "initModule.groovy: loadUserDefinedConfigs"

    def repoName = JSON.getValueFromPropertiesFile('scmRepoName')
    def prop = [:]
    def resultList = []
    /*
    * handle the case where the codebase doesn't exists or is not a jazz project
    */
    def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml")
    if (fExists) {
        println "deployment yaml exists"
        resultList = new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml").readLines()
    }
    
    def cleanedList = []
    for (i in resultList) {
        if (!i.toLowerCase().startsWith('#')) {
            cleanedList.add(i)
        }
    }
    for (item in cleanedList) {
        item = item.replaceAll(' ', '').replaceFirst(':', '#');
        def eachItemList = item.tokenize('#')
        if (eachItemList[0] && eachItemList[0].trim() && eachItemList[1] && eachItemList[1].trim()) {
            prop.put(eachItemList[0].trim(), eachItemList[1].trim())
        }
    }   

    println "Loaded configurations => $prop"
    JSON.setValueToPropertiesFile('userDefinedConfig', prop)
}

/*
* Function to clone serverless config repository
* @param  serverlessConfigRepo
*/
def loadServerlessRepo(serverlessConfigRepo) {
    println "initModule.groovy: loadServerlessRepo"

    def eventsModule = new EventsModule()
    eventsModule.sendStartedEvent('GET_SERVERLESS_CONF')
    try{
        println "cleaning serverlessConfigRepo in working directory"
        sh("rm -rf ${PROPS.WORKING_DIRECTORY}/serverlessConfigRepo")

        println "Cloning serverlessConfigRepo"
        sh("git clone -b master --depth 1 ${serverlessConfigRepo} ${PROPS.WORKING_DIRECTORY}/serverlessConfigRepo")

        eventsModule.sendCompletedEvent('GET_SERVERLESS_CONF')
    } catch(ex) {
        println "Cloning serverlessConfigRepo failed " + ex
        eventsModule.sendFailureEvent('GET_SERVERLESS_CONF', ex.message)
        throw new Exception("Cloning serverlessConfigRepo failed ", ex)
    }
}

/**
 * Update the service name in serverless config file
 * @param  serviceConfig
 * @param  repoName
 * @return
 */
def updateServiceNameConfig(serviceConfig, repoName, jobContext) {
    println "initModule.groovy: updateServiceNameConfig"

    def eventsModule = new EventsModule()
    def slackModule = new SlackModule()
    def envModule = new EnvironmentDeploymentMetadataLoader();
    eventsModule.sendStartedEvent('UPDATE_DEPLOYMENT_CONF')
    try{
        println "sets CF Stack name as service--domain-env"
        def environmentLogicalId = envModule.getEnvironmentLogicalData() ? envModule.getEnvironmentLogicalData() : ""
        def serviceName = serviceConfig.service
        def domain = serviceConfig.domain
        def cfStackName = "$serviceName" + '--' + "$domain"

        def tags = "$domain" + '_' + "$serviceName" + '_' + "$environmentLogicalId"
        println "tags: $tags"

        sh("sed -i -- 's/service: \${file(deployment-env.yml):service}/service: $cfStackName/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        sh("sed -i -- 's/name: \${self:domain}_\${self:serviceTag}_\${self:custom.myStage}/name: $tags/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        println "successfully ran updateServiceNameConfig"
        eventsModule.sendCompletedEvent('UPDATE_DEPLOYMENT_CONF')
    } catch(ex) {
        println "updateServiceNameConfig failed " + ex
        eventsModule.sendFailureEvent('UPDATE_DEPLOYMENT_CONF', ex.message)
        jobContext['EVENT'] = 'UPDATE_DEPLOYMENT_CONF'
        jobContext['Error Message'] = 'updateServiceNameConfig Failed. ' + ex.message
        slackModule.sendSlackNotification('UPDATE_DEPLOYMENT_CONF', ex.message, 'FAILED', jobContext)
        throw new Exception("updateServiceNameConfig failed", ex)
    }
}

/**
* Load the serverless configuration file from SCM based on the runtime.
* @param  runtime
* @param  repoName
* @return
*/
def loadServerlessConfig(runtime, repoName, jobContext){
    println "initModule.groovy: loadServerlessConfig"

    def slackModule = new SlackModule()
    try {
        sh("touch ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        if (runtime.indexOf('nodejs') > -1) {
            sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-nodejs.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        } else if (runtime.indexOf('java') > -1) {
            sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-java.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        } else if (runtime.indexOf('python') > -1) {
            sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-python.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        } else if (runtime.indexOf('go') > -1) {
            sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-go.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        }
        removeEventResources(repoName)
    } catch(ex) {
        println "loadServerlessConfig failed " + ex
        jobContext['EVENT'] = 'GET_SERVERLESS_CONF'
        jobContext['Error Message'] = 'loadServerlessConfig Failed. ' + ex.message
        slackModule.sendSlackNotification('GET_SERVERLESS_CONF', ex.message, 'FAILED', jobContext)
        throw new Exception("loadServerlessConfig failed ", ex)
    }
}

def removeEventResources(repoName) {
    sh("sed -i -- '/#Start:resources/,/#End:resources/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}

/**
* Validate service inputs
* @return
*/
def validateServiceInputs (eventName, jobContext) {
    println "initModule.groovy:validateServiceInputs"

    def slackModule = new SlackModule()
    def envModule = new EnvironmentDeploymentMetadataLoader();
    def eventsModule = new EventsModule()
    try{
        eventsModule.sendStartedEvent(eventName, 'Input validation')

        println "Validating environments"
        envModule.getEnvironmentGuids()
        validateEnvironments(jobContext)

        def serviceName = JSON.getValueFromPropertiesFile('serviceName')
        def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
        if(!serviceName) {
            println "No Service Name present"
            jobContext['EVENT'] = 'VALIDATE_PRE_BUILD_CONF'
            jobContext['Error Message'] = 'Service Name is invalid'
            slackModule.sendSlackNotification('VALIDATE_PRE_BUILD_CONF', 'Service Name is invalid', 'FAILED', jobContext)
            throw new Exception("No Service Name present")
        }
        if(!serviceDomain) {
            println "No Service Domain present"
            jobContext['EVENT'] = 'VALIDATE_PRE_BUILD_CONF'
            jobContext['Error Message'] = 'Domain Name is invalid'
            slackModule.sendSlackNotification('VALIDATE_PRE_BUILD_CONF', 'Domain Name is invalid', 'FAILED', jobContext)
            throw new Exception("No Service Domain present")
        }

        eventsModule.sendCompletedEvent(eventName, 'Valid inputs')
    } catch(ex) {
        println "Something went wrong while validating service inputs: " + ex
        eventsModule.sendFailureEvent(eventName, ex.message)
        throw new Exception("Something went wrong while validating service inputs: ", ex)
    }
}

/**
* Validate environment logical Ids
* @return
*/
def validateEnvironments (jobContext) {
    println "In initModule.groovy:validateEnvironments"
    
    def slackModule = new SlackModule()
    def envModule = new EnvironmentDeploymentMetadataLoader();

    /*
    * Incoming Environment guid parameter
    */
    def environmentIds = JSON.getValueFromPropertiesFile('environmentId')
    /*
    * Total environment list for the service
    */
    def environmentList = JSON.getValueFromPropertiesFile('environmentIdList')

    def environmentArray  = []
    if (environmentIds) {
        environmentArray.push(environmentIds)
    }
    /*
    * If env parameter has environmentId -> environment deletion
    * populate with env guid
    */
    if (environmentIds && environmentIds != 'null' && environmentArray.size() > 0) {
        /*
        * validate if each envId is in environment catalog
        */
        for (_eId in environmentArray) {
            if (!environmentList.contains(_eId)) {
                jobContext['EVENT'] = 'VALIDATE_PRE_BUILD_CONF'
                jobContext['Error Message'] = "Unable to find the environment id $_eId from environment catalog"
                slackModule.sendSlackNotification('VALIDATE_PRE_BUILD_CONF', "Unable to find the environment id $_eId from environment catalog", 'FAILED', jobContext)
                throw new Exception("Unable to find the environment id $_eId from environment catalog")
            }
        }
        JSON.setValueToPropertiesFile("environmentArray", environmentArray)
    } 
    /*
    * If env parameter doesn't has any environmentId -> service deletion
    * populate all environment for the service
    */
    else if (environmentList.size() > 0) {
        JSON.setValueToPropertiesFile("environmentArray", environmentList)
    } 
    else if (environmentList.size() == 0) {
        println "WARN: service doesn't contain any active environments in environment catalog!"
        JSON.setValueToPropertiesFile("environmentArray", environmentArray)
    }
}
