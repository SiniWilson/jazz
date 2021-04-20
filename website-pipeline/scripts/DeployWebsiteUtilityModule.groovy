#!groovy?
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.text.*
import groovy.json.JsonOutput
import common.util.Shell as ShellUtil
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.File as FILE
import common.util.Yaml as YAML
import common.util.Status as Status
import static common.util.Shell.sh as sh
import java.lang.*


static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

def initialize () {
    println "Initializing website pipeline."
    
    def utilModule = new UtilityModule()
    utilModule.showServiceEnvParams()

    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    try {   
        validatePipelineTriggerParams()
        cloneUpstreamService(events, environmentDeploymentMetadata)
        println "============================================================"
        println "=              CHECK FOR USER PIPELINE                     ="
        println "============================================================"
        try {
            utilModule.checkIfUserPipeline();
        } catch (ex) {
            events.sendFailureEvent('UPDATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap("failed"));
            throw new Exception(ex.message);
        }
        println "============================================================"
        println "              PRE BUILD VALIDATION                          "
        println "============================================================"

        preBuildValidation()
        
    } catch (ex) {
        println "Exception occured while initializing the website pipeline: + $ex"
        throw new Exception("Exception occured while initializing the website pipeline", ex)
    }
}


def configDeployment() {
    println "DeployWebsiteUtilityModule.groovy:configDeployment"
    try {
        def events = new EventsModule();
        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
        def qcpModule = new QcpModule()
        def slack = new SlackModule()
        def env = System.getenv()

        events.sendStartedEvent('UPDATE_DEPLOYMENT', "configDeployment", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))

        println "============================================================"
        println "                    DEPLOY TO AWS                                "
        println "============================================================"
        
        deploy()

        def archiveEnvironmentId = JSON.getValueFromPropertiesFile('archiveEnvironmentId')

        if(archiveEnvironmentId && shouldTriggerArchivalPipeline()) {
            println "============================================================"
            println "               REQUEST ENVIRONMENT ARCHIVAL                 "
            println "============================================================"

            requestEnvironmentArchival(events, qcpModule, slack)
        }

        println "============================================================"
        println "                        END                                 "
        println "============================================================"

    } catch (ex) {
        println "Exception occured in configDeployment: $ex"
        throw new Exception("Exception occured while initializing the website pipeline", ex)
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
* Function to determine whether to trigger archival pipeline or not
*/
def shouldTriggerArchivalPipeline() {
    println "DeployWebsiteUtilityModule.groovy:shouldTriggerArchivalPipeline"

    try {
        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
        def result = false;
        def diffAccount = false;
        /*
        * Getting new and old environment logical ID
        */
        def archiveEnvironmentId = JSON.getValueFromPropertiesFile('archiveEnvironmentId')
        def environmentInfo = environmentDeploymentMetadata.getEnvironmentInfo()
        def envLogicalId = environmentInfo["logical_id"]
        /*
        * Getting new and old environment info for deployment accounts
        */
        def latestdeploymentAccounts
        if(environmentInfo && environmentInfo["metadata"] && environmentInfo["metadata"]["archived_environments"]) {
            def archivedEnvironments =  environmentInfo["metadata"]["archived_environments"]
            archivedEnvironments = sort(archivedEnvironments)
            def keys = archivedEnvironments.keySet() as List
            def oldEnvInfo = archivedEnvironments[keys[0]]
            if (oldEnvInfo.status == 'pending_archival') {
                latestdeploymentAccounts = oldEnvInfo.deployment_accounts
            }
            def currentDeploymentAccount = environmentInfo.deployment_accounts
            if(latestdeploymentAccounts && (latestdeploymentAccounts[0].account != currentDeploymentAccount[0].account)) {
                diffAccount = true
            }
        }
        /*
        * To handle scenario when region is changed for same environment
        */
        if(archiveEnvironmentId != envLogicalId || diffAccount) {
            result = true;
        }
        return result;
    } catch(ex) {
        println "Failed to determine whether to trigger archival pipeline or not"
        ex.printStackTrace()
        throw new Exception("Failed to determine whether to trigger archival pipeline or not", ex)
    }
}


def validatePipelineTriggerParams() {
    println "DeployWebsiteUtilityModule.groovy:validatePipelineTriggerParams"
    def env = System.getenv()
    if(!env['REPO_URL']) {
        println "REPO_URL is not come as part of the request."
        throw new Exception("REPO_URL is not come as part of the request.");
    }
    if(!env.REPO_BRANCH) {
        println "Repo branch is not come as part of the request."
        throw new Exception("Repo branch is not come as part of the request.");
    }
    if(!env.REPO_NAME) {
        println "Repo name is not come as part of the request."
        throw new Exception("Repo name is not come as part of the request.");
    }   
    if(!env.REQUEST_ID) {
        println "REQUEST_ID is not come as part of the request. Setting it here."
        //Setting request id
        def requestId = sh("uuidgen -t") 
        JSON.setValueToPropertiesFile('REQUEST_ID', requestId.trim())
    } else {
        JSON.setValueToPropertiesFile('REQUEST_ID', env.REQUEST_ID)
    }
}

def cloneUpstreamService(events, environmentDeploymentMetadata) {
    println "DeployWebsiteUtilityModule.groovy:cloneUpstreamService"
   try {
        def qcpModule = new QcpModule()
        def env = System.getenv()
        def commitSha = env.COMMIT_SHA
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')        
        def repoInfo = serviceConfig.repository.split(".*://")
        println "Repo Info: ${repoInfo}"
        def repository = "https://${env.GITLAB_SVC_ACCT_USER}:${env.GITLAB_SVC_ACCT_PASSWORD}@${repoInfo[1]}"
        //Setting repo branch to make parity with all other build packs
        JSON.setValueToPropertiesFile('repoBranch', env.REPO_BRANCH)
        if (env.REPO_BRANCH == 'master') environmentDeploymentMetadata.setEnvironmentLogicalId('stg')
        def environmentId = environmentDeploymentMetadata.getEnvironmentId()
        qcpModule.initialize()
        println "Cloning the upstream repo"
        sh("git clone -b ${env.REPO_BRANCH}  ${repository}   ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}")
        if(!commitSha) commitSha = sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};git rev-parse HEAD" )
        JSON.setValueToPropertiesFile('commitSha', commitSha)
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started)) 
        if (!env.REQUEST_ID || (serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ) {        
            events.sendStartedEvent('CREATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started)) 
        } else {
            events.sendStartedEvent('UPDATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started)) 
        }
   } catch(ex) {
       events.sendFailureEvent('UPDATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
       println "loadDeploymentConfigurations failed- $ex"
       throw new Exception("loadDeploymentConfigurations failed:- ", ex)
   }   
}

def preBuildValidation() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    println "DeployWebsiteUtilityModule.groovy:preBuildValidation"
    events.sendStartedEvent("VALIDATE_PRE_BUILD_CONF") 
    try {
        checkEnvironmentConfiguration(environmentDeploymentMetadata)
        checkDeploymentConfiguration(environmentDeploymentMetadata)
        loadServiceConfiguration()
        events.sendCompletedEvent("VALIDATE_PRE_BUILD_CONF")
    } catch (ex) {
        println "preBuildValidation failed: $ex"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("VALIDATE_PRE_BUILD_CONF", trimMessage(ex.message), "FAILED") 
        events.sendFailureEvent("VALIDATE_PRE_BUILD_CONF")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "Prebuild validation failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("preBuildValidation failed: ", ex)    
    }
}

def loadServiceConfiguration() {
    try {
        def serviceConfigDataLoader = new ServiceConfigurationDataLoader()
        serviceConfigDataLoader.loadServiceConfigurationData()        
    } catch (ex) {
        println "loadServiceConfiguration failed: ${ex.message}"
        throw new Exception("loadServiceConfiguration failed:", ex)
    }
}


def checkEnvironmentConfiguration(environmentDeploymentMetadata) {
    println "DeployWebsiteUtilityModule.groovy:preBuildValidation"
    def qcpModule = new QcpModule()
    try {
        def env = System.getenv()
        def environmentLogicalId
        def archiveEnvironmentId = false

        if(env.REPO_BRANCH == 'master') {
            // preparing for staging deployment
            environmentLogicalId = 'stg'
            if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.stg.yml")){
                sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.stg.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml") 
            }
            else if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")){
                // Keep a copy for use during production deployment
                sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deploymentEnvOriginal.yml") 
            }
        } else {
            environmentLogicalId = environmentDeploymentMetadata.getEnvironmentLogicalId () 
        }

        //Checking environment Id is set or not for dev branch
        if (!environmentLogicalId || environmentLogicalId == null || environmentLogicalId == '') {            
            throw new Exception("The environment is not set for the repoBranch: ${env.REPO_BRANCH}, please set them by logging into Jazz UI.")
        }
        //sending qcp notifiaction for start
        qcpModule.sendQCPEvent("Pre", "success")
        //Checking environment logical id has change or not for archiving the assets
        if (env.REPO_BRANCH != 'master') {
            println "Getting changed environment logical id"
            environmentDeploymentMetadata.getLatestEnvironmentLogicalIdPendingArchival()
        }
    } catch (ex) {
        println "checkEnvironmentConfiguration failed: $ex"
        throw new Exception("checkEnvironmentConfiguration failed:", ex)
    }
    
}

def checkDeploymentConfiguration (environmentDeploymentMetadata) {
    println "DeployWebsiteUtilityModule.groovy:checkDeploymentConfiguration"    
    try {
        //Checking deployment account information for environments
        def environmentInfo = environmentDeploymentMetadata.getEnvironmentInfo()
        def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
        def deploymentAccount = environmentInfo.deployment_accounts
        if (!deploymentAccount || deploymentAccount.isEmpty()) {
            println "Deployment account information is not set for the environment: $environmentLogicalId, please set them by logging into Jazz UI."
            throw new Exception("Deployment account information is not set for the environment: $environmentLogicalId, please set them by logging into Jazz UI.")
        } else {
            //Getting user defined informations
            loadUserDefinedConfigs()
            checkDeploymentTarget()        
        }
    } catch (ex) {
        println "checkDeploymentConfiguration failed: $ex"
        throw new Exception("checkDeploymentConfiguration failed", ex)
    }
}


/*
* Set runtime in service config
*/
def setServiceRuntime(runtime){
    println "In DeployWebsiteUtilityModule.groovy:setServiceRuntime"
    serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    serviceConfig['runtime'] = runtime
    JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)
}

/*
* Set account in service config
*/
def setServiceAccount(account){
    println "In DeployWebsiteUtilityModule.groovy:setServiceAccount"
    serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    serviceConfig['account'] = account
    JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)
}

/*
* Set region in service config
*/
def setServiceRegion(region){
    println "In DeployWebsiteUtilityModule.groovy:setServiceRegion"
    serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    serviceConfig['region'] = region
    JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)
}

def codeQualityCheck() {
    println "DeployWebsiteUtilityModule.groovy:codeQualityCheck"
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    events.sendStartedEvent("CODE_QUALITY_CHECK") 
    try {
        def runtime = "n/a" // Prevents code break at other places where runtime is a required field.
        setServiceRuntime(runtime)
        def projectKey = 'jazz'
        def env = System.getenv()        
        def sonar = new SonarModule()
        def fortifyScan = new FortifyScanModule()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "codeQualityCheck", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        sonar.configureForProject(env.REPO_BRANCH, projectKey)
        sonar.doAnalysis()
        fortifyScan.doScan(projectKey)
        
        events.sendCompletedEvent("CODE_QUALITY_CHECK")
    } catch (ex) {
        println "preBuildValidation failed: $ex"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("CODE_QUALITY_CHECK", trimMessage(ex.message), "FAILED")
        events.sendFailureEvent("CODE_QUALITY_CHECK")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "codeQualityCheck failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("codeQualityCheck failed: ", ex)    
    }   
}

//Loading user defined configurations form deployment-env.yml file of the user repo
def loadUserDefinedConfigs() {
    println "DeployWebsiteUtilityModule.groovy:loadUserDefinedConfigs"
    def env = System.getenv()
    def prop = [:]
    def resultList = []
    // handle the case where the codebase doesn't exists or is not a jazz project
    def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
    if (fExists) {
        println "deployment-env.yml exists in user repository!"
        resultList = new java.io.File("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml").readLines()
        prop = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
        // if prop is null set it to empty map
        if (!prop){
            prop = [:]
        }
        println "prop: $prop"
    }

    def metadata = JSON.getValueFromPropertiesFile('serviceMetadata')
    //add metadata to deployment-env, for reference in serverless.yml
    metadata.each{k, v ->
        if (prop[k] == null || prop[k] == "") {
            sh("echo '\n${k}: ${v}\n' >> ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
        }
    }

    println "Loaded configurations => $prop"
    JSON.setValueToPropertiesFile('userDefinedConfig', prop)
}

def checkDeploymentTarget() {
    println "DeployWebsiteUtilityModule.groovy:checkDeploymentTarget"
    def props = JSON.getAllProperties() 
    def environmentInfo = props['environmentInfo']
    def environmentLogicalId = props['environmentLogicalId']
    def deploymentAccount = environmentInfo['deployment_accounts']
    if(!deploymentAccount.isEmpty()) {        
        if (deploymentAccount[0].containsKey('region') && deploymentAccount[0].containsKey('account')) {
            //Getting the required deployment account and region information
            JSON.setValueToPropertiesFile('deploymentAccount', deploymentAccount[0].account)
            JSON.setValueToPropertiesFile('deploymentRegion', deploymentAccount[0].region)  
            

            def utilModule = new UtilityModule();
            props['serviceConfig']['account'] = deploymentAccount[0].account
            setServiceAccount(deploymentAccount[0].account)
            props['serviceConfig']['region'] = deploymentAccount[0].region
            setServiceRegion(deploymentAccount[0].region)
            def accountDetails = utilModule.getAccountInfo();
            JSON.setValueToPropertiesFile('accountDetails', accountDetails)
            def userDefinedConfig = props['userDefinedConfig']
            validateTemplates()
        } else {
            throw new Exception("Deployment account information is not valid for the environment: ${environmentLogicalId}, please set them by logging into Jazz UI.")
        }
    } else {
        throw new Exception("Deployment account information is not valid for the environment: ${environmentLogicalId}, please set them by logging into Jazz UI.")
    }
}

def validateTemplates() {
    println "DeployWebsiteUtilityModule.groovy:validateTemplates"
    def userDefinedConfig = JSON.getValueFromPropertiesFile('userDefinedConfig')
    def serviceConfig = JSON.getValueFromPropertiesFile("serviceConfig")
    def websiteType = getWebsiteType(userDefinedConfig)
    println "websiteType is $websiteType"
    if (serviceConfig['framework'] == 'angular') {
        validateAngularTemplate();
    } else if (serviceConfig['framework'] == 'react') {
        validateReactTemplate();
    } else if (serviceConfig['framework'] == 'static' || websiteType == 'static'){
        validateBasicTemplate();
    }
}

/**
 * Validate template for all necessary files. Ex: index.html
 */
def validateBasicTemplate() {
    println "DeployWebsiteUtilityModule.groovy:validateBasicTemplate"
    def env = System.getenv()
    def repoName = env['REPO_NAME']
    if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/app/index.html")) {
        println "index.html is present"
    } else {
        throw new Exception("index.html is not available.")
    }
}


/**
  *    VALIDATE ANGULAR TEMPLATE
*/
def validateAngularTemplate() {
    println "DeployWebsiteUtilityModule.groovy:validateAngularTemplate"
    def env = System.getenv()
    def repoName = env['REPO_NAME']
    if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/app/package.json")) {
        println "package.json is present"
    } else {
        throw new Exception("package.json is not available.")
    }

    if(JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/app/angular.json") || JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/app/angular-cli.json")) {
        println "Angular / Angular-cli json available"
    } else {
        throw new Exception("angular / angular-cli json is not available.")
    }

    try {
        // def packageString = new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/app/package.json").readLines()
        def packageJson = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/app/package.json")
        def angularVersionString = packageJson.dependencies.'@angular/core'

        def version = angularVersionString.substring(1, angularVersionString.indexOf('.'))
        println "Angular Project version $version"
    } catch(ex) {
        throw new Exception("Error while checking version", ex)
    }
}


/**
  * VALIDATE REACT TEMPLATE
 */
def validateReactTemplate() {
    println "DeployWebsiteUtilityModule.groovy:validateReactTemplate"
    def env = System.getenv()
    def repoName = env['REPO_NAME']
    if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/app/package.json")) {
        println "package.json is present"
    } else {
        throw new Exception("package.json is not available.")
    }

    try {
        //def packageString = new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/app/package.json").readLines()
        def packageJson = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/app/package.json")
        def reactVersionString = packageJson.dependencies.react;
        def version = reactVersionString.substring(1, reactVersionString.indexOf('.'))

        def reactDomVersionString = packageJson.dependencies.'react-dom';
        def domVersion = reactDomVersionString.substring(1, reactDomVersionString.indexOf('.'))

        println "React Project version $version, Dom version $domVersion"
    } catch(ex) {
        throw new Exception("Error while checking version", ex)
    }
}

def productionDeploy() {
    def qcpModule = new QcpModule()
    def events = new EventsModule()
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def utilModule = new UtilityModule()
    def status = 'failed'
    try {    
        utilModule.showServiceEnvParams()
        // set the prod
        def environmentId = environmentDeploymentMetadata.getEnvironmentId()
        def approvalRequestRaisedTime = JSON.getValueFromPropertiesFile('approvalRequestRaisedTime')
        def currentTimeValue = System.currentTimeMillis();
        def diffTime = (currentTimeValue - approvalRequestRaisedTime)/60000
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def triggerPipeline = false;

        def approvalUpdateResponse = utilModule.approvalStatusUpdate(triggerPipeline);
        if(approvalUpdateResponse.equalsIgnoreCase("REJECTED")) {
            status = 'REJECTED'
            events.sendFailureEvent('UPDATE_DEPLOYMENT', 'Approval was rejected!', environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.approval_rejected))
            throw new Exception("Deployment approval request was rejected! Please deploy again!")
        }
        
        println "diffTime: $diffTime"
        if(diffTime > serviceConfig.approvalTimeOutInMins.toInteger()) {
            println "Approval timeout expired, please deploy again"

            events.sendFailureEvent('UPDATE_DEPLOYMENT', "Approval timeout has expired", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.approval_expired))
            throw new Exception("Production deployment approval request expired! Please deploy again!")
        }
        qcpModule.sendQCPEvent("Pre", "success")
        buildWebsite()
        deploy()
    } catch (ex) {
        println "Production deployment failed"
        if(status === 'REJECTED') {
            events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message, environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.approval_rejected))
        } else {
            events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message, environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        }
        throw new Exception("Production deployment failed", ex)
    }
}

def buildWebsite() {
    println "DeployWebsiteUtilityModule.groovy:Build"
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def events = new EventsModule()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def utilModule = new UtilityModule()
    def props = JSON.getAllProperties() 
    def environmentInfo = props['environmentInfo']
    def environmentLogicalId = props['environmentLogicalId']
    def accountDetails = props['accountDetails']
    def config = props['serviceConfig']

    def username = config['owner']
    def domain = config['domain']
    def applicationName = config['app_name']
    def service = config['service']
    def websiteType = getWebsiteType(props['userDefinedConfig'])
    def scmGitUrl = props['serviceConfig']['repository']
    def account = config['account']
    def region = config['region']
    originAccessIdentityValue = generateAccessIdentity(accountDetails)
    def credsId = null;
    def deploymentAccountCreds = null;
    def context_map = [created_by: username]
    def url    = null
    def env = System.getenv()
    def repoName = env['REPO_NAME']
    // To DO refactor
    if(environmentLogicalId == 'prod'){
        //qcpModule.sendQCPEvent("Pre", "success")
        //events.sendStartedEvent('CREATE_DEPLOYMENT', "Deployment creation event for ${environmentLogicalId} deployment", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        events.sendStartedEvent('UPDATE_ENVIRONMENT', 'Environment  status update event  for Production deployment', environmentDeploymentMetadata.generateEnvironmentMap("deployment_started"))
        events.sendStartedEvent('DEPLOY_TO_AWS', 'starts deploying website to AWS')
    }
    events.sendStartedEvent('UPDATE_DEPLOYMENT', "buildAndDeploy", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
    def cfArn = null
    // Build the Application
    events.sendStartedEvent('BUILD', 'Website build started')
    def buildScriptExists = false
    try {
        credsId = utilModule.generateCreds(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)
        /*
        * Getting temporary credentials for cross account role if not primary account
        */
        deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, env.AWS_DEFAULT_REGION);
        def customBuildExecuted = false
        buildScriptExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/build.website")

        // If user provides custom build script, attempt to build the app using the user provided script
        if (buildScriptExists) {
            println "Code contains custom build script. Will read and execute.."
            def buildScriptContent = new File("${PROPS.WORKING_DIRECTORY}/${repoName}/build.website").getText('UTF-8')
            //def buildScript = new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/build.website").readLines()
            def buildScript = "bash build.website >  ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-custom-script_${environmentLogicalId}.log 2>&1"
            println "Custom build script provided by user: ${buildScriptContent}"
            
            // Making variable "env" available to the user script
            def setEnv = "export env=${environmentLogicalId};cd ${PROPS.WORKING_DIRECTORY}/${repoName};sed -i '1 i\\set -e' build.website;"
            println "setEnv ${setEnv}"
            if (buildScriptContent && buildScriptContent.trim()){
                println "Using custom build script.."
                // add setEnv
                buildScript = setEnv + buildScript;
                println "================ CUSTOM BUILD START   ============="
                println sh(buildScript, true)
                println sh("cat ${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-custom-script_${environmentLogicalId}.log ")
                println "================= CUSTOM BUILD END  ==============="
                customBuildExecuted = true
            } else {
                println "Custom build script is empty, will attempt to build using platfrom build instructions.."
            }
        }

        if (!customBuildExecuted){
            println "Using platform build instructions.."
            buildApp(config['framework'], environmentLogicalId, repoName)
        }
        events.sendCompletedEvent('BUILD', 'Website build completed')
    } catch(ex) {
        events.sendFailureEvent('BUILD', trimMessage(ex.getMessage()), environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        events.sendFailureEvent('UPDATE_DEPLOYMENT',  trimMessage(ex.getMessage()), environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("BUILD", trimMessage(ex.message), "FAILED")
        if (buildScriptExists) {
            println "==================================================="
            buildOut = sh("cat ${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-custom-script_${environmentLogicalId}.log")
            printColour(buildOut)
        }
        throw new Exception("Error", ex)
    }
}

/** Run Test cases based on runtime
*/
def runUnitTestCases() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    events.sendStartedEvent("UNIT_TEST") 
    try {
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def userDefinedConfig = JSON.getValueFromPropertiesFile('userDefinedConfig')
        def websiteType = getWebsiteType(userDefinedConfig)

        def env = System.getenv()
        def repoName = env['REPO_NAME']

        // checking for framework/websiteType. If it exists, computing code coverage else not..
        if (serviceConfig['framework'] || !websiteType.isEmpty())
        {
            if((serviceConfig['framework'] && serviceConfig['framework'].indexOf("react") > -1) || websiteType.indexOf("react") > -1) {
                println "Run test cases for react website.."
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}/app;npm test -- --coverage > ../../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
            } else if((serviceConfig['framework'] && serviceConfig['framework'].indexOf("angular") > -1) || websiteType.indexOf("angular") > -1) {
                println "Run test cases for angular website.."
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}/app;npm run test -- --no-watch --code-coverage > ../../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
            }
            def sonar = new SonarModule()
            sonar.cleanUpWorkspace()
        }
        events.sendCompletedEvent("UNIT_TEST")
    } catch (ex) {
        println "runUnitTestCases failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("UNIT_TEST", ex.message, "FAILED")
        events.sendFailureEvent("UNIT_TEST")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "runUnitTestCases failed.", environmentDeploymentMetadata.generateDeploymentMap("failed"))
        throw new Exception("runUnitTestCases failed: ", ex)    
    }    
}

def deploy() {
    println "DeployWebsiteUtilityModule.groovy:deploy"
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def events = new EventsModule()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def props = JSON.getAllProperties() 
    def environmentInfo = props['environmentInfo']
    def environmentLogicalId = props['environmentLogicalId']
    def accountDetails = props['accountDetails']
    def config = props['serviceConfig']

    def username = config['owner']
    def domain = config['domain']
    def applicationName = config['app_name']
    def service = config['service']
    def websiteType = getWebsiteType(props['userDefinedConfig'])
    def scmGitUrl = props['serviceConfig']['repository']
    def account = config['account']
    def region = config['region']
    originAccessIdentityValue = generateAccessIdentity(accountDetails)
    credsId = null
    def context_map = [created_by: username]
    def url    = null
    def env = System.getenv()
    def repoName = env['REPO_NAME']
    // To DO refactor
    if(environmentLogicalId == 'prod'){
        events.sendStartedEvent('UPDATE_ENVIRONMENT', 'Environment  status update event  for Production deployment', environmentDeploymentMetadata.generateEnvironmentMap("deployment_started"))
        events.sendStartedEvent('DEPLOY_TO_AWS', 'starts deploying website to AWS')
    }
    def cfArn = null
    def deploymentAccountCreds = null;
    def utilModule = new UtilityModule()

    // Deploy the Application
    events.sendCompletedEvent('DEPLOY', 'Website deploy started')
    try {
        credsId = utilModule.generateCreds(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)
        /*
        * Getting temporary credentials for cross account role if not primary account
        */
        deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, region);

        awsDeploy(deploymentAccountCreds, events, environmentDeploymentMetadata, qcpModule, slack)
    } catch(ex) {
        events.sendFailureEvent('DEPLOY', trimMessage(ex.getMessage()), environmentDeploymentMetadata.generateDeploymentMap("failed"))
        events.sendFailureEvent('UPDATE_DEPLOYMENT',  trimMessage(ex.getMessage()), environmentDeploymentMetadata.generateDeploymentMap("failed"))
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("DEPLOY", trimMessage(ex.message), "FAILED")
        throw new Exception("Error", ex)
    }
}

def printColour(textOut) {
    def fg = 30
    def fgl = 39
    def bg = 46
    def bgl = 49
    def style = "${(char)27}[$fg;$bg"+"m"
    def stylel = "${(char)27}[$fgl;$bgl"+"m"
    println(style+"$textOut"+stylel)
}

def awsDeploy(credsId, events, environmentDeploymentMetadata, qcpModule, slack) {
    println "DeployWebsiteUtilityModule.groovy:awsDeploy"
    def ip = sh("curl ifconfig.co");
    println "Build agent IP: ${ip}";
    def utilModule = new UtilityModule()
    serviceMetadataLoader = new ServiceMetadataLoader()
    def env = System.getenv()
    def props = JSON.getAllProperties() 
    def environmentInfo = props['environmentInfo']
    def environmentLogicalId = props['environmentLogicalId']
    def accountDetails = props['accountDetails']
    def config = props['serviceConfig']
    def metadata = JSON.getValueFromPropertiesFile('serviceMetadata')

    def username = config['owner']
    def domain = config['domain']
    def service = config['service']
    def region = config['region']
    def envName = getEnvironmentTag(environmentLogicalId)
    def context_map = [created_by: username]
    def repoName = env['REPO_NAME']
    def isPublic = environmentInfo['is_public_endpoint']
    def originAccessIdentityValue = generateAccessIdentity(accountDetails)
    def userDefinedConfig = props['userDefinedConfig']
    def websiteType = getWebsiteType(userDefinedConfig)
    def configLoaderValue = props['configData']
    def gitCommitHash = env['COMMIT_SHA']
    // Deploy the application
    events.sendStartedEvent('DEPLOY_TO_AWS', 'starts deploying website to AWS')
    events.sendStartedEvent('UPDATE_ENVIRONMENT', 'Environment  status update event for ' + envName + ' deployment')
    serviceMetadataLoader.setDomain(config['domain'])
    serviceMetadataLoader.setService(config['service'])
    // Checking if bucket is allocated to this service..
    s3BucketName = serviceMetadataLoader.getS3BucketNameForService(credsId)
    println "S3 bucket allocated to this service: $s3BucketName"

    if(!s3BucketName) {
        println "No S3 bucket found for the service, creating one now.."
        s3BucketName = utilModule.generateBucketNameForService(domain, service)
        println "Will be creating a new bucket: $s3BucketName to deploy this service"
    } 
    
    def s3AssetInfo = "arn:aws:s3:::$s3BucketName/$environmentLogicalId"
    def isBucketExists = utilModule.checkIfBucketExists(s3BucketName, credsId)
    if(isBucketExists){
        println "Uploading content to a location in the existing bucket: ${s3AssetInfo}"
        _eventName = "UPDATE_ASSET";
        try {
            // Check whether the environmentLogicalId is and older archived environment, then it might have older asset archived entries
            if(environmentInfo && environmentInfo["metadata"] && environmentInfo["metadata"]["archived_environments"] && environmentInfo["metadata"]["archived_environments"].containsKey(environmentLogicalId)) {
                _eventName = "CREATE_ASSET";
            }
        } catch(ex) {
            println "Exception in archived fetch environments $ex"
        }
        events.sendStartedEvent(_eventName);
    } else {
        println "Creating a new S3 bucket: ${s3AssetInfo} in region: ${region} and uploading content to it.."
        _eventName = "CREATE_ASSET";
        events.sendStartedEvent(_eventName);
        println sh("aws s3 mb s3://$s3BucketName --profile ${credsId} --region ${region}")

        enableAccessLogsToS3(s3BucketName, credsId, configLoaderValue)
    }    
    try {
        // Verify only when bucket not ready
        println "Just after created: ${utilModule.checkIfBucketExists(s3BucketName, credsId)}"
        if("${utilModule.checkIfBucketExists(s3BucketName, credsId)}" != "true") {
            sleep(30000)
        }
        println "After some time: ${utilModule.checkIfBucketExists(s3BucketName, credsId)}"
        uploadContentToS3(s3BucketName, environmentLogicalId, config, credsId, configLoaderValue)
        
        events.sendCompletedEvent(_eventName, null, generateAssetMap("aws", s3AssetInfo, "s3", username));

        def cloudFrontDistribution = updateOrCreateCF(environmentLogicalId, domain + '_' + service, config, userDefinedConfig, s3BucketName, isPublic, websiteType, credsId, originAccessIdentityValue, accountDetails, configLoaderValue, gitCommitHash)
        cfArn = cloudFrontDistribution.ARN
        events.sendCompletedEvent(_eventName, null, generateAssetMap("aws", cfArn, "cloudfront", username));
        if(isBucketExists){
            invalidateCloudFrontCache(cfArn, domain + '_' + service, credsId)
        }
        generateBucketPolicy(s3BucketName, environmentLogicalId, credsId, originAccessIdentityValue, configLoaderValue)
        url = 'https://' + cloudFrontDistribution['DomainName']
        println "You can access the website at $url"

        events.sendCompletedEvent(_eventName, null, generateAssetMap("aws", url, "endpoint_url", username));
        def moreCxt = [created_by: username,  deployed_by: config['owner'], endpoint_url: url]
        // TO DO
        // environmentDeploymentMetadata.setEnvironmentEndpoint(url)
        events.sendCompletedEvent('UPDATE_ENVIRONMENT', 'Environment Update event for deployment completion', environmentDeploymentMetadata.generateEnvironmentMap("deployment_completed", null, null, url))
        events.sendCompletedEvent('UPDATE_DEPLOYMENT',  'Deployment completion Event for ' + envName + ' deployment',  environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.successful))
        events.sendCompletedEvent('DEPLOY_TO_AWS', 'successfully deployed website to AWS', moreCxt)
        qcpModule.sendQCPEvent("Post", "success")
    } catch(ex) {
        if (_eventName) {
            events.sendFailureEvent(_eventName, trimMessage(ex.getMessage()));
        }
        slack.sendSlackNotification("DEPLOY_TO_AWS", trimMessage(ex.message), "FAILED")
        events.sendFailureEvent('DEPLOY_TO_AWS', trimMessage(ex.getMessage()))
        events.sendFailureEvent('UPDATE_ENVIRONMENT', trimMessage(ex.getMessage()), environmentDeploymentMetadata.generateEnvironmentMap("deployment_failed"))
        events.sendFailureEvent('UPDATE_DEPLOYMENT', trimMessage(ex.getMessage()), environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        qcpModule.sendQCPEvent("Post", "failure")
        throw new Exception("The deployment failed with error ", ex)
    } finally {
        resetCredentials(credsId);
    }
}

/**
 * Create an S3 policy for direct S3 access through URLs or only through cloud front
 *
 */
def generateBucketPolicy(bucketName, stage, credsId, origin_access_identity_ID, configLoaderValue) {
    println "DeployWebsiteUtilityModule.groovy:generateBucketPolicy"
    def policySpec = '{"Version":"2012-10-17","Id":"Policy For ","Statement":[]}'
    def policyObj

    try {
        def policyJson = sh("aws s3api get-bucket-policy --bucket $bucketName --query Policy --output text --profile ${credsId}", true)
        policyObj = JSON.jsonParse(policyJson) 
    } catch(ex) {
        println "Bucket policy doesn't exists yet."
        policyObj =  JSON.jsonParse(policySpec)
        policyObj.Id += "$bucketName"
    }
    if(policyObj) {
        updateBucketPolicy(bucketName, stage, policyObj, credsId, origin_access_identity_ID, configLoaderValue)
    }
}

/**
 * Update an existing policy
 *
 */
def updateBucketPolicy(bucketName, stage, existingPolicyObj, credsId, origin_access_identity_ID, configLoaderValue) {
    println "DeployWebsiteUtilityModule.groovy:updateBucketPolicy"
    try {
        def policyObj = existingPolicyObj
        def accessIdentityValue

        if(origin_access_identity_ID){
            accessIdentityValue = origin_access_identity_ID
        } else {
            try {
                accessIdentityValue = sh("aws cloudfront list-cloud-front-origin-access-identities --output text \
                --query \"CloudFrontOriginAccessIdentityList.Items[?Comment=='${configLoaderValue.INSTANCE_PREFIX}-origin_access_identity'].{j: Id}\" --profile ${credsId}", true).trim()
            } catch (ex) {
                println "Failed to list cloudfront origin access identities"
            }
        }
        
        def existingStmts = policyObj.Statement
        
        def listBucketPolicyExists = false;
        def envSpecificPolicyExists = false;
        def ipAccessPolicyExists = false;

        // Check if the desired policies exists
        for (aStmt in existingStmts) {
            if (aStmt['Sid'] == "list-$bucketName"){
                listBucketPolicyExists = true
            }
            else if (aStmt['Sid'] == "$stage-$bucketName"){
                envSpecificPolicyExists = true
            }
            else if (aStmt['Sid'] == "S3-Access-IPList"){
                ipAccessPolicyExists = true
            }
        }
        
        //Add Env specific policy
        if (!envSpecificPolicyExists){
            def principal = ["AWS": "arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity $accessIdentityValue"]
            def stmtCf = '{"Sid":"","Effect":"Allow","Principal":{"AWS":""},"Action":"s3:GetObject","Resource":""}'
            def newStmtObj = JSON.jsonParse(stmtCf)
            newStmtObj.Sid = "$stage-$bucketName"
            newStmtObj.Principal = principal
            newStmtObj.Resource = "arn:aws:s3:::$bucketName/$stage/*"
            policyObj.Statement << newStmtObj
        }
        
        //Add list policy
        if (!listBucketPolicyExists){
            def principal = ["AWS": "arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity $accessIdentityValue"]
            def stmtCfList = '{"Sid":"","Effect":"Allow","Principal":{"AWS":""},"Action":"s3:ListBucket","Resource":""}'
            def listPolicyObject = JSON.jsonParse(stmtCfList)
            listPolicyObject.Sid = "list-$bucketName"
            listPolicyObject.Principal = principal
            listPolicyObject.Resource = "arn:aws:s3:::$bucketName"
            policyObj.Statement << listPolicyObject
        }
        
        //Adding accessible IP List
        if (!ipAccessPolicyExists){
            def accessibleCfList = '{"Sid":"","Effect":"Allow","Principal":"*","Action":"s3:*","Resource":[],"Condition":{"IpAddress":{"aws:SourceIp":[]}}}'
            def accessiblePolicyObject = JSON.jsonParse(accessibleCfList)
            accessiblePolicyObject.Sid = "S3-Access-IPList"
            accessiblePolicyObject.Resource.push("arn:aws:s3:::${bucketName}")
            accessiblePolicyObject.Resource.push("arn:aws:s3:::${bucketName}/*")
            accessiblePolicyObject.Condition.IpAddress['aws:SourceIp'] = configLoaderValue.JAZZ.S3_ACCESS_IP_ADDRESSES
            policyObj.Statement << accessiblePolicyObject
        }
        
        def newPolicyJson = JsonOutput.prettyPrint(JsonOutput.toJson(policyObj))
        println "S3 Policy: $newPolicyJson"
        def putPolicyResponse = sh("aws s3api put-bucket-policy --output json --bucket $bucketName --policy \'${newPolicyJson}\' --profile ${credsId}", true)
    } catch(ex) {
        println "Bucket policy update failed. "+ ex.getMessage()
        throw new Exception("Bucket policy update failed. ", ex)
    }
}


/**
 * Invalidate CloudFront Cache after deployment
 *
 */
def invalidateCloudFrontCache(arn, service, credsId) {
    println "DeployWebsiteUtilityModule.groovy:invalidateCloudFrontCache"
    def utilModule = new UtilityModule();
    try {
        if(arn) {
            def distId = arn.tokenize(":")[4].replaceAll("distribution/","")
            if(distId) {
                def callerReference = service + "_" + utilModule.generateRequestId()
                def config = '\\{\\"Paths\\":\\{\\"Quantity\\":1,\\"Items\\":[\\"/*\\"]\\},\\"CallerReference\\":\\"'+callerReference+'\\"\\}'
                def invalidateStatus = sh("aws cloudfront create-invalidation \
                                    --distribution-id $distId \
                                    --invalidation-batch $config \
                                    --output json \
                                    --profile ${credsId}", true)

                println "Invalidated CloudFront for $service, status: $invalidateStatus"
            }
        }
    } catch(ex) {
        throw new Exception("Error occured while invalidating the Cloudfron Cache. ", ex)
    }
}

/**
 * Reset AWS credentials
 */
def resetCredentials(credsId) {
    println "DeployWebsiteUtilityModule.groovy:resetCredentials"
    println "resetting AWS credentials"
    sh("aws configure set profile.${credsId}.aws_access_key_id XXXXXXXXXXXXXXXXXXXXXXXXXX")
    sh("aws configure set profile.${credsId}.aws_secret_access_key XXXXXXXXXXXXXXXXXXXXXX")
}

/*
* Function to enable access logs for the s3 bucket created
*/
def enableAccessLogsToS3(s3BucketName, credsId, configLoaderValue) {
    println "DeployWebsiteUtilityModule.groovy:enableAccessLogsToS3"

    try {
        def props = JSON.getAllProperties() 
        def config = props['serviceConfig']
        def accountId = config['account']

        def canonicalUserId = sh("aws s3api list-buckets --query Owner.ID --output text --profile ${credsId}");
        println "canonicalUserId: ${canonicalUserId}"

        def LoggingEnabled = [:]
        LoggingEnabled.put("TargetBucket", "${configLoaderValue.JAZZ.JAZZ_S3_TARGET_BUCKET_ACCESS_LOGS}-${accountId}")
        LoggingEnabled.put("TargetPrefix", s3BucketName)

        def FinalLog = [:]
        FinalLog.put("LoggingEnabled", LoggingEnabled);

        def loggingPayload = JSON.objectToJsonString(FinalLog)

        println "FinalLog: $FinalLog"

        if (JSON.isFileExists('logging.json')) {
            println "file exists"
            sh("rm -rf logging.json")
        }
        sh("echo '$loggingPayload' >> logging.json")

        println sh("aws s3api put-bucket-acl --bucket ${configLoaderValue.JAZZ.JAZZ_S3_TARGET_BUCKET_ACCESS_LOGS}-${accountId}  --grant-write URI=http://acs.amazonaws.com/groups/s3/LogDelivery --grant-read-acp URI=http://acs.amazonaws.com/groups/s3/LogDelivery --profile ${credsId}")
        println sh("aws s3api put-bucket-logging --bucket ${s3BucketName} --bucket-logging-status file://logging.json --profile ${credsId}")

    } catch(ex) {
        throw new Exception("Failed to enable access logs in s3", ex)
    }
}

def uploadContentToS3(s3BucketName, environment, config, credsId, configLoaderValue){
    println "DeployWebsiteUtilityModule.groovy:uploadContentToS3"
    def env = System.getenv()
    def repoName = env['REPO_NAME']
    try {
        if(config['framework'] == 'angular'){
            println "aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app/dist s3://$s3BucketName/$environment  --exclude \".git/*\" --exclude \".gitignore\" --exclude \"*.svg\" --profile ${credsId}"
            sh("aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app/dist s3://$s3BucketName/$environment  --exclude \".git/*\" --exclude \".gitignore\" --exclude \"*.svg\" --profile ${credsId}")
            println "aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app/dist s3://$s3BucketName/$environment  --exclude \"*\" --include \"*.svg\" --no-guess-mime-type --content-type image/svg+xml --profile ${credsId}"
            sh("aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app/dist s3://$s3BucketName/$environment  --exclude \"*\" --include \"*.svg\" --no-guess-mime-type --content-type image/svg+xml --profile ${credsId}")
        } else if (config['framework'] == 'react'){
            sh("aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app/build s3://$s3BucketName/$environment  --exclude \".git/*\" --exclude \".gitignore\" --exclude \"*.svg\" --profile ${credsId}")
            sh("aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app/build s3://$s3BucketName/$environment  --exclude \"*\" --include \"*.svg\" --no-guess-mime-type --content-type image/svg+xml --profile ${credsId}")
        } else {
            println "aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app s3://$s3BucketName/$environment  --exclude \".git/*\" --exclude \".gitignore\" --exclude \"*.svg\" --profile ${credsId}"
            println sh("aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app s3://$s3BucketName/$environment  --exclude \".git/*\" --exclude \".gitignore\" --exclude \"*.svg\" --profile ${credsId}")
            println "aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app s3://$s3BucketName/$environment  --exclude \"*\" --include \"*.svg\" --no-guess-mime-type --content-type image/svg+xml --profile ${credsId}"
            println sh("aws s3 sync ${PROPS.WORKING_DIRECTORY}/${repoName}/app s3://$s3BucketName/$environment  --exclude \"*\" --include \"*.svg\" --no-guess-mime-type --content-type image/svg+xml --profile ${credsId}")
        }
        // Need to use 'Environment: Production' as a tag since we use the same S3 bucket for all environments
        println sh("aws s3api put-bucket-tagging --bucket $s3BucketName --tagging 'TagSet=[{Key=Application,Value=${config.appTag}},{Key=ApplicationId,Value=${config.appId}},{Key=Platform,Value=${configLoaderValue.INSTANCE_PREFIX}},{Key=Service,Value=${config.service}},{Key=Domain,Value=${config.domain}},{Key=Environment,Value=Production},{Key=Owner,Value=${config.owner}}]' --profile ${credsId}", true)
    }  catch(ex) {
        throw new Exception("Failed to upload content in s3", ex)
    }
}

/*
* Function to get the Region the bucket resides in
*/
def getBucketLocation(bucketName, credsId) {
    println "DeployWebsiteUtilityModule.groovy:getBucketLocation"

    try {
        def bucketLocation = sh("aws s3api get-bucket-location --bucket ${bucketName} --profile ${credsId}", true)
        println "bucketLocation is: ${bucketLocation}"
        def bucketRegion = JSON.jsonParse(bucketLocation)
        println "bucketRegion is: ${bucketRegion}"
        // https://docs.aws.amazon.com/cli/latest/reference/s3api/get-bucket-location.html
        // as per documentation LocationConstraint = null implies us-east-1
        if (bucketRegion.LocationConstraint == null || bucketRegion.LocationConstraint.equals("null"))
        {
            println "setting bucket region to us-east-1"
            bucketRegion.LocationConstraint = 'us-east-1'
        }
        return bucketRegion.LocationConstraint
    } catch(ex) {
        println "Failed to get the region of s3"
        ex.printStackTrace()
        throw new Exception("Failed to get the region of s3", ex)
    }
}

/**
 * Create/Update the CloudFront Distribution
 */
def updateOrCreateCF(stage, service, config, userDefinedConfig, bucketName, isPublic, websiteType, credsId, originAccessIdentityValue, accountDetails, configLoaderValue, gitCommitHash) {
    println "DeployWebsiteUtilityModule.groovy:updateOrCreateCF"
    def result
    def distributionId = getDistributionId(stage, service, credsId)
    def bucketDeployedRegion = getBucketLocation(bucketName, credsId)
    println "CloudFront DistributionId: " + distributionId
    if(distributionId == null || distributionId == "") {
        println "No distribution configured for $service. Creating a new distribution..."
        //loadDistributionConfig(scmManagedRepoCredentialId)
        def indexForwardingRequired = false
        def ipAuthorizationRequired = false

        if (!isPublic) {
            println "Applying IP authorizer function for this distribution.."
            ipAuthorizationRequired = true
        }
        if (websiteType && (websiteType == 'hugo' || websiteType == 'mkdocs')) {
            println "Applying index forwarder function for this distribution.."
            indexForwardingRequired = true
        }
        
        generateLambdaEdgeConfig(indexForwardingRequired, ipAuthorizationRequired, accountDetails, userDefinedConfig.lambdaEdgeAuthorizerArn, userDefinedConfig.lambdaEdgeEventType, configLoaderValue)

        println "Generating distribution configuration."
        generateDistributionConfig(service, stage, config, bucketName, originAccessIdentityValue, configLoaderValue, gitCommitHash, bucketDeployedRegion)
        
        sh("aws cloudfront create-distribution-with-tags --distribution-config-with-tags --output json  file://distribution_config_with_tags.json --profile ${credsId}")
        result = getCloudFrontARN(service, stage, credsId)
        return result
    } else {
        println "Found distribution, getting distribution config..."
        def cf_config = sh("aws cloudfront get-distribution-config --id $distributionId --output json --profile ${credsId}", true)

        try {
            def cfConfig = JSON.jsonParse(cf_config)
            if(cfConfig == null) { throw new Exception("Could not parse distribution configuration")}

            def _eTag = cfConfig.ETag
            def updateConfig =     cfConfig.DistributionConfig
            if(updateConfig == null) { throw new Exception("Invalid distribution configuration returned")}

            // set new origin
            println "Updating existing CloudFront distribution.."
            updateConfig.Origins.Items[0].DomainName = "$bucketName"+".s3."+bucketDeployedRegion+".amazonaws.com"
            println "DomainName with region: " + updateConfig.Origins.Items[0].DomainName
            updateConfig.Origins.Items[0].OriginPath = "/$stage"

            if(!updateConfig.Enabled) {
                updateConfig.Enabled = true
            }
            if(!updateConfig.DefaultCacheBehavior.SmoothStreaming) {
                updateConfig.DefaultCacheBehavior.SmoothStreaming = false
            }
            if(!updateConfig.DefaultCacheBehavior.Compress) {
                updateConfig.DefaultCacheBehavior.Compress = true
            }

            if(updateConfig.DefaultCacheBehavior) {
                println "Checking if cloudfront needs lambda@edge trigger.."
                def LambdaFunctionARN;
                def ipAuthorizationRequired = false
                def indexForwardingRequired = false

                if(!isPublic) {
                    println "Applying IP authorizer function to this distribution..."
                    ipAuthorizationRequired = true
                }
                if (websiteType && (websiteType == 'hugo' || websiteType == 'mkdocs')) {
                    println "Applying index forwarder function to this distribution.."
                    indexForwardingRequired = true
                }
                if (!accountDetails.PRIMARY) {
                    if (indexForwardingRequired && ipAuthorizationRequired){
                    LambdaFunctionARN = accountDetails.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER_INDEX_FORWARDER
                    } else if (indexForwardingRequired){
                    LambdaFunctionARN = accountDetails.CLOUDFRONT.LAMBDA_AT_EDGE.INDEX_FORWARDER
                    } else if (ipAuthorizationRequired) {
                        LambdaFunctionARN = accountDetails.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER
                    }        
                }
                else {
                    if (indexForwardingRequired && ipAuthorizationRequired){
                    LambdaFunctionARN = configLoaderValue.AWS.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER_INDEX_FORWARDER
                    } else if (indexForwardingRequired){
                    LambdaFunctionARN = configLoaderValue.AWS.CLOUDFRONT.LAMBDA_AT_EDGE.INDEX_FORWARDER
                    } else if (ipAuthorizationRequired) {
                        LambdaFunctionARN = configLoaderValue.AWS.CLOUDFRONT_LAMBDA_EDGE_ARN
                    // TODO: Enable below once we figure out the backfill process (We can only add one funtion to CF for a specific eventType)
                    // LambdaFunctionARN = Configs.AWS.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER
                    }
                }
                // read from deployment-env.yml
                // lambdaEdgeEventType: 'viewer-request' - Supported values: https://docs.aws.amazon.com/cloudfront/latest/APIReference/API_LambdaFunctionAssociation.html
                // lambdaEdgeAuthorizerArn: userLambdaAuthorizerARNWithVersion
                // eg. arn:aws:lambda:REGION:ACCT_ID:function:my-lambda-authorizer-prod:4 - Doesn't work with $LATEST or Version Alias            
            
                // Default EventType for backwards compatability
                def LambdaFunctionAssociationEventType = 'viewer-request'
            
                if (userDefinedConfig.lambdaEdgeAuthorizerArn && userDefinedConfig.lambdaEdgeAuthorizerArn.trim()){
                    LambdaFunctionARN = userDefinedConfig.lambdaEdgeAuthorizerArn.trim()
                    if (userDefinedConfig.lambdaEdgeEventType && userDefinedConfig.lambdaEdgeEventType.trim()) {
                        LambdaFunctionAssociationEventType = userDefinedConfig.lambdaEdgeEventType.trim()
                    }
                }
                if (LambdaFunctionARN) {
                    println "lambdaEdgeAuthorizerArn: ${LambdaFunctionARN}"
                    println "lambdaEdgeEventType: ${LambdaFunctionAssociationEventType}"
                
                    // This will override any existing function associations & this is by design!
                    def Items = []
                    def ItemMap_1 = [:]
                    ItemMap_1['EventType'] = LambdaFunctionAssociationEventType
                    ItemMap_1['LambdaFunctionARN'] = LambdaFunctionARN
                    Items[0] = ItemMap_1
                    def LambdaFunctionAssociations = [:]
                    LambdaFunctionAssociations['Items'] = Items
                    LambdaFunctionAssociations['Quantity'] = 1
                    updateConfig.DefaultCacheBehavior.LambdaFunctionAssociations = LambdaFunctionAssociations
                } else {
                    println "No Lambda to attach! Also, removing any existing associations.."
                       def LambdaFunctionAssociations = [:]
                    LambdaFunctionAssociations['Items'] = []
                    LambdaFunctionAssociations['Quantity'] = 0
                    updateConfig.DefaultCacheBehavior.LambdaFunctionAssociations = LambdaFunctionAssociations
                }
            }
            def updateConfigJson = JsonOutput.toJson(updateConfig)
            try {
                sh("echo '$updateConfigJson' > cf_config.json")
                println sh("cat cf_config.json", true)
            } catch(ex){
                println "Ignoring the lazy error: "+ex.getMessage()
            }
            sh("aws cloudfront update-distribution \
                    --distribution-config file://cf_config.json \
                    --id $distributionId \
                    --if-match $_eTag \
                    --output json \
                    --profile ${credsId}")
            result = getCloudFrontARN(service, stage, credsId)
            
            println "Updating tags.."
            
            // Update tags for an existing distribution
            sh("aws cloudfront tag-resource \
                    --resource ${result.ARN} \
                    --tags 'Items=[{Key=Application,Value=${config.appTag}},{Key=ApplicationId,Value=${config.appId}},{Key=Platform,Value=${configLoaderValue.INSTANCE_PREFIX}},{Key=Service,Value=${config.service}},{Key=Domain,Value=${config.domain}},{Key=Environment,Value=${getEnvironmentTag(stage)}},{Key=EnvironmentId,Value=${stage}},{Key=Owner,Value=${config.owner}},{Key=GitCommitHash,Value=${gitCommitHash}}]' \
                    --profile ${credsId}")
        } catch(ex) {
            if((ex.getMessage().indexOf("groovy.json.JsonSlurper") > -1) ||
                    (ex.getMessage().indexOf("groovy.json.internal.LazyMap") > -1)) {
                println "Ignoring the lazy error: "+ex.getMessage()
            } else {
                throw new Exception("Could not update the distribution.", ex)
            }
        } finally {
            return result
        }
    }
}

/**
 * Get dist Id if exists
 *
 */
def getDistributionId(stage, service, credsId) {
    println "DeployWebsiteUtilityModule.groovy:getDistributionId"
    def distributionID
    def outputStr
    try {
        outputStr = sh ("aws cloudfront list-distributions \
                --output json \
                --profile ${credsId} \
                --query \"DistributionList.Items[?Origins.Items[?Id=='$stage-static-website-origin-$service']].{Distribution:DomainName, Id:Id}\"", true)
        if(outputStr) {
            println "Result oflist-distributions call: $outputStr"
            def outputObj = new JsonSlurper().parseText(outputStr)
            if(outputObj && outputObj[0].Id) {
                distributionID = outputObj[0].Id
            }
        }
        return distributionID
    }catch (ex) {
        return distributionID
    }
}

// Not needed, will remove once validated
/**
 * Loads template distribution config
 */
def loadDistributionConfig(scmManagedRepoCredentialId) {
    println "DeployWebsiteUtilityModule.groovy:loadDistributionConfig"
    try {
        def distr_url = "https://${coreRepoScmHostName}/${coreRepoBaseUri}/jenkins-build-pack-website-v2.git"
        dir('_distribution_config'){
            println "Loading distribution configuration"
            checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: scmManagedRepoCredentialId, url: distr_url]]])
        }
        sh "cp _distribution_config/distribution_config_with_tags.json ./distribution_config_with_tags.json"
    } finally {
        sh "rm -rf ./_distribution_config"
    }
}


/**
    Generate the distribution config file
*/
def generateLambdaEdgeConfig(indexForwardingRequired, ipAuthorizationRequired, accountDetails, lambdaEdgeAuthorizerArn, lambdaEdgeEventType, configLoaderValue) {
    println "DeployWebsiteUtilityModule.groovy:generateLambdaEdgeConfig"
    try{

        //def distribution_config_with_tags = new java.io.File("./distribution_config_with_tags.json").readLines()
        //println distribution_config_with_tags

        def cfConfig = JSON.readFile("./distribution_config_with_tags.json")
        // def cfConfig = JSON.jsonParse(distribution_config_with_tags)
        if(cfConfig.DistributionConfig == null) {
            throw new Exception("Invalid distribution configuration")
        }
        if (cfConfig.DistributionConfig.DefaultCacheBehavior && (indexForwardingRequired || ipAuthorizationRequired)) {
            def LambdaFunctionARN;
            if (!accountDetails.PRIMARY) {
                if (indexForwardingRequired && ipAuthorizationRequired){
                    LambdaFunctionARN = accountDetails.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER_INDEX_FORWARDER
                } else if (indexForwardingRequired){
                    LambdaFunctionARN = accountDetails.CLOUDFRONT.LAMBDA_AT_EDGE.INDEX_FORWARDER
                } else if (ipAuthorizationRequired) {
                        LambdaFunctionARN = accountDetails.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER
                }
            }
            else {
                if (indexForwardingRequired && ipAuthorizationRequired){
                    LambdaFunctionARN = configLoaderValue.AWS.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER_INDEX_FORWARDER
                } else if (indexForwardingRequired){
                    LambdaFunctionARN = configLoaderValue.AWS.CLOUDFRONT.LAMBDA_AT_EDGE.INDEX_FORWARDER
                } else if (ipAuthorizationRequired) {
                        LambdaFunctionARN = configLoaderValue.AWS.CLOUDFRONT_LAMBDA_EDGE_ARN
                    // TODO: Enable below once we figure out the backfill process (We can only add one funtion to CF for a specific eventType)
                    // LambdaFunctionARN = Configs.AWS.CLOUDFRONT.LAMBDA_AT_EDGE.IP_AUTHORIZER
                }
            }
            // read from deployment-env.yml
            // lambdaEdgeEventType: 'viewer-request' - Supported values: https://docs.aws.amazon.com/cloudfront/latest/APIReference/API_LambdaFunctionAssociation.html
            // lambdaEdgeAuthorizerArn: userLambdaAuthorizerARNWithVersion
            // eg. arn:aws:lambda:REGION:ACCT_ID:function:my-lambda-authorizer-prod:4 - Doesn't work with $LATEST or Version Alias
    
            // Default EventType for backwards compatability
            def LambdaFunctionAssociationEventType = 'viewer-request'
            
            if (lambdaEdgeAuthorizerArn && lambdaEdgeAuthorizerArn.trim()) {
                LambdaFunctionARN = lambdaEdgeAuthorizerArn.trim()
                if (lambdaEdgeEventType && lambdaEdgeEventType.trim()) {
                    LambdaFunctionAssociationEventType = lambdaEdgeEventType.trim()
                }
            }
            if  (LambdaFunctionARN) {
                println "lambdaEdgeAuthorizerArn: ${LambdaFunctionARN}"
                println "lambdaEdgeEventType: ${LambdaFunctionAssociationEventType}"
            
                def Items = []
                def ItemMap_1 = [:]
                ItemMap_1['EventType'] = LambdaFunctionAssociationEventType
                ItemMap_1['LambdaFunctionARN'] = LambdaFunctionARN
                Items[0] = ItemMap_1

                def LambdaFunctionAssociations = [:]
                LambdaFunctionAssociations['Items'] = Items
                LambdaFunctionAssociations['Quantity'] = 1
                cfConfig.DistributionConfig.DefaultCacheBehavior.LambdaFunctionAssociations = LambdaFunctionAssociations
            } else  {
                println "No LambdaFunctionAssociation!"
            }
        }
        def cfConfigJson = JsonOutput.toJson(cfConfig)
        // writeFile file: "distribution_config_with_tags.json", text: cfConfigJson
        println "ConfigJSON: ${cfConfigJson}"
        File file = new File("./distribution_config_with_tags.json")
        file.write(cfConfigJson)
    }
    catch(Exception e) {
        throw new Exception("Exception occurred", e)
     }
    def distribution_config_with_tags = new java.io.File("distribution_config_with_tags.json").readLines()
    println distribution_config_with_tags
}


/**
    Generate the distribution config file
*/
def generateDistributionConfig(service, env, config, bucketName, originAccessIdentity, configLoaderValue, gitCommitHash, bucketDeployedRegion) {
    println "DeployWebsiteUtilityModule.groovy:generateDistributionConfig"
    def cloudFrontOriginAccessIdentity = "origin-access-identity/cloudfront/"+originAccessIdentity
    sh("sed -i -- 's/{bucketName}/"+bucketName+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's:{oai}:${cloudFrontOriginAccessIdentity}:g' distribution_config_with_tags.json")
    
    sh("sed -i -- 's/{service}/"+service+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{domain}/"+config['domain']+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{region}/"+bucketDeployedRegion+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{environmentId}/"+env+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{applicationId}/"+config['appId']+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{applicationTag}/"+config['appTag']+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{owner}/"+config['owner']+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{platform}/"+configLoaderValue.INSTANCE_PREFIX+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{gitCommitHash}/"+gitCommitHash+"/g' distribution_config_with_tags.json")
    sh("sed -i -- 's/{environmentTag}/"+getEnvironmentTag(env)+"/g' distribution_config_with_tags.json")

    def distribution_config_with_tags = new java.io.File("distribution_config_with_tags.json").readLines()
    println distribution_config_with_tags
}


/**
 * Get CloudFront ARN
 */
def getCloudFrontARN(service_name, env, credsId) {
    println "DeployWebsiteUtilityModule.groovy:getCloudFrontARN"
    def cfInfo = [:]
    try {
        def cloudFrontDetails = "";
        cloudFrontDetails = sh("aws cloudfront list-distributions --output  json  --query \"DistributionList.Items[?Origins.Items[?Id=='$env-static-website-origin-$service_name']].{Distribution:DomainName, Id:Id}\" --profile ${credsId}")
        def cloudFrontDetailsLazyMap = JSON.jsonParse(cloudFrontDetails)

        def cloudFrontId = "";
        def cloudFrontDetailsArray = cloudFrontDetailsLazyMap[0];
        cloudFrontId = cloudFrontDetailsArray.Id;

        def cloudFrontDistributionDetails = sh("aws cloudfront get-distribution --output json  --id $cloudFrontId --profile ${credsId}")
        println "cloudFrontDistributionDetails... $cloudFrontDistributionDetails"
        def _map = JSON.jsonParse(cloudFrontDistributionDetails)
        if(_map) {
            cfInfo << [ARN: _map.Distribution.ARN]
            cfInfo << [DomainName: _map.Distribution.DomainName]
        }
    } catch(error) {
        println "error $error"
    } finally {
        return cfInfo;
    }
}


def generateAssetMap(provider, providerId, type, created_by) {
    println "DeployWebsiteUtilityModule.groovy:generateAssetMap"
    def serviceCtxMap = [
        provider: provider,
        provider_id: providerId,
        type: type,
        created_by: created_by
    ]
    return serviceCtxMap;
}

def getWebsiteType(userDefinedConfig) {
    println "DeployWebsiteUtilityModule.groovy:getWebsiteType"
    def websiteType = ""
    if (userDefinedConfig.containsKey('type')) {
        websiteType = userDefinedConfig['type']
    }
    if(websiteType == '' || websiteType == null ){
        websiteType = 'static'
    }

    return websiteType.trim()
}

def generateAccessIdentity(accountDetails){
    println "DeployWebsiteUtilityModule.groovy:generateAccessIdentity"
    def originAccessIdentityValue = accountDetails.CLOUDFRONT.CLOUDFRONT_ORIGIN_ID.split("/");
    originAccessIdentityValue = originAccessIdentityValue[originAccessIdentityValue.length - 1];
    println "OriginAccessIdentity: ${originAccessIdentityValue}"
    return originAccessIdentityValue;
}

/**
*   Generate tag for a specific environment based on the id
*
*   Ref: https://ccoe.docs.t-mobile.com/aws/reference/hostname_tagging_guidelines/#environments
*/
def getEnvironmentTag(env){
    println "DeployWebsiteUtilityModule.groovy:getEnvironmentTag"
    def envTag = "Non-production"
    if (env == "prod"){
      envTag = "Production"
    }
    return envTag
}

/**
*   Build the app
*/
def buildApp(framework, env, repoName) {
    println "DeployWebsiteUtilityModule.groovy:buildApp"
    def envs = System.getenv()

    try {
        if(env != 'prod' && env != 'stg'){
            env = 'dev';
        }
        def websiteCodeFolder = "${PROPS.WORKING_DIRECTORY}/${repoName}/app/"
        switch(framework) {
            case "angular":
                sh("cd ${websiteCodeFolder};rm -rf dist;npm cache clean --force;npm install > ../../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_npm-install.log;npm run build -- --configuration=${env} --output-path=dist > ../../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_build-app.log 2>&1")
                println "==================================================="
                println sh("cat  ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_npm-install.log")
                println sh("cat  ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_build-app.log")
                break
            case "react":
                sh("cd ${websiteCodeFolder};rm -rf build;npm cache clean --force;npm install >  ../../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_npm-install.log;CI=false npm run build >  ../../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_build-app.log 2>&1")
                println "==================================================="
                println sh("cat  ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_npm-install.log")
                println sh("cat  ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_build-app.log")
                break
            default:
                println "Unknown framework, not executing any build commands.."
                break
        }
    } catch(ex) {
        println "Error occured while building the app: ${ex}"
        throw new Exception("Error occured while building the app: ", ex)
    }
}

def configureProductionDeploy() {
    println "DeployWebsiteUtilityModule.groovy:configureProductionDeploy"
    try {
        def env = System.getenv()
    
        def utilModule = new UtilityModule()
        def events = new EventsModule()
        utilModule.showServiceEnvParams()

        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
        resetEnvironmentSpecificConfigurations()
        environmentDeploymentMetadata.setEnvironmentLogicalId("prod")
        // calling the method sets the environmentId in the properties file
        def environmentId = environmentDeploymentMetadata.getEnvironmentId()

        sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
        sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")

        // If prod specific deployment-env.yml exists, use it. If not, copy the original file from user repository.
        if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.prod.yml")){
            sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.prod.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml") 
        }
        else if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deploymentEnvOriginal.yml")){
            sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deploymentEnvOriginal.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")  
        } else {
            println "No deployment-env.yml in user repository. Proceeding without one.."
        }


        checkDeploymentConfiguration(environmentDeploymentMetadata)
        events.sendStartedEvent('CREATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        approvalForProductionDeploy()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "Sent email for approval", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.approval_pending))
        
        //Setting current time for checking the timeput of production deploy
        def currentTimeValue = System.currentTimeMillis();
        JSON.setValueToPropertiesFile('approvalRequestRaisedTime', currentTimeValue)
    } catch (ex) {
        println "configureProductionDeploy- failed: $ex"
        throw new Exception("configureProductionDeploy- failed: ", ex)
    }    
}

def approvalForProductionDeploy () {
    println "DeployWebsiteUtilityModule.groovy:approvalForProductionDeploy"
    def id
    def isInitialCommit = false 
    def props = JSON.getAllProperties()
    def configData = props['configData']
    def serviceConfig = props['serviceConfig']
    def utilModule = new UtilityModule()
    try {
        if(!isInitialCommit){
            def approval_timeout_mins = configData.JAZZ.PRODUCTION_APPROVAL_TIMEOUT_MINS as Integer
            if (serviceConfig.approvalTimeOutInMins) {
                approval_timeout_mins =serviceConfig.approvalTimeOutInMins as Integer
            }
            id = utilModule.getDeploymentId(approval_timeout_mins, serviceConfig, "production-deployment")
            JSON.setValueToPropertiesFile("approvalDeploymentId", id);
            //sending email notification to approver
            utilModule.notifyApprover(id, approval_timeout_mins, serviceConfig, configData)            
        }
    } catch (ex) {
        throw new Exception ("Approver notification failed", ex)
    }
}

def resetEnvironmentSpecificConfigurations() {
    println "DeployWebsiteUtilityModule.groovy:resetEnvironmentSpecificConfigurations"
    def props = JSON.getAllProperties()
    if (props['deploymentRole']) props.remove('deploymentRole')
    if (props['deploymentAccount']) props.remove('deploymentAccount')
    if (props['accountDetails']) props.remove('accountDetails')
    if (props['buildConfig']) props.remove('buildConfig')
    if (props['environmentInfo']) props.remove('environmentInfo')
    if (props['environmentLogicalId']) props.remove('environmentLogicalId')
    if (props['environmentId']) props.remove('environmentId')
    if (props['deploymentRegion']) props.remove('deploymentRegion')
    if (props['roleId']) props.remove('roleId')
    def serviceConfig = props['serviceConfig']
    serviceConfig.remove('account')
    serviceConfig.remove('region')
    props['serviceConfig'] = serviceConfig
    JSON.setAllProperties(props) 

    //Setting request id
    def requestId = sh("uuidgen -t") 
    JSON.setValueToPropertiesFile('REQUEST_ID', requestId.trim())

    println "account details - ${JSON.getValueFromPropertiesFile('accountDetails')}"
    println "environmentInfo details - ${JSON.getValueFromPropertiesFile('environmentInfo')}"
}

def requestEnvironmentArchival(events, qcpModule, slack) {
    println "DeployWebsiteUtilityModule.groovy:requestEnvironmentArchival"
    events.sendStartedEvent('REQUEST_ENVIRONMENT_ARCHIVAL')
    try {
        def utilModule = new UtilityModule();
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def environmentId = JSON.getValueFromPropertiesFile('environmentLogicalId')
        def archiveEnvironmentId = JSON.getValueFromPropertiesFile('archiveEnvironmentId')
        println "Trigger archival flow called"
        utilModule.triggerArchiveWorkflow(serviceConfig['service'], serviceConfig['domain'], environmentId, archiveEnvironmentId)
        events.sendCompletedEvent('REQUEST_ENVIRONMENT_ARCHIVAL')
    }catch(ex){
        println "Failed to trigger archival flow: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("REQUEST_ENVIRONMENT_ARCHIVAL", trimMessage(ex.message), "FAILED")
        events.setFailureEvent('REQUEST_ENVIRONMENT_ARCHIVAL')
        throw new Exception("Failed to trigger archival flow: ", ex)
    }
}

def trimMessage(exceptionMessage, lengthLimit=300) {
    println "DeployWebsiteUtilityModule.groovy:trimMessage"
    def env = System.getenv()
    trimMessage = exceptionMessage.substring(0, Math.min(exceptionMessage.length(), lengthLimit));
    println trimMessage + " ... Checkout for more - " + env.CI_PIPELINE_URL
    return trimMessage + " ... Checkout for more - " + env.CI_PIPELINE_URL
}
