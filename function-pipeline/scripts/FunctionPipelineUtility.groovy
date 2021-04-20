#!groovy?
import groovy.json.*
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.File as FILE
import common.util.Yaml as YAML
import common.util.Status as Status
import static common.util.Shell.sh as sh
import java.lang.*


/*
* FunctionPipelineUtility.groovy
* @author: Sini Wilson
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

def initialize () {
    println "Initializing function pipeline."
    
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
        preBuildValidation()
        
    } catch (ex) {
        println "Exception occured while initializing the function pipeline: + ${ex.message}"
        throw new Exception("Exception occured while initializing the function pipeline", ex)
    }
}

def configDeployment() {
    try {
        def events = new EventsModule();
        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
        def qcpModule = new QcpModule()
        def slack = new SlackModule()
        def env = System.getenv()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "configDeployment", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))

        println "============================================================"
        println "               DEPLOY TO AWS                                "
        println "============================================================"
        
        awsDeploy()

        def archiveEnvironmentId = JSON.getValueFromPropertiesFile('archiveEnvironmentId')

        if (archiveEnvironmentId) {
            println "============================================================"
            println "               REQUEST ENVIRONMENT ARCHIVAL                 "
            println "============================================================"

            requestEnvironmentArchival(events, qcpModule, slack)
        }

        println "============================================================"
        println "                        END                                 "
        println "============================================================"

    } catch (ex) {
        println "Exception occured in configDeployment: ${ex.message}"
        throw new Exception("Exception occured in configDeployment:", ex)
    }
}



def validatePipelineTriggerParams() {
    def env = System.getenv()
    if(!env.REPO_URL) {
        println "REPO_URL is not come as part of the request."
        throw new Exception("REPO_URL is not come as part of the request.");
    }
    if(!env.REPO_BRANCH) {
        println "REPO_BRANCH is not come as part of the request."
        throw new Exception("REPO_BRANCH is not come as part of the request.");
    }
    if(!env.REPO_NAME) {
        println "REPO_NAME is not come as part of the request."
        throw new Exception("REPO_NAME is not come as part of the request.");
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
   try {
        def qcpModule = new QcpModule()
        def env = System.getenv()
        def commitSha = env.COMMIT_SHA
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')        
        def repoInfo = serviceConfig.repository.split(".*://")
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
        if (!env.REQUEST_ID || (serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) )
        {        
            events.sendStartedEvent('CREATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started)) 
        }
        else
        {
            events.sendStartedEvent('UPDATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started)) 
        }
   } catch(ex) {
       events.sendFailureEvent('UPDATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
       println "loadDeploymentConfigurations failed- ${ex.message}"
       throw new Exception("loadDeploymentConfigurations failed:- ", ex)
   }   
}

def preBuildValidation() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    events.sendStartedEvent("VALIDATE_PRE_BUILD_CONF")    
    try {
        checkEnvironmentConfiguration(environmentDeploymentMetadata)
        checkDeploymentConfiguration(environmentDeploymentMetadata)
        loadServiceConfiguration()
        loadServerlessConfig()
        
        events.sendCompletedEvent("VALIDATE_PRE_BUILD_CONF")
    } catch (ex) {
        println "preBuildValidation failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("VALIDATE_PRE_BUILD_CONF", ex.message, "FAILED") 
        events.sendFailureEvent("VALIDATE_PRE_BUILD_CONF")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "Prebuild validation failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("preBuildValidation failed: ", ex)    
    }
}

def checkEnvironmentConfiguration (environmentDeploymentMetadata) {
    def qcpModule = new QcpModule()
    try {
        def env = System.getenv()        
        def environmentLogicalId
        def archiveEnvironmentId = false

        if(env.REPO_BRANCH == 'master') {
            // preparing for staging deployment
            environmentLogicalId = 'stg'
            def fileName = "deployment-env.stg.yml"
            def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/${fileName}")
            if (fExists)
            {
                sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.stg.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml") 
            }
            else
            {
                sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deploymentEnvOriginal.yml") 
            }
        } else {
            environmentLogicalId = environmentDeploymentMetadata.getEnvironmentLogicalId() 
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
        println "checkEnvironmentConfiguration failed: ${ex.message}"
        throw new Exception("checkEnvironmentConfiguration failed:", ex)
    }
    
}

def checkDeploymentConfiguration(environmentDeploymentMetadata) {    
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
        println "checkDeploymentConfiguration failed: ${ex.message}"
        throw new Exception("checkDeploymentConfiguration failed", ex)
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
//Loading user defined configurations from deployment-env.yml file of the user repo
def loadUserDefinedConfigs() {
    def env = System.getenv()
    def resultList = []
    def prop = [:]
    // handle the case where the codebase doesn't exists or is not a jazz project
    def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
    if (fExists) {
        println "deployment yaml exists"
        prop = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
        println "prop: $prop"
        // if prop is null set it to empty map
        if (!prop)
        {
            prop = [:]
        }
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
    def props = JSON.getAllProperties() 
    def environmentInfo = props['environmentInfo']
    def environmentLogicalId = props['environmentLogicalId']
    def deploymentAccount = environmentInfo['deployment_accounts']
    def userDefinedConfig = props['userDefinedConfig']
    def serviceConfig = props['serviceConfig']
    def internalAccess = null

    if (userDefinedConfig['require_internal_access'] != null && userDefinedConfig['require_internal_access'] != "" ) {
        println "user defined require_internal_access is being used."
        internalAccess = userDefinedConfig['require_internal_access'].toString();
    } else if (serviceConfig['require_internal_access'] != null && serviceConfig['require_internal_access'] != "" ){
        println "require_internal_access - from service catalog is being used."
        internalAccess = serviceConfig['require_internal_access'].toString();
    } 

    if(!deploymentAccount.isEmpty()) {        
        if (deploymentAccount[0].containsKey('region') && deploymentAccount[0].containsKey('account')) {
            //Getting the required deployment account and region information
            JSON.setValueToPropertiesFile('deploymentAccount', deploymentAccount[0].account)
            JSON.setValueToPropertiesFile('deploymentRegion', deploymentAccount[0].region)  

            serviceConfig['account'] = deploymentAccount[0].account
            serviceConfig['region'] = deploymentAccount[0].region

            JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)

            def utilModule = new UtilityModule();
            def accountDetails = utilModule.getAccountInfo();
            JSON.setValueToPropertiesFile('accountDetails', accountDetails)
            def deploymentRole
            if (userDefinedConfig['iamRoleARN']) {
                println "user defined role is using."
                deploymentRole = userDefinedConfig['iamRoleARN'];
            } else if (internalAccess != null && internalAccess.equals('false')){
                deploymentRole = accountDetails.IAM.USERSERVICES_ROLEID;
            } else {
                deploymentRole = accountDetails.IAM.VPCSERVICES_ROLEID;
            }

            JSON.setValueToPropertiesFile('deploymentRole', deploymentRole) 

            def roleId = deploymentRole.substring(deploymentRole.indexOf("::")+2, deploymentRole.lastIndexOf(":"))
            JSON.setValueToPropertiesFile('roleId', roleId)  
            serviceConfig << userDefinedConfig     
        } else {
            throw new Exception("Deployment account information is not valid for the environmentLogicalId: ${environmentLogicalId}, please set them by logging into Jazz UI.")
        }
    } else {
        throw new Exception("Deployment account information is not valid for the environmentLogicalId: ${environmentLogicalId}, please set them by logging into Jazz UI.")
    }

    validateDeploymentConfigurations(serviceConfig)
}

def clearVirtualEnv() {
    def env = System.getenv()
    sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf venv")
    sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf virtualenv")
}

def validateDeploymentConfigurations(serviceConfig) {
    if (!serviceConfig["service"]) {
        throw new Exception("Wrong configuration. Value for Key 'service' is missing in the configuration")        
    }

    if (serviceConfig["providerRuntime"]) {
        def _runtime = serviceConfig['providerRuntime']
        if (_runtime == "") {
            throw new Exception("Wrong configuration. Value for Key 'providerRuntime' is missing in the configuration")
        } else {
            def validRuntimes = ["nodejs10.x", "nodejs8.10", "nodejs12.x", "python2.7", "python3.6", "python3.8", "java8", "java11", "go1.x"]
            def flag = false
            for(int i = 0; i < validRuntimes.size(); i++) {
                if(_runtime == validRuntimes[i]) {
                    flag = true
                }
            }

            if(!flag) {
                throw new Exception("Runtime given in the configuration is not valid.")
            }
        }
    } else {
        throw new Exception("Wrong configuration. Key 'providerRuntime' is missing in the configuration")
    }

    if (serviceConfig["providerTimeout"]) {
        def providerTimeout
        if (!serviceConfig['providerTimeout'] instanceof Integer){
            providerTimeout = Integer.parseInt(serviceConfig['providerTimeout'])
        }
        if (providerTimeout == "") {
            throw new Exception("Wrong configuration. Value for Key 'providerTimeout' is missing in the configuration")
        } else if (providerTimeout > 300) { // Should not be a high
            throw new Exception("Wrong configuration. Value for Key 'providerTimeout' should be a less than 160")
        }
    } else {
        throw new Exception("Wrong configuration. Key 'providerTimeout' is missing in the configuration")
    }

    if (!serviceConfig["region"]) {
        throw new Exception("Wrong configuration. Key 'region' is missing in the configuration")
    } 

    def runtime = serviceConfig['providerRuntime']
    if(serviceConfig['runtime'].indexOf("java") > -1) {
        if(!serviceConfig["artifact"] ) {
            throw new Exception("Wrong configuration. Key 'artifact' is missing in the configuration")
        }
        if(!serviceConfig["mainClass"]) {
            throw new Exception("Wrong configuration. Key 'mainClass' is missing in the configuration")
        }
    }
}

def loadServerlessConfig() {
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def userDefinedConfig = JSON.getValueFromPropertiesFile('userDefinedConfig')
    def props = JSON.getAllProperties() 
    def environmentLogicalId = props['environmentLogicalId']
    def env = System.getenv()
    
    def internalAccess = null
    def isScheduleEnabled = isEnabled(serviceConfig, "eventScheduleRate")
    def isStreamEnabled = isEnabled(serviceConfig, "event_source_kinesis")
    def isDynamoDbEnabled = isEnabled(serviceConfig, "event_source_dynamodb")
    def isS3EventEnabled = isEnabled(serviceConfig, "event_source_s3")
    def isSQSEventEnabled = isEnabled(serviceConfig, "event_source_sqs")
    def isEc2EventEnabled = isEnabled(serviceConfig, "event_source_ec2")
    def isEventScheduled = false
    if (isScheduleEnabled || isEc2EventEnabled || isS3EventEnabled || 
            isStreamEnabled || isDynamoDbEnabled || isSQSEventEnabled) {
        isEventScheduled = true
        JSON.setValueToPropertiesFile('isEventScheduled', isEventScheduled)
    }

    //Merging userConfig to serviceConfig
    serviceConfig << userDefinedConfig
    println "serviceConfig: $serviceConfig"

    clearVirtualEnv()
    //cleaning workplace before cloning
    println "cleaning serverlessConfigRepo in working directory"
    sh("rm -rf ${PROPS.SERVERLESS_CONFIG_DIRECTORY}")

    //Cloning serverless config repo
    sh("git clone -b master ${env.SERVERLESS_CONFIG_REPO} ${PROPS.SERVERLESS_CONFIG_DIRECTORY}")
       
    
    sh("touch ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    if(serviceConfig['providerRuntime'].indexOf("nodejs") > -1) {
        sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-nodejs.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml" )
    }else if(serviceConfig['providerRuntime'].indexOf("java") > -1) {
        sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-java.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }else if(serviceConfig['providerRuntime'].indexOf("python") > -1) {
        sh( "cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-python.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }else if (serviceConfig['providerRuntime'].indexOf("go") > -1){
        sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-go.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }
    // serverless.yml file that is loaded contains all events enabled.
    // remove events if the not selected and also enable events
    if (isEventScheduled == true) {
        addEvents(isScheduleEnabled, isEc2EventEnabled, isS3EventEnabled, isStreamEnabled, isDynamoDbEnabled, isSQSEventEnabled)
    }

    if (isS3EventEnabled || isSQSEventEnabled || isDynamoDbEnabled || isStreamEnabled) {
        sh("cp ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/aws-events-policies/custom-policy.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/policyFile.yml")
    }

    if (userDefinedConfig['require_internal_access'] != null && userDefinedConfig['require_internal_access'] != "" ) {
        println "user defined require_internal_access is being used."
        internalAccess = userDefinedConfig['require_internal_access'].toString();
    } else if (serviceConfig['require_internal_access'] != null && serviceConfig['require_internal_access'] != "" ){
        println "require_internal_access - from service catalog is being used."
        internalAccess = serviceConfig['require_internal_access'].toString();
    }

    /*
    * Checking if deployment-env.yml file has any values for securityGroupIds & subnetIds
    * If values provided, using that and making internalAccess = true
    */
    if(userDefinedConfig['securityGroupIds'] && userDefinedConfig['subnetIds']){
        println "using subnetIds and securityGroupIds from deployment-env.yml file"
        internalAccess = 'true'
        addSecuritySettings(env, userDefinedConfig['subnetIds'], userDefinedConfig['securityGroupIds'])
    } else {
        /* Adding VPC configurations */
        if ((internalAccess != null && internalAccess.equals('true')) || internalAccess == null) {
            println "internal access is true..."
            println "Reading subnet id settings"

            def subnetConfig = JSON.readFile("${PROPS.SERVERLESS_CONFIG_DIRECTORY}/aws-subnet-configurations.json")
            println "subnetConfigurations--- $subnetConfig"
            def accounts = subnetConfig['accounts']

            for (account in accounts){
                if (account.id == serviceConfig['account'] && account.region == serviceConfig['region']) {
                    addSecuritySettings(env, account['subnets'], account['security_groups'])
                    break
                }
            }
        } else {
            if (isS3EventEnabled || isSQSEventEnabled || isDynamoDbEnabled || isStreamEnabled) {
                removeVpcDetails(env.REPO_NAME)
            }
        }
    }

    if(!isEventScheduled) {
        removeEventResources(env.REPO_NAME)
    }
    println "Completed Vpn settings.."
}

/*
* Function to add subnetIds and securityGroupIds
* @param: env - System environment file
* @param: subnets - subnetIds from deployment-env.yml
* @param: securityGroupIds - securityGroupIds from deployment-env.yml
*/
def addSecuritySettings(env, subnets, securityGroups) {
    def subnetIds = subnets
    def securityGroupIds = securityGroups

    println "subnetIds: " + subnetIds
    println "securityGroupIds: " + securityGroupIds

    sh("sed -i -- 's/securityGroupIds/securityGroupIdsOld/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")
    sh("sed -i -- 's/subnetIds/subnetIdsOld/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")

    println "writing subnetids to deployment-env.yml file"

    sh("echo '\nsubnetIds : $subnetIds\nsecurityGroupIds : $securityGroupIds\n' >> ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml")

    addVpcDetails(env.REPO_NAME)
}

def addEvents(def isScheduleEnabled, def isEc2EventEnabled, def isS3EventEnabled, def isStreamEnabled, def isDynamoDbEnabled, isSQSEventEnabled) {
    println "addEvents to serverless.yml file"
    def env = System.getenv()
    def sedCommand = "s/eventsDisabled:/events:/g";
    if(!isScheduleEnabled){
        sedCommand = sedCommand + "; /#Start:isScheduleEnabled/,/#End:isScheduleEnabled/d"
    }
    if(!isEc2EventEnabled){
        sedCommand = sedCommand + "; /#Start:isEc2EventEnabled/,/#End:isEc2EventEnabled/d"
    }
    if(!isS3EventEnabled){
        sedCommand = sedCommand + "; /#Start:isS3EventEnabled/,/#End:isS3EventEnabled/d"
        sedCommand = sedCommand + "; /#Start:S3CustomRole/,/#End:S3CustomRole/d"
    }
    if (!isSQSEventEnabled) {
        sedCommand = sedCommand + "; /#Start:isSQSEventEnabled/,/#End:isSQSEventEnabled/d"
    }
    if (!isStreamEnabled) {
        sedCommand = sedCommand + "; /#Start:isStreamEnabled/,/#End:isStreamEnabled/d"
    }
    if (!isDynamoDbEnabled) {
        sedCommand = sedCommand + "; /#Start:isDynamoDbEnabled/,/#End:isDynamoDbEnabled/d"
    }

    sh("sed -i -- '$sedCommand' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    println "------------------------DONE--------------"
}

def codeQualityCheck() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    events.sendStartedEvent("CODE_QUALITY_CHECK") 
    try {
        def projectKey = 'jazz'
        def env = System.getenv()    
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "codeQualityCheck", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))    
        def sonar = new SonarModule()
        def fortifyScan = new FortifyScanModule()

        runtimeValidation(env.REPO_NAME)
        sonar.configureForProject(env.REPO_BRANCH, projectKey)
        sonar.doAnalysis()
        fortifyScan.doScan(projectKey)
        
        events.sendCompletedEvent("CODE_QUALITY_CHECK")
    } catch (ex) {
        println "preBuildValidation failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("CODE_QUALITY_CHECK", ex.message, "FAILED")
        events.sendFailureEvent("CODE_QUALITY_CHECK")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "codeQualityCheck failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("codeQualityCheck failed: ", ex)    
    }   
}

/** Run validation based on runtime
*/
def runtimeValidation(repoName) {
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def configData = JSON.getValueFromPropertiesFile('configData')

    if(serviceConfig['runtime'].indexOf("nodejs") > -1) {
        println "skipping validations for ${serviceConfig['runtime']}"
        // sh "jshint *.js"
    } else if(serviceConfig['runtime'].indexOf("java") > -1) {
        println "running validations for ${serviceConfig['runtime']}"
        sh("mkdir -p ${configData.CODE_QUALITY.SONAR.CHECKSTYLE_LIB}")
        sh("wget ${configData.CODE_QUALITY.SONAR.CHECKSTYLE_LIB} -P ${PROPS.JAVA_CONFIG_DIRECTORY}")
        sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};java -cp ../../${PROPS.JAVA_CONFIG_DIRECTORY}/checkstyle-7.6-all.jar com.puppycrawl.tools.checkstyle.Main -c sun_checks.xml src")
    } else if(serviceConfig['runtime'].indexOf("python") > -1) {
        println "skipping validations for ${serviceConfig['runtime']}"
    } else if(serviceConfig['runtime'].indexOf("go") > -1){
        println "skipping validations for ${serviceConfig['runtime']}"
    }
}

/**	Build project based on runtime
*/
def buildLambda() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    events.sendStartedEvent("BUILD") 
    try {
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def env = System.getenv() 
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "buildLambda", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        println "installing dependencies for ${serviceConfig['runtime']}"
        if(serviceConfig['runtime'].indexOf("nodejs") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};npm install --save  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log 2>&1")
        } else if(serviceConfig['runtime'].indexOf("java") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn package --settings settings_cdp.xml  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log 2>&1")
        } else if(serviceConfig['runtime'].indexOf("python") > -1) {
            // install requirements.txt in library folder, these python modules will be a part of deployment package
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf library")
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};mkdir library")
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};touch library/__init__.py")

            if (serviceConfig['runtime'] == "python3.6" || serviceConfig['runtime'] == "python3.8") {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip3 install -r requirements.txt -t library  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log 2>&1")
            } else {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip install -r requirements.txt -t library  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log 2>&1")
            }
        } else if (serviceConfig['runtime'].indexOf("go") > -1 ){
            //Installing dependencies using dep ensure           
                def CI_PROJECT_DIR = env['CI_PROJECT_DIR']
                def workspacePath = "${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}"
                def GOPATH = "${CI_PROJECT_DIR}/${workspacePath}"
                sh("export GOPATH=${GOPATH};mkdir -p $GOPATH/{src,pkg,bin}")
                sh("export GOPATH=${GOPATH};mkdir -p $GOPATH/src/${env.REPO_NAME}")
                sh("export GOPATH=${GOPATH};cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config $GOPATH/bin/")                
                sh("export GOPATH=${GOPATH};rsync -a --exclude='.*' --exclude='src' ${workspacePath}/*  $GOPATH/src/${env.REPO_NAME}")
                sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME};dep ensure")
                sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME} && env GOOS=linux GOARCH=amd64 go build -o  $GOPATH/bin/main  *.go;pwd")
                // sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME};go test -short -coverprofile=bin/cov.out")
	    	    // sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME};go tool cover -func=bin/cov.out")
        }
        events.sendCompletedEvent("BUILD")
    } catch (ex) {
        println "preBuildValidation failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("BUILD", ex.message, "FAILED")
        events.sendFailureEvent("BUILD")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "Prebuild validation failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("preBuildValidation failed: ", ex)    
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
        def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
        def env = System.getenv()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "runUnitTestCases", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        println "Run test cases for ${serviceConfig['runtime']}"
        if(serviceConfig['runtime'].indexOf("nodejs") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};npm test >  ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log  2>&1")
        }else if(serviceConfig['runtime'].indexOf("java") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn test --settings settings_cdp.xml   > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log  2>&1")
        }else if(serviceConfig['runtime'].indexOf("python") > -1) {
            if(serviceConfig['runtime'] == 'python3.6' || serviceConfig['runtime'] == "python3.8") {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; python3 -m venv virtualenv")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; . virtualenv/bin/activate")
                def devtxtExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/requirements-dev.txt")
                if(devtxtExists) {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip3 install -r requirements-dev.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip3 freeze -r requirements-dev.txt")
                } else {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip3 install -r requirements.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip3 freeze -r requirements.txt")
                }
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip3 install pytest")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip3 install coverage")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage run -m pytest --ignore=library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log  2>&1")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage xml -i")
            } else {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip install virtualenv")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; virtualenv venv")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; . venv/bin/activate")
                def devtxtExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/requirements-dev.txt")
                if(devtxtExists) {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip install -r requirements-dev.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip freeze -r requirements-dev.txt")
                } else {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip install -r requirements.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip freeze -r requirements.txt")
                }
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}; pip install pytest")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip install coverage")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage run -m pytest --ignore=library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log  2>&1")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage xml -i")
            }
        }else if( serviceConfig['runtime'].indexOf("go") > -1){
            def CI_PROJECT_DIR = env['CI_PROJECT_DIR']
            def workspacePath = "${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}"
            def GOPATH = "${CI_PROJECT_DIR}/${workspacePath}"
            sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME} && go test ./... -coverprofile=cov.out")
            sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME} && go tool cover -func=cov.out")
   
        }
        def sonar = new SonarModule()
        sonar.cleanUpWorkspace()
        events.sendCompletedEvent("UNIT_TEST")
    } catch (ex) {
        println "preBuildValidation failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("UNIT_TEST", ex.message, "FAILED")
        events.sendFailureEvent("UNIT_TEST")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "Prebuild validation failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("preBuildValidation failed: ", ex)    
    }    
}

def removeEventResources(repoName){
    sh("sed -i -- '/#Start:resources/,/#End:resources/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
    sh("sed -i -- '/#Start:events/,/#End:events/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}

def removeVpcDetails(repoName) {
    println "Removing VPC details from policy file"
    sh("sed -i -- '/#Start:VPC/,/#End:VPC/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/policyFile.yml")
}

def addVpcDetails(repoName) {
    println "addVpndetails to serverless.yml file"
    sh("sed -i -- 's/vpcDisabled/vpc/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")	
}

def addCommonExcludes(repoName) {
    sh("sed -i -e '/exclusionList/r ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-common-excludes.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
    sh("sed -i -e '/exclusionList/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}

def isEnabled(serviceConfig, key) {
    if (serviceConfig.containsKey(key) && serviceConfig[key] != null && serviceConfig[key] != "null") {
        return true
    } else {
        return false
    }
}

def productionDeploy() {
    def utilModule = new UtilityModule()
    def events = new EventsModule()
    def qcpModule = new QcpModule()
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def status = 'failed'
    try {
        utilModule.showServiceEnvParams()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "productionDeploy", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
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
        awsDeploy()
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

def awsDeploy() {

    def props = JSON.getAllProperties()
    def env = System.getenv()
    def environmentLogicalId = props['environmentLogicalId']
    def serviceConfig = props['serviceConfig']
    def accountDetails = props['accountDetails']
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def lambdaEvents = new AWSLambdaEventsModule()
    def serviceConfigDataLoader = new ServiceConfigurationDataLoader()
    def utilModule = new UtilityModule()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def lambdaARN = null
    def credsId = null
    def deploymentAccountCreds = null;

    def _event
    events.sendStartedEvent("DEPLOY_TO_AWS")
    events.sendStartedEvent('UPDATE_ENVIRONMENT', "awsDeploy started.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_started")) 
    try {
        credsId = utilModule.generateCreds(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)
        /*
        * Getting temporary credentials for cross account role if not primary account
        */
        deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, env.AWS_DEFAULT_REGION);

        _event = "UPDATE_DEPLOYMENT_CONF"
        events.sendStartedEvent(_event)

        // Generate serverless yml file with domain added in function name
        println "Generate deployment env with domain"
        generateServerlessYml(props)
        addEventSourceToServerless(props, lambdaEvents)

        println "setting lambda execution role";
        setLambdaExecutionRole(props);
        events.sendCompletedEvent(_event)

        addStackTags(props)
        addCommonExcludes(env.REPO_NAME)
        def stackName =  "${serviceConfig['service']}--${serviceConfig['domain']}-${environmentLogicalId}" //service--domain-env 

        if (environmentLogicalId == "prod"){
            updateServerlessBucketInfo("PROD", props)
        } else if (environmentLogicalId == "stg"){
            updateServerlessBucketInfo("STG", props)
        } else {
            // For backward compatibility. Reuse the dev s3 bucket for deployment.
            updateServerlessBucketInfo("DEV", props)
        }

        // Ref: https://ccoe.docs.t-mobile.com/aws/reference/hostname_tagging_guidelines/#environments
        def environmentName = getEnvironmentTag(environmentLogicalId)
        handleServerlessDeployment(events, lambdaEvents, props, deploymentAccountCreds)
        
        // attach tags to log group
        attachTagstoLogGroup(serviceConfig, environmentLogicalId, deploymentAccountCreds, environmentName, props['commitSha'],props['deploymentRegion'])
        if(serviceConfig['eventScheduleRate']) {
            def eventsArns = getEventsArn(serviceConfig, environmentLogicalId, deploymentAccountCreds)
            for (def i = 0; i < eventsArns.size(); i++) {
                def arn = eventsArns[i]
                events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", arn, "cloudwatch_event", serviceConfig['owner']));
            }
        }

        lambdaARN = getLambdaARN(stackName, deploymentAccountCreds, serviceConfig);
        events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", lambdaARN.arn, "lambda", serviceConfig['owner']));

        logGroupARN = getLogARN(lambdaARN.accountId, lambdaARN.region, lambdaARN.functionName);
        events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", logGroupARN, "log_group", serviceConfig['owner']));

        // Update Lambda settings, allowing to be triggered by S3 events
        def isLambdaUpdateRequired = JSON.getValueFromPropertiesFile('isLambdaUpdateRequired')? JSON.getValueFromPropertiesFile('isLambdaUpdateRequired') : false
        println "isLambdaUpdateRequired - $isLambdaUpdateRequired"
        if (serviceConfig['event_source_s3'] && isLambdaUpdateRequired) {
            def event_arn = constructEventArn (serviceConfig['event_source_s3'], serviceConfig)
            def event_source_s3 = lambdaEvents.getEventResourceNamePerEnvironment(event_arn, environmentLogicalId, "-")
            lambdaEvents.updateLambdaPermissionAndNotification(lambdaARN.arn, event_source_s3, serviceConfig['event_action_s3'], deploymentAccountCreds, serviceConfig['region'])
        }

        // Publish role as a new asset if applicable
        if (serviceConfig['event_source_dynamodb'] || serviceConfig['event_source_sqs'] || serviceConfig['event_source_s3'] || serviceConfig['event_source_kinesis']) {
            def roleName =  "jazz-${serviceConfig['service']}--${serviceConfig['domain']}-${environmentLogicalId}"
            def roleArn = lambdaEvents.getRoleArn(roleName, deploymentAccountCreds)
            if (roleArn != null) {
                events.sendCompletedEvent('CREATE_ASSET', null,  generateAssetMap("aws", roleArn , "iam_role", serviceConfig['owner']));
            }
        }

        if (serviceConfig['domain'] && serviceConfig['domain'] == "jazz") {
            serviceConfigDataLoader.setKinesisStream(props, deploymentAccountCreds)   
        }

        _event = 'UPDATE_LOGGING_CONF'
        events.sendStartedEvent(_event)
        cloudWatchLogGroupName = createSubscriptionFilters(deploymentAccountCreds,  props);
        events.sendCompletedEvent(_event)

        if(!accountDetails.PRIMARY){
            def primaryAccountData = utilModule.getAccountInfoPrimary(props['configData']);
            def primaryAccountValue = primaryAccountData.ACCOUNTID;
            def functionName = lambdaARN.functionName
            
            addPermission(functionName, primaryAccountValue, deploymentAccountCreds, serviceConfig);
        } 

        echoServiceInfo(lambdaARN.arn)
        
        qcpModule.sendQCPEvent("Post", "success")
        events.sendCompletedEvent("DEPLOY_TO_AWS")
        events.sendCompletedEvent('UPDATE_ENVIRONMENT', "awsDeploy completed.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_completed", null, null, lambdaARN.arn)) 
        events.sendCompletedEvent('UPDATE_DEPLOYMENT', "awsDeploy completed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.successful))
    } catch (ex) {
        println "awsDeploy failed: ${ex.message}"
        if (_event){
            events.sendFailureEvent(_event)
        }
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("DEPLOY_TO_AWS", ex.message, "FAILED")
        events.sendFailureEvent("DEPLOY_TO_AWS")
        events.sendFailureEvent('UPDATE_ENVIRONMENT', "awsDeploy failed.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_failed")) 
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "awsDeploy failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("awsDeploy failed: ", ex)    
    } finally {
        resetCredentials(deploymentAccountCreds)
    }
}

/**
	Generate the serverless yml file with domain name in lambda name
*/
def generateServerlessYml(props) {    
    def serviceConfig = props['serviceConfig']
    def configData = props['configData']
    def environmentLogicalId = props['environmentLogicalId']
    def env = System.getenv() 
    def stackName =  "${serviceConfig['service']}--${serviceConfig['domain']}"

    sh("sed -i -- 's/name: \${self:domain}_\${self:serviceTag}_\${self:custom.myStage}/name: ${serviceConfig['domain']}_${serviceConfig['service']}_${environmentLogicalId}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml" )
    sh("sed -i -- 's/service: \${file(deployment-env.yml):service}/service: ${stackName}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{region}/${serviceConfig['region']}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    //Adding log retention policy
    sh("sed -i -- 's/{logRetentionInDays}/${configData.JAZZ.LOGS.CLOUDWATCH_LOG_RETENTION_DAYS}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
}

def addEventSourceToServerless(props, lambdaEvents) {
    def serviceConfig = props['serviceConfig'] 
    def environmentLogicalId = props['environmentLogicalId']
    def env = System.getenv()
   
    if (serviceConfig['event_source_s3']) {
        def event_arn = constructEventArn (serviceConfig['event_source_s3'], serviceConfig) 
        def event_source_s3 =  lambdaEvents.getEventResourceNamePerEnvironment(event_arn, environmentLogicalId, "-")
        def event_s3_arn = "arn:aws:s3:::${event_source_s3}"
        sh("sed -i -- 's/{event_source_s3}/${event_source_s3}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -- 's/{event_action_s3}/${serviceConfig['event_action_s3']}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -- 's|{event_s3_arn}|${event_s3_arn}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/policyFile.yml")
    }

    if (serviceConfig['event_source_sqs']) {
        def event_arn = constructEventArn (serviceConfig['event_source_sqs'], serviceConfig) 
        def event_source_sqs =  lambdaEvents.getSqsQueueName(event_arn, environmentLogicalId)
        def event_sqs_arn = lambdaEvents.getEventResourceNamePerEnvironment(event_arn, environmentLogicalId, "_")
        sh("sed -i -- 's/{event_source_sqs}/${event_source_sqs}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -- 's|{event_sqs_arn}|${event_sqs_arn}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -- 's|{event_sqs_arn}|${event_sqs_arn}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/policyFile.yml")
        sh("sed -i -- 's/resourcesDisabled/resources/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }

    if (serviceConfig['event_source_kinesis']) {
        def event_arn = constructEventArn (serviceConfig['event_source_kinesis'], serviceConfig) 
        def event_source_kinesis = lambdaEvents.splitAndGetResourceName(event_arn, environmentLogicalId)
        sh("sed -i -- 's/resourcesDisabled/resources/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -- 's|{event_source_kinesis}|${event_source_kinesis}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }

    if (serviceConfig['event_source_dynamodb']) {
        def event_arn = constructEventArn (serviceConfig['event_source_dynamodb'], serviceConfig) 
        def event_source_dynamodb = lambdaEvents.splitAndGetResourceName(event_arn, environmentLogicalId)
        sh("sed -i -- 's/resourcesDisabled/resources/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -- 's|{event_source_dynamodb}|${event_source_dynamodb}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }
}

def constructEventArn (arn, serviceConfig) {
    try {
         if( arn != null ) {
            arn = arn.replaceAll("\\{region\\}", "${serviceConfig['region']}")
            arn = arn.replaceAll("\\{account\\}", "${serviceConfig['account']}")
        } else {
            throw new Exception("Arn Templates not found for the service : ${serviceConfig['service']}")
        }
    } catch (ex) {
        throw new Exception("Exception occured while replacing account, region in event source. ${ex.message}")
    }
   return arn;
}

def setLambdaExecutionRole(props) {
    def userDefinedConfig = props['userDefinedConfig']
    def serviceConfig = props['serviceConfig']
    def userRole = userDefinedConfig['iamRoleARN']
    def env = System.getenv()

    if (userRole && userRole.trim()) {
        // If user supplies non-empty custom role, use it!
        sh("sed -i -- 's|DEFAULT_LAMBDA_EXE_ROLE|${userRole}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    } else if (serviceConfig['event_source_s3'] || serviceConfig['event_source_dynamodb'] || serviceConfig['event_source_sqs'] || serviceConfig['event_source_kinesis']) {
        // If no role is supplied by the user, use the custom event role created by Jazz
        sh("sed -i -- 's/resourcesDisabled/resources/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
        sh("sed -i -e 's|DEFAULT_LAMBDA_EXE_ROLE|customEventRole|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    } else {
        // Use default role otherwise
        sh("sed -i -- 's|DEFAULT_LAMBDA_EXE_ROLE|${props['deploymentRole']}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    }
}

/**
*   Generate tag for a specific environment based on the id
*
*   Ref: https://ccoe.docs.t-mobile.com/aws/reference/hostname_tagging_guidelines/#environments
*/
def getEnvironmentTag(env){
    def envTag = "Non-production"
    if (env == "prod"){
      envTag = "Production"
    }
    return envTag
}

def addStackTags(props) {

    def serviceConfig = props['serviceConfig']
    def env = System.getenv() 
    def environmentLogicalId = props['environmentLogicalId']
    def configData = props['configData']
    def gitCommitHash = props['commitSha']

    println "gitCommitHash:- $gitCommitHash"
    
    println "Adding stack tags to serverless.yml"
    sh("sed -i -- 's/{applicationTag}/${serviceConfig.appTag}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{applicationId}/${serviceConfig.appId}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{platform}/${configData.INSTANCE_PREFIX}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{serviceTag}/${serviceConfig.service}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{domain}/${serviceConfig.domain}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{environmentTag}/${getEnvironmentTag(environmentLogicalId)}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{environmentId}/$environmentLogicalId/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{owner}/${serviceConfig.created_by}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
    sh("sed -i -- 's/{gitCommitHash}/$gitCommitHash/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
}

/**
	Reuse the dedicated S3 bucket for sls deployment
*/
def updateServerlessBucketInfo(deploymentStage, props) {
    def s3BucketName;
    def accountDetails = props['accountDetails']
    def serviceConfig = props['serviceConfig']
    def env = System.getenv()
    for (item in accountDetails.REGIONS) {
        if(item.REGION == serviceConfig.region){
            s3BucketName = item.S3[deploymentStage]
        }
    }
    sh("sed -i -- 's/{s3bucketValue}/${s3BucketName}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
}

def handleServerlessDeployment(events, lambdaEvents, props, credsId) {
    def serviceConfig = props['serviceConfig']
    def environmentLogicalId = props['environmentLogicalId']
    def env = System.getenv()
    def serviceName =  env.REPO_NAME
    def isEventScheduled = props['isEventScheduled']

    def deployOutput = serverlessDeploy(environmentLogicalId, serviceName, credsId);
    println "deployOutput- $deployOutput"
    if (deployOutput != 'success') {
        if (serviceConfig['event_source_s3']) {
            handleS3BucketError(lambdaEvents, deployOutput, serviceConfig, environmentLogicalId, isEventScheduled, serviceName, credsId)
        } else if (serviceConfig['event_source_sqs']) {
            handleSqsError(lambdaEvents, deployOutput, props['configData'], serviceConfig, environmentLogicalId, serviceName, credsId)
        } else if (serviceConfig['event_source_kinesis']) {
            handleKinesisStreamError(lambdaEvents, deployOutput, serviceConfig['event_source_kinesis'], environmentLogicalId, serviceName, credsId, props['configData'], serviceConfig)
        } else if (serviceConfig['event_source_dynamodb']) {
            handleDynamoDbStreamError(lambdaEvents, events, deployOutput, props['configData'], serviceConfig, environmentLogicalId, serviceName, credsId)
        } else {
            handleDeploymentErrors(deployOutput, serviceName, environmentLogicalId, credsId)
        }
    } else {
        if (serviceConfig['event_source_s3']) {
            def event_arn = constructEventArn(serviceConfig['event_source_s3'], serviceConfig)
            def bucket_arn = "arn:aws:s3:::${event_arn}"
            def s3_bucket_arn = lambdaEvents.getEventResourceNamePerEnvironment(bucket_arn, environmentLogicalId, "-")
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", s3_bucket_arn, "s3", serviceConfig['owner']));
        } else if (serviceConfig['event_source_sqs']) {
            def event_arn = constructEventArn(serviceConfig['event_source_sqs'], serviceConfig)
            def event_source_sqs_arn =  lambdaEvents.getEventResourceNamePerEnvironment(event_arn, environmentLogicalId, "_")
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws",  event_source_sqs_arn, "sqs", serviceConfig['owner']));
        } else if (serviceConfig['event_source_kinesis']) {
            def event_arn = constructEventArn(serviceConfig['event_source_kinesis'], serviceConfig)
            def event_source_kinesis_stream_arn = lambdaEvents.getEventResourceNamePerEnvironment(event_arn, environmentLogicalId, "_")
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", event_source_kinesis_stream_arn, "kinesis_stream", serviceConfig['owner']));
        } else if (serviceConfig['event_source_dynamodb']) {
            def event_arn = constructEventArn(serviceConfig['event_source_dynamodb'], serviceConfig)
            def dynamodbTableName = lambdaEvents.splitAndGetResourceName(event_arn, environmentLogicalId)
            def stream_details = lambdaEvents.getDynamoDbStreamDetails(dynamodbTableName, serviceConfig.region, credsId)
            def event_source_dynamodb_arn = lambdaEvents.getEventResourceNamePerEnvironment(event_arn, environmentLogicalId, "_")
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", event_source_dynamodb_arn, "dynamodb", serviceConfig['owner']));
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", stream_details.StreamArn, "dynamodb_stream", serviceConfig['owner']));
        }
    }
}

def serverlessDeploy(env, serviceName, credsId){
    def envs = System.getenv()
    def ip = sh("curl ifconfig.co")
    println "Build agent IP: ${ip}"
    try{
        println sh("ls -al ${PROPS.WORKING_DIRECTORY}/${serviceName}")
        println "----------------------serverless.yml-----------------------------"
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${serviceName}/serverless.yml") 
        println "----------------------deployment-env.yml----------------------"
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${serviceName}/deployment-env.yml") 
        println "-----------------------------------------------------"
        println "deploying $serviceName to $env"
        def listValue = sh("aws configure list --profile ${credsId}")
        println "listValue: ${listValue}"
        def identities = sh("aws sts get-caller-identity")
        println "identities: ${identities}"
        sh("cd ${PROPS.WORKING_DIRECTORY}/${serviceName};serverless deploy --force --stage $env --aws-profile ${credsId} -v > ../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log" )
        println "==================================================="
        println sh("cat ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log")
        println "==================================================="
        println "-----------------DEPLOYMENT DONE-------------------"
        return "success"
    } catch (ex) {
        println "Serverless deployment failed due to ${ex.message}"
    }
    def outputLog = new java.io.File("${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log").text
    println "serverless deployment log $outputLog"
    return outputLog
}

def handleS3BucketError(lambdaEvents, deployOutput, serviceConfig, environmentLogicalId, isEventScheduled, serviceName, credsId) {
  def s3BucketName = constructEventArn(serviceConfig['event_source_s3'], serviceConfig)
  s3BucketName = lambdaEvents.getEventResourceNamePerEnvironment(s3BucketName, environmentLogicalId, "-")
  def s3BucketErrString = "CloudFormation - CREATE_FAILED - AWS::S3::Bucket - "
  if (deployOutput.contains(s3BucketErrString)) {
    if (lambdaEvents.checkS3BucketExists(s3BucketName, credsId)) {
      isLambdaUpdateRequired = true
      JSON.setValueToPropertiesFile('isLambdaUpdateRequired', isLambdaUpdateRequired)
      lambdaEvents.removeS3EventsFromServerless(isEventScheduled, serviceName)
      deleteAndRedeployService(environmentLogicalId, serviceName, credsId)
    } else {
      throw new Exception("Error occured while accessing the s3 bucket")
    }
  } else {
      handleDeploymentErrors(deployOutput, serviceName, environmentLogicalId, credsId)
  }
}

def handleSqsError(lambdaEvents, deployOutput, configData, serviceConfig, environmentLogicalId, serviceName, credsId) {
  def event_source_sqs_arn = constructEventArn(serviceConfig['event_source_sqs'], serviceConfig)
  def sqsErrString = "CloudFormation - CREATE_FAILED - AWS::SQS::Queue - "
  if (deployOutput.contains(sqsErrString)) {
    def event_source_sqs = lambdaEvents.getSqsQueueName(event_source_sqs_arn, environmentLogicalId)
    event_source_sqs_arn =  lambdaEvents.getEventResourceNamePerEnvironment(event_source_sqs_arn, environmentLogicalId, "_")
    def lambda_arn = "arn:aws:lambda:${serviceConfig['region']}:${serviceConfig['account']}:function:${configData.INSTANCE_PREFIX}-${serviceConfig['domain']}-${serviceConfig['service']}-${environmentLogicalId}"
    if (lambdaEvents.checkSqsQueueExists(event_source_sqs, credsId)) {
      if(lambdaEvents.checkIfDifferentFunctionTriggerAttached(event_source_sqs_arn, lambda_arn, credsId)){
        throw new Exception("Queue contains a different function trigger already. Please remove the existing function trigger and try again.")
      }
      lambdaEvents.updateSqsResourceServerless(serviceName)
      deleteAndRedeployService(environmentLogicalId, serviceName, credsId)
    } else {
       throw new Exception("Error occured while accessing the SQS queue")
    }
  } else {
      handleDeploymentErrors(deployOutput, serviceName, environmentLogicalId, credsId)
  }
}

def handleKinesisStreamError(lambdaEvents, deployOutput, event_source_kinesis_stream_arn, environmentLogicalId, serviceName, credsId, configData, serviceConfig) {
  def kinesisStreamErrString = "CloudFormation - CREATE_FAILED - AWS::Kinesis::Stream - "
  event_source_kinesis_stream_arn = constructEventArn(event_source_kinesis_stream_arn, serviceConfig)
  def event_source_kinesis = lambdaEvents.splitAndGetResourceName(event_source_kinesis_stream_arn, environmentLogicalId)
  event_source_kinesis_stream_arn = lambdaEvents.getEventResourceNamePerEnvironment(event_source_kinesis_stream_arn, environmentLogicalId, "_")
  def lambda_arn = "arn:aws:lambda:${serviceConfig['region']}:${serviceConfig['account']}:function:${configData.INSTANCE_PREFIX}-${serviceConfig['domain']}-${serviceConfig['service']}-${environmentLogicalId}"
  if (deployOutput.contains(kinesisStreamErrString)) {
    if (lambdaEvents.checkKinesisStreamExists(event_source_kinesis, credsId)) {
      if(lambdaEvents.checkIfDifferentFunctionTriggerAttached(event_source_kinesis_stream_arn, lambda_arn, credsId)){
        throw new Exception("Kinesis stream contains a different function trigger already. Please remove the existing function trigger and try again.")
      }
      lambdaEvents.updateKinesisResourceServerless(event_source_kinesis_stream_arn, serviceName)
      deleteAndRedeployService(environmentLogicalId, serviceName, credsId)
    } else {
       throw new Exception("Error occured while accessing the Kinesis stream")
    }
  } else {
      handleDeploymentErrors(deployOutput, serviceName, environmentLogicalId, credsId)
  }
}

def handleDynamoDbStreamError (lambdaEvents, events, deployOutput, configData, serviceConfig, environmentLogicalId, serviceName, credsId) {
  def dynamodbTableErrString = "CloudFormation - CREATE_FAILED - AWS::DynamoDB::Table - "
  def event_source_dynamodb_table_arn =  constructEventArn(serviceConfig['event_source_dynamodb'], serviceConfig)
  def event_source_dynamodb = lambdaEvents.splitAndGetResourceName(event_source_dynamodb_table_arn, environmentLogicalId)
  event_source_dynamodb_table_arn = lambdaEvents.getEventResourceNamePerEnvironment(event_source_dynamodb_table_arn, environmentLogicalId, "_")
  def lambda_arn = "arn:aws:lambda:${serviceConfig['region']}:${serviceConfig['account']}:function:${configData.INSTANCE_PREFIX}-${serviceConfig['domain']}-${serviceConfig['service']}-${environmentLogicalId}"
  if (deployOutput.contains(dynamodbTableErrString)) {
    if (lambdaEvents.checkDynamoDbTableExists(event_source_dynamodb, serviceConfig.region, credsId)) {
      def stream_details = lambdaEvents.getDynamoDbStreamDetails(event_source_dynamodb, serviceConfig.region, credsId)
      if (!stream_details.isNewStream) {
        if(lambdaEvents.checkIfDifferentFunctionTriggerAttached(stream_details.StreamArn, lambda_arn, credsId)){
             throw new Exception("Dynamodb stream contains a different function trigger already. Please remove the existing function trigger and try again.")
        }
      } else {
        events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", stream_details.StreamArn, "dynamodb_stream", serviceConfig['owner']));
      }
      lambdaEvents.updateDynamoDbResourceServerless(stream_details.StreamArn, serviceName)
      deleteAndRedeployService(environmentLogicalId, serviceName, credsId)
    } else {
        throw new Exception("Error occured while accessing the dynamodb stream")
    }
  } else {
      handleDeploymentErrors(deployOutput, serviceName, environmentLogicalId, credsId)
  }
}

def deleteAndRedeployService(env, serviceName, credsId){
    def utilModule = new UtilityModule()
    def props = JSON.getAllProperties()
    def serviceConfig = props['serviceConfig']
    def configData = props['configData']
    def cfstack = "${serviceConfig['service']}--${serviceConfig['domain']}-${env}"
    try {
        sh("cd ${PROPS.WORKING_DIRECTORY}/${serviceName};serverless remove --stage $env --aws-profile ${credsId};pwd" )
    } catch (ex) {
        println "serverless remove failed"
        // GO FOR CF DELETE
        utilModule.deletecfstack(credsId, cfstack, serviceConfig['region'])
    }
    def redeployOutput = serverlessDeploy(env, serviceName, credsId)
    if (redeployOutput != 'success') {
         throw new Exception("Exception occured while serverless deployment to ${env} environmentLogicalId")
    }
}

def handleDeploymentErrors(deployOutput, serviceName, env, credsId) {
    def utilModule = new UtilityModule()
    def logGroupErrString = "CloudFormation - CREATE_FAILED - AWS::Logs::LogGroup - HandlerLogGroup";
    if (utilModule.checkCfResourceFailed(credsId) || utilModule.checkCfFailed(deployOutput)) {
        deleteAndRedeployService(env, serviceName, credsId)
    } else if (deployOutput.contains(logGroupErrString)) {
        def lambda = "/aws/lambda/${serviceName}-${env}"
        if (isExistingLogGroup(lambda, credsId)) {
            println "Exception occured while serverless deploy : $logGroupErrString, Removing existing log group"
            removeLogGroup(lambda, credsId)
            deleteAndRedeployService(env, serviceName, credsId);
        } else {
             throw new Exception("Unable to find existing log groups")
        }
    } else {
        println "Error occured while deploying $serviceName to $env environmentLogicalId : $deployOutput"
         throw new Exception("Exception occured while serverless deployment to ${env} environmentLogicalId")
    }
}

def isExistingLogGroup (groupName, credsId){
    def isExistingLogGrp = false
    def filterLogs = sh("aws logs describe-log-groups --log-group-name-prefix $groupName  --profile ${credsId}", true)

    def filterLogsJSON = JSON.parseJson(filterLogs)
    def logGroups = filterLogsJSON.logGroups
    println "logGroups : $logGroups"
    if(logGroups.size() > 0  && logGroups[0].logGroupName == groupName){
        isExistingLogGrp = true
    }
    return isExistingLogGrp
}

def removeLogGroup(groupName, credsId) {
    println "Removing log group $groupName"
    sh("aws logs delete-log-group --log-group-name $groupName --profile ${credsId};pwd")
    println "successfully removed log group $groupName"
}

def generateAssetMap(provider, providerId, type, created_by) {

    def serviceCtxMap = [
        provider: provider,
        provider_id: providerId,
        type: type,
        created_by: created_by
    ]

    return serviceCtxMap;
}

def getLambdaARN (stackName, credsId, serviceConfig) {
    def ARN = "";
    try {
        def cloudformation_resources = "";
        cloudformation_resources = sh("aws cloudformation describe-stacks  --stack-name ${stackName} --profile ${credsId} --region ${serviceConfig.region};pwd", true)


        println "cloudformation_resources - $cloudformation_resources"
        if(cloudformation_resources) {
            def parsedObject = JSON.parseJson(cloudformation_resources);
            def outputs = parsedObject.Stacks[0].Outputs;

            for (output in outputs) {
                if (output.OutputKey == "HandlerLambdaFunctionQualifiedArn" || output.OutputKey == "HandlerLambdaFunctionArn") {
                    ARN = output.OutputValue;
                    break;
                }
            }
        } else {
            throw new Exception("getLambdaARN- failed: ")
        }
        
        println "Lambda ARN: $ARN";
    } catch(ex) {
         throw new Exception ("getLambdaARN - failed- " , ex)
    }

    if(ARN) {
        def tokens = ARN.split(':')
        def version
        def alias
        if (tokens[7].substring(0,1).isNumber()) {
            version = tokens[7]
            alias = ""
        } else {
            version = ""
            alias = tokens[7]
        }
        if (alias || version) {
            ARN = ARN.substring(0, ARN.lastIndexOf(':'));
        }
        return [ arn: ARN, region: tokens[3], accountId: tokens[4], functionName: tokens[6], version: version, alias: alias ]
    }    
}

def getLogARN(accountId, region, functionName) {
    return "arn:aws:logs:${region}:${accountId}:log-group:/aws/lambda/${functionName}:*"
}

def attachTagstoLogGroup(serviceConfig, environmentLogicalId, credsId, environmentName, gitCommitHash, deploymentRegion){
    // add tags to log group
    try {
        // changing variable names appropriately
        def functionName = "${serviceConfig['domain']}_${serviceConfig['service']}_${environmentLogicalId}"
        def logGroupName = "/aws/lambda/${functionName}"
        def serviceName = serviceConfig['service']
        def domain = serviceConfig['domain']
        def applicationId = serviceConfig['appId']
        def owner = serviceConfig['owner']
        def application = serviceConfig['appTag']
        def platform = 'jazz'
        def tagResult = sh("aws logs tag-log-group --log-group-name ${logGroupName} --tags '{\"Service\":\"${serviceName}\", \"Domain\":\"${domain}\", \"EnvironmentId\":\"${environmentLogicalId}\", \"FunctionName\":\"${functionName}\", \"ApplicationId\":\"${applicationId}\", \"Owner\":\"${owner}\", \"Application\":\"${application}\", \"GitCommitHash\":\"${gitCommitHash}\", \"Environment\":\"${environmentName}\", \"Platform\":\"${platform}\"}' --profile ${credsId}  --region ${deploymentRegion}", true)
        println "Set tags result: $tagResult"

    } catch (Exception ex) {
        println "Error occured while attaching tags to logGroup ${ex}"
    } 
}

def getEventsArn(serviceConfig, environmentLogicalId, credsId) {
    def eventsArn = []
    try {
        def lambdaPolicyTxt = sh("aws lambda get-policy --region ${serviceConfig['region']} --function-name ${serviceConfig['domain']}_${serviceConfig['service']}_${environmentLogicalId} --profile ${credsId};pwd",  true)
        println "lambdaPolicyTxt : $lambdaPolicyTxt"
        def policyLists = null
        if(lambdaPolicyTxt) {
            policyLists = JSON.parseJson(JSON.parseJson(lambdaPolicyTxt).Policy)

            println "policyLists: $policyLists"
            if(policyLists) {
                for (st in policyLists.Statement) {
                    if (st.Principal.Service == "events.amazonaws.com") {
                    if(st.Condition.ArnLike["AWS:SourceArn"]) {
                            eventsArn.push(st.Condition.ArnLike["AWS:SourceArn"])
                        }
                    }
                }
            }
        }
        return eventsArn
    } catch (ex) {
        // Skip the 'ResourceNotFoundException' when deploying first time. Workflow can't fail here.
        println  "Can't fetch the events policy configurations for lambda. ${ex.message}"
        return []
    }
}

def echoServiceInfo(arn) {
    println "======================================================================="
    println "Function deployed: $arn"
    println "========================================================================"
}

/**
	Create the subscription filters and loggroup if not existing
**/
def createSubscriptionFilters( credsId, props) {
    
    def serviceConfig = props['serviceConfig']
    def accountDetails = props['accountDetails']
    def configData = props['configData']

    def logStream = configData.AWS.KINESIS_LOGS_STREAM
    def logStreamersList = configData.JAZZ.LOG_STREAMERS.values()
    def lambda = "/aws/lambda/${serviceConfig['domain']}_${serviceConfig['service']}_${props['environmentLogicalId']}"
    def isLogStreamerService = false
    for (item in logStreamersList) {
        def streamerService = item.tokenize(":")[6].replaceAll("-(dev|stg|prod)\$","")
        if (streamerService == serviceConfig['service']) {
            isLogStreamerService = true
            break;
        }
    }

    if (!logStream) {
        throw new Exception("Invalid logs streamer configuration in Jenkins. Provide a valid log streamer arn")
    }
  	// Add subscription filter only to cloudwatch log groups for non streamer services.
  	if (!isLogStreamerService) {
        try {
            filter_json = sh("aws logs describe-subscription-filters  --log-group-name \"${lambda}\" --region ${props['deploymentRegion']} --profile ${credsId};pwd", true)
            // println "${filter_json}"
            def resultJson = JSON.parseJson(filter_json)
            filtername = resultJson.subscriptionFilters[0].filterName
            println "removing existing filter... $filtername"
            if(filtername != "" && !filtername.equals(lambda)) {
                sh("aws logs delete-subscription-filter  --log-group-name \"${lambda}\" --filter-name \"${filtername}\" --region ${props['deploymentRegion']} --profile ${credsId};pwd" )
            }
        } catch(Exception ex) {} // ignore error if not created yet

          try {
              //updating the policy if the deployment account is not primary
              for (item in configData.AWS.ACCOUNTS) {
                  if(item.ACCOUNTID == serviceConfig.account){
                      if(!item.PRIMARY){
                          def destARN
                          for (data in accountDetails.REGIONS) {
                              if(data.REGION == serviceConfig.region){
                                  destARN = data.LOGS.PROD
                              }
                          }
                          sh("aws logs put-subscription-filter  --log-group-name \"${lambda}\" --filter-name \"${lambda}\" --filter-pattern \"\" --destination-arn \"${destARN}\" --region ${props['deploymentRegion']} --profile ${credsId}")
                      } else {
                          sh("aws logs put-subscription-filter  --log-group-name \"${lambda}\" --filter-name \"${lambda}\" --filter-pattern \"\" --destination-arn \"${logStream}\" --region ${props['deploymentRegion']} --role-arn ${configData.AWS.LAMBDA_EXECUTION_ROLE} --profile ${credsId}")
                      }
                  }
              }
          } catch (Exception ex) {
              println "error occured: ${ex.message}" 
          }
    }
    return lambda;
}

/**
 * Send email to the recipient with the build status and any additional text content
 * Supported build status values = STARTED, FAILED & COMPLETED
 * @return
 */
def sendEmail(props, buildStatus, emailContent) {
    def serviceConfig = serviceConfig['serviceConfig'] 
    if (serviceConfig.domain != "jazz") {
        println "Sending build notification to ${serviceConfig['created_by']}"
        def body_subject = ''
        def body_text = ''
        def cc_email = ''
        def body_html = ''
        if (buildStatus == 'STARTED') {
            println "email status started"
            body_subject = 'Jazz Build Notification: Deployment STARTED for service: ' + serviceConfig['service']
        } else if (buildStatus == 'FAILED') {
            println "email status failed"
            body_subject = 'Jazz Build Notification: Deployment FAILED for service: ' + serviceConfig['service']
            body_text = body_text + '\n\nFor more details, please click this link: ' + props['pipelineUrl']
        } else if (buildStatus == 'COMPLETED') {
            body_subject = 'Jazz Build Notification: Deployment COMPLETED successfully for service: ' + serviceConfig['service']
        } else {
            println "Unsupported build status, nothing to email.."
            return
        }
        if (emailContent != '') {
            body_text = body_text + '\n\n' + emailContent
        }
        def fromStr = 'Jazz Admin <' + configLoader.JAZZ.ADMIN + '>'
        body = JsonOutput.toJson([
                    from: fromStr,
                    to: serviceConfig['created_by'],
                    subject: body_subject,
                    text: body_text,
                    cc: cc_email,
                    html: body_html
                ])

        try {
            def sendMail = sh("curl -X POST \
                            ${props['apiBaseUrl']}/email \
                            -k -v -H \"Authorization: ${props['authToken']}\" \
                            -H \"Content-Type: application/json\" \
                            -d \'${body}\'", true)
            def responseJSON = JSON.parseJson(sendMail)
            if (responseJSON.data) {
                println "successfully sent e-mail to ${serviceConfig['created_by']}"
            } else {
                println "exception occured while sending e-mail: $responseJSON"
            }
        } catch (e) {
            println "Failed while sending build status notification"
        }
    }
}

def resetEnvironmentSpecificConfigurations() {
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
    

def addPermission(functionName, primaryAccountValue, credsId, serviceConfig){
    try {
        sh("aws lambda remove-permission --function-name ${functionName} --region ${serviceConfig.region} --statement-id ${functionName}_invoke --profile ${credsId}")
    } catch(Exception ex) {
        // ignore if the policy is not there
    } 
    /* Adding permission to invoke the function created in secondary account*/
    sh("aws lambda add-permission --function-name ${functionName} --region ${serviceConfig.region} --statement-id ${functionName}_invoke --action 'lambda:InvokeFunction' --principal ${primaryAccountValue} --profile ${credsId}")
}

def resetCredentials(credsId) {
    println "resetting AWS credentials"
    sh("aws configure set profile.${credsId}.aws_access_key_id XXXXXXXXXXXXXXXXXXXXXXXXXX")
    sh("aws configure set profile.${credsId}.aws_secret_access_key XXXXXXXXXXXXXXXXXXXXXX")
}

def configureProductionDeploy() {
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

        // preparing for production deploy
        def fileName = "deployment-env.prod.yml"
        def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/${fileName}")
        if (fExists)
        {
            sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.prod.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml") 
        }
        else
        {
            sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deploymentEnvOriginal.yml ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/deployment-env.yml") 
        }

        checkDeploymentConfiguration(environmentDeploymentMetadata)
        loadServerlessConfig()
        events.sendStartedEvent('CREATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        approvalForProductionDeploy()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "Sent email for approval", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.approval_pending))
        
        //Setting current time for checking the timeput of production deploy
        def currentTimeValue = System.currentTimeMillis();
        JSON.setValueToPropertiesFile('approvalRequestRaisedTime', currentTimeValue)
    } catch (ex) {
        println "configureProductionDeploy- failed: ${ex.message}"
        throw new Exception("configureProductionDeploy- failed: ", ex)
    }    
}

def approvalForProductionDeploy () {
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
        slack.sendSlackNotification("REQUEST_ENVIRONMENT_ARCHIVAL", ex.message, "FAILED")
        events.setFailureEvent('REQUEST_ENVIRONMENT_ARCHIVAL')
        throw new Exception("Failed to trigger archival flow: ", ex)
    }
}
