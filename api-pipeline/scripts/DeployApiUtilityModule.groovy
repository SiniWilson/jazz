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
* DeployApiUtilityModule.groovy
* @author: Dimple
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

def initialize () {
    println "Initializing api pipeline."
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
        println "=               PRE BUILD VALIDATION                       ="
        println "============================================================"

        preBuildValidation()
        
    } catch (ex) {
        println "Exception occured while initializing the api pipeline: + $ex"
        throw new Exception("Exception occured while initializing the api pipeline", ex)
    }
}

def configDeployment() {
    try {
        def events = new EventsModule();
        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
        def env = System.getenv()
        def qcpModule = new QcpModule()
        def slack = new SlackModule()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "configDeployment", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        
        println "============================================================"
        println "=                DEPLOY_TO_AWS                             ="
        println "============================================================"
        
        awsDeploy()

        def archiveEnvironmentId = JSON.getValueFromPropertiesFile('archiveEnvironmentId')

        if(archiveEnvironmentId) {
            println "============================================================"
            println "=                REQUEST_ENVIRONMENT_ARCHIVAL              ="
            println "============================================================"

            requestEnvironmentArchival(events, qcpModule, slack)
        }

        println "============================================================"
        println "=                         END                              ="
        println "============================================================"

    } catch (ex) {
        println "Exception occured in configDeployment: $ex"
        throw new Exception("Exception occured while initializing the api pipeline", ex)
    }
}



def validatePipelineTriggerParams() {
    def env = System.getenv()
    if(!env.REPO_URL) {
        println "Repo Url is not come as part of the request."
        throw new Exception("Repo Url is not come as part of the request.");
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
   try {
        def env = System.getenv()
        def qcpModule = new QcpModule()
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
        if (!env.REQUEST_ID ||(serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ) {        
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
    events.sendStartedEvent("VALIDATE_PRE_BUILD_CONF") 
    try {
        checkEnvironmentConfiguration(environmentDeploymentMetadata)
        checkDeploymentConfiguration(environmentDeploymentMetadata)
        events.sendCompletedEvent("VALIDATE_PRE_BUILD_CONF")
    } catch (ex) {
        println "preBuildValidation failed: $ex"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("VALIDATE_PRE_BUILD_CONF", ex.message, "FAILED")
        events.sendFailureEvent("VALIDATE_PRE_BUILD_CONF")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "Prebuild validation failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("preBuildValidation failed: ", ex)    
    }
}

def checkEnvironmentConfiguration(environmentDeploymentMetadata) {
    def qcpModule = new QcpModule()
    try {
        def env = System.getenv()
        def environmentLogicalId
        JSON.setValueToPropertiesFile('repoName', env.REPO_NAME)
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
            environmentDeploymentMetadata.getEnvironmentLogicalId();
            environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
        }

        // create a swagger copy
        sh("cp -r ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swaggerOriginal.json")

        //Checking environment Id is set or not for dev branch
        if (!environmentLogicalId || environmentLogicalId == null || environmentLogicalId == '') {            
            throw new Exception("The environment is not set for the repoBranch: ${env.REPO_BRANCH}, please set them by logging into Jazz UI.")
        }
        //sending qcp notifiaction for start
        qcpModule.sendQCPEvent("Pre", "success")
        environmentDeploymentMetadata.setEnvironmentLogicalId(environmentLogicalId)
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

def loadServiceConfiguration() {
    try {
        def serviceConfigDataLoader = new ServiceConfigurationDataLoader()
        serviceConfigDataLoader.loadServiceConfigurationData()        
    } catch (ex) {
        println "loadServiceConfiguration failed: $ex"
        throw new Exception("loadServiceConfiguration failed:", ex)
    }
}
//Loading user defined configurations form deployment-env.yml file of the user repo
def loadUserDefinedConfigs() {
    def env = System.getenv()
    def resultList = []
    def prop = [:]
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
    def custom_integration_spec = null
    if (userDefinedConfig['require_internal_access'] != null && userDefinedConfig['require_internal_access'] != "") {
        println "user defined require_internal_access is being used."
        internalAccess = userDefinedConfig['require_internal_access'].toString();
    } else if (serviceConfig['require_internal_access'] != null && serviceConfig['require_internal_access'] != "" ){
        println "require_internal_access - from service catalog is being used."
        internalAccess = serviceConfig['require_internal_access'].toString();
    }

    if (userDefinedConfig['custom_integration_spec'] != null && userDefinedConfig['custom_integration_spec'] != "") {
        println "user defined custom_integration_spec is being used."
        custom_integration_spec = userDefinedConfig['custom_integration_spec'].toString().equals("true") ? "true":"false";
    } else if (serviceConfig['custom_integration_spec'] != null && serviceConfig['custom_integration_spec'] != "" ){
        println "custom_integration_spec - from service catalog is being used."
        custom_integration_spec = serviceConfig['custom_integration_spec'].toString().equals("true") ? "true":"false";
    }

    println "custom_integration_spec: $custom_integration_spec"
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

    // validation is already done for stg, so there is no need for prod.
    if (environmentLogicalId !== "prod") {
        validateDeploymentConfigurations(serviceConfig)
        validateSwaggerSpec(custom_integration_spec);
    }
    
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

    //Merging userConfig to serviceConfig
    serviceConfig << userDefinedConfig
    println "serviceConfig: $serviceConfig"

    clearVirtualEnv()
    //cleaning workplace before cloning
    println "cleaning serverlessConfigRepo in working directory"
    sh("rm -rf ${PROPS.SERVERLESS_CONFIG_DIRECTORY}")

    // Cloning serverless config repo
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

    def internalAccess = null
    if (userDefinedConfig['require_internal_access'] != null && userDefinedConfig['require_internal_access'] != "") {
        println "user defined require_internal_access is being used."
        internalAccess = userDefinedConfig['require_internal_access'].toString();
    } else if (serviceConfig['require_internal_access'] != null && serviceConfig['require_internal_access'] != ""){
        println "require_internal_access - from service catalog is being used."
        internalAccess = serviceConfig['require_internal_access'].toString();
    }
    
    removeEventResources(env.REPO_NAME)

    /*
    * Checking if deployment-env.yml file has any values for securityGroupIds & subnetIds
    * If values provided, using that and making internalAccess = true
    */
    if(userDefinedConfig['securityGroupIds'] && userDefinedConfig['subnetIds']){
        println "using subnetIds and securityGroupIds from deployment-env.yml file"
        internalAccess = 'true'
        addSecuritySettings(env, userDefinedConfig['subnetIds'], userDefinedConfig['securityGroupIds'])
        addVpcDetails(env.REPO_NAME)
    } else if ((internalAccess != null && internalAccess.equals('true')) || internalAccess == null) {
        def subnetConfig = ReadSubnetConfiguration()
        println "subnetConfigurations--- $subnetConfig"
        def accounts = subnetConfig['accounts']

        for (account in accounts){
            if (account.id == serviceConfig['account'] && account.region == serviceConfig['region']) {
                addSecuritySettings(env, account['subnets'], account['security_groups'])
                addVpcDetails(env.REPO_NAME)
                break
            }
        }
    }
    println "Completed Vpc settings.."
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
}

def ReadSubnetConfiguration() {
	def data = JSON.readFile("${PROPS.SERVERLESS_CONFIG_DIRECTORY}/aws-subnet-configurations.json")
	println "Reading subnet configuration completed."
	if(data){
		println "Data read from aws-subnet-configurations.json : $data"
        return data
	} else {
		throw new Exception("Missing Subnet Configuration details")
	}
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
        def sonar = new SonarModule()
        def fortifyScan = new FortifyScanModule()
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "codeQualityCheck", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        runtimeValidation(env.REPO_NAME)
        sonar.configureForProject(env.REPO_BRANCH, projectKey)
        sonar.doAnalysis()
        fortifyScan.doScan(projectKey)
        clearVirtualEnv()
        
        events.sendCompletedEvent("CODE_QUALITY_CHECK")
    } catch (ex) {
        println "preBuildValidation failed: $ex"
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
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};npm install --save > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log")
        } else if(serviceConfig['runtime'].indexOf("java") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn package --settings settings_cdp.xml > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log 2>&1")
        } else if(serviceConfig['runtime'].indexOf("python") > -1) {
            // install requirements.txt in library folder, these python modules will be a part of deployment package
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf library")
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};mkdir library")
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};touch library/__init__.py")

            if ((serviceConfig['runtime'] == "python3.6") || (serviceConfig['runtime'] == "python3.8")) {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip3 install -r requirements.txt -t library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log")
            } else {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip install -r requirements.txt -t library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-lambda.log")
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
        println "preBuildValidation failed: $ex"
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
        def env = System.getenv()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "runUnitTestCases", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        println "Run test cases for ${serviceConfig['runtime']}"
        if(serviceConfig['runtime'].indexOf("nodejs") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};npm test > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
        }else if(serviceConfig['runtime'].indexOf("java") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn test --settings settings_cdp.xml > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
        }else if(serviceConfig['runtime'].indexOf("python") > -1) {
            if((serviceConfig['runtime'] == 'python3.6') || (serviceConfig['runtime'] == "python3.8")) {
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
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage run -m pytest --ignore=library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
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
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage run -m pytest --ignore=library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
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
        println "preBuildValidation failed: $ex"
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

def addVpcDetails(repoName) {
    println "addVpndetails to serverless.yml file"
    sh("sed -i -- 's/vpcDisabled/vpc/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")	
}

def addCommonExcludes(repoName) {
    sh("sed -i -e '/exclusionList/r ${PROPS.SERVERLESS_CONFIG_DIRECTORY}/serverless-common-excludes.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
    sh("sed -i -e '/exclusionList/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}

def awsDeploy() {

    def props = JSON.getAllProperties()
    def env = System.getenv()
    def events = new EventsModule();
    def lambdaEvents = new AWSLambdaEventsModule()
    def serviceConfigDataLoader = new ServiceConfigurationDataLoader()
    def apigatewayModule = new AWSApiGatewayModule()
    def utilModule = new UtilityModule()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def environmentLogicalId = props['environmentLogicalId']
    def repoName = env.REPO_NAME
    def serviceConfig = props['serviceConfig']
    def accountDetails = props['accountDetails']
    def configLoaderValue = props['configData']
    def userDefinedConfig = props['userDefinedConfig']
    def credsId = null
    def _event
    def lambdaARN = null
    events.sendStartedEvent("DEPLOY_TO_AWS")
    events.sendStartedEvent('UPDATE_ENVIRONMENT', "awsDeploy started.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_started")) 
    def envMetadata
    def deployApiStageName
    def endpointUrl = null
    def custom_integration_spec = null
    def deploymentAccountCreds = null;

        try {
            credsId = utilModule.generateCreds(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)
            /*
            * Getting temporary credentials for cross account role if not primary account
            */
            deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, env.AWS_DEFAULT_REGION);
            
            envMetadata = getEnvironmentInfo(environmentDeploymentMetadata, environmentLogicalId, props, deploymentAccountCreds, accountDetails)					

            def apiId = envMetadata["AWS_API_ID"]
            def apiName = envMetadata["AWS_API_NAME"]	
            
            // If enable_api_security is true and authorizer_arn is provided then check whether provided authorizer exist, if not create one
            def enableApiSecurityFlag = userDefinedConfig['enable_api_security']
            if (enableApiSecurityFlag != null){
                enableApiSecurityFlag = enableApiSecurityFlag.toString();
            }
            if(userDefinedConfig['authorizer_arn'] && userDefinedConfig['authorizer_arn'] != null && enableApiSecurityFlag && enableApiSecurityFlag != null && enableApiSecurityFlag.equals("true")){
                def checkAuthARN = userDefinedConfig['authorizer_arn']
                createAuthorizerInAPIGateway(checkAuthARN, apiId, deploymentAccountCreds, serviceConfig, accountDetails)
                // Update Authorizer Id in metadata (if gets created now)
                getADAuthorizerID(environmentLogicalId, apiId, deploymentAccountCreds, apigatewayModule, checkAuthARN)
                envMetadata["AD_AUTHORIZER"] = JSON.getValueFromPropertiesFile('apiId:AD_AUTHORIZER')
            }
            _event = 'GET_SERVICE_CODE'
            events.sendStartedEvent( _event, 'checkout service config file')
            sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json")
            sh("cp ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swaggerOriginal.json ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json")
            loadServiceConfiguration()
            events.sendCompletedEvent(_event, 'successfully clone service config file')

            _event = 'GET_DEPLOYMENT_CONF'
            events.sendStartedEvent( _event, 'loading deployment config file')
            
            loadServerlessConfig()
                
            events.sendCompletedEvent(_event, 'successfully loaded deployment config file')
            
            // Generate serverless yml file with domain added in function name
            println "Generate deployment env with domain"
            _event = 'UPDATE_DEPLOYMENT_CONF'
            events.sendStartedEvent( _event, 'updating deployment config file')
            generateServerlessYml(props)
            
            if (environmentLogicalId == "prod"){
                updateServerlessBucketInfo("PROD", props)
                deployApiStageName = environmentLogicalId
                
            } else if (environmentLogicalId == "stg"){
                updateServerlessBucketInfo("STG", props)
                deployApiStageName = environmentLogicalId
            } else {
                // For backward compatibility. Reuse the dev s3 bucket for deployment.
                updateServerlessBucketInfo("DEV", props)
                deployApiStageName = "dev"
            }			
            
            println "setting lambda execution role as ${props['deploymentRole']}";
            setLambdaExecutionRole(props);
            events.sendCompletedEvent(_event, 'successfully updated deployment config file')

            addStackTags(props)
            addCommonExcludes(props['repoName'])
            def deployOutput = serverlessDeploy(environmentLogicalId, props['repoName'], deploymentAccountCreds);

            if(deployOutput != 'success'){
                handleDeploymentErrors(deployOutput, props['repoName'], environmentLogicalId, deploymentAccountCreds)						
            }
            def environmentName = getEnvironmentTag(environmentLogicalId)
            // attach tags to log group
            attachTagstoLogGroup(serviceConfig, environmentLogicalId, deploymentAccountCreds, environmentName, props['commitSha'], props['deploymentRegion'])
           
            def stackName =  "${serviceConfig['service']}--${serviceConfig['domain']}-${environmentLogicalId}" //service--domain-env 
            lambdaARN = getLambdaARN(stackName, deploymentAccountCreds, serviceConfig);
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", lambdaARN.arn, "lambda", serviceConfig['created_by']));

            logGroupARN = getLogARN(lambdaARN.accountId, lambdaARN.region, lambdaARN.functionName);
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", logGroupARN, "log_group", serviceConfig['created_by']));

            events.sendStartedEvent( 'UPDATE_LOGGING_CONF', 'updating logging config')
            createSubscriptionFilters(deploymentAccountCreds,  props);
            events.sendCompletedEvent('UPDATE_LOGGING_CONF', 'successfully updated logging config')

            // Generate swagger file based on environment
            def swaggerExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
            println "Swagger Exists: " + swaggerExists.toString()
            if (swaggerExists) {
                println "Generating the swagger file for each environment"
                addOperationId("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json", repoName)				

                def swaggerDocUrl = "https://${configLoaderValue.JAZZ.API_DOC.DNS_HOST_NAME}/${repoName}/${environmentLogicalId}/swagger.json"
                
                def apiDeploymentTarget;
                if (serviceConfig.deployment_targets){
                    // if service metadata is retrieved using service metadata loader
                    apiDeploymentTarget = serviceConfig.deployment_targets.api;    
                }else{
                    // Supporting older services with no service_id in their deployment-env.yml. Service metadata loader is not used in this case.
                    apiDeploymentTarget = 'aws_apigateway';    
                }

                if (userDefinedConfig['custom_integration_spec'] != null && userDefinedConfig['custom_integration_spec'] != "") {
                    println "user defined custom_integration_spec is being used."
                    custom_integration_spec = userDefinedConfig['custom_integration_spec'].toString().equals("true") ? "true":"false";
                } else if (serviceConfig['custom_integration_spec'] != null && serviceConfig['custom_integration_spec'] != "" ){
                    println "custom_integration_spec - from service catalog is being used."
                    custom_integration_spec = serviceConfig['custom_integration_spec'].toString().equals("true") ? "true":"false";
                }
                
                println "custom_integration_spec: $custom_integration_spec"
                switch (apiDeploymentTarget) {
                    case 'aws_apigateway':

                        _event = 'UPDATE_SWAGGER'
                        events.sendStartedEvent( _event, 'updating swagger file')

                        if(custom_integration_spec == null || custom_integration_spec.equals("false")) {
                            sanitizeSwaggerSpec("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
                            addAmazonIntegrationSpec(repoName, env.APIGATEWAY_LAMBDA_INTEGRATION_REPO)
                        }

                        def apiHostName = envMetadata['DNS_HOSTNAME']

                        updateSwaggerWithAccountSpecificDetails(serviceConfig, accountDetails, repoName)
                        updateSwaggerWithServiceDetails(serviceConfig['service'], environmentLogicalId, serviceConfig['service'], apiHostName, serviceConfig['domain'], repoName)
                        validateSwaggerFile(serviceConfig['service'], serviceConfig['domain'], apiHostName, apiDeploymentTarget, repoName)
                        
                        events.sendCompletedEvent(_event, 'swagger file update completed.')
                        
                        // import swagger api spec
                        println "Deploying API gateway Endpoints"
                        _event = 'DEPLOY_SWAGGER_TO_APIGATEWAY'
                        events.sendStartedEvent( _event, 'starts deploying swagger file to api gateway.')
                        
                        if(environmentLogicalId != 'stg' && environmentLogicalId != 'prod') {
                            updateStageForDev(environmentLogicalId, repoName)
                        }
                        
                        deploySwagger(deployApiStageName ,apiId, apiName, deploymentAccountCreds, serviceConfig, repoName, events, environmentDeploymentMetadata)
                        events.sendCompletedEvent(_event, 'successfully deployed swagger file to api gateway')
                        
                        //enabling AD auth for service
                        println "enableApiSecurityFlag: $enableApiSecurityFlag"
                        enabledAPIAuth(events, serviceConfig, environmentLogicalId, enableApiSecurityFlag, apiId, envMetadata["AD_AUTHORIZER"], deploymentAccountCreds, deployApiStageName, repoName)
                        def apiArns = getSwaggerResourceARNs(serviceConfig['region'], apiId, environmentLogicalId, "aws", serviceConfig, repoName)
                        if(apiArns) {
                            for (arn in apiArns) {
                                events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", arn, "apigateway", serviceConfig['created_by']));
                            }
                        }

                        // create documentation
                        updateApiDocUrl(deployApiStageName, configLoaderValue, serviceConfig, repoName, envMetadata)
                        basePath = getBasePath(repoName)
                        
                        _event = 'GENERATE_API_DOC'
                        events.sendStartedEvent(_event, 'generate API docs')
                        // We are not generating api documentation anymore!
                        events.sendCompletedEvent(_event, 'successfully generated API docs')

                        events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", swaggerDocUrl, "swagger_url", serviceConfig['created_by']));

                        endpointUrl = getEndpoint(configLoaderValue, serviceConfig, deployApiStageName, serviceConfig['service'], envMetadata, repoName);

                        events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", endpointUrl, "endpoint_url", serviceConfig['created_by']));
                        break;
                    case 'gcp_apigee':
                        endpointUrl = gcpDeploy(events, repoName, lambdaARN, environmentLogicalId, serviceConfig, swaggerDocUrl, configLoaderValue, accountDetails, envMetadata)
                        break;
                    default:
                        throw new Exception("Deployment targets are not defined")
                }

                _event = 'UPLOAD_API_SPEC'
                events.sendStartedEvent( _event, 'uploading API specification')
                def clearwaterModule = new ClearwaterModule()
                clearwaterModule.generatePNGFromPUML(repoName)
                sanitizeSwaggerSpec("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
                uploadToS3(repoName, environmentLogicalId, serviceConfig, events, utilModule, configLoaderValue, environmentDeploymentMetadata, qcpModule, slack)
                println "---------------------- API Info -----------------------------"
                println "API is now available at $endpointUrl"
                println "-------------------------------------------------------------"
                println "API Spec is now available at http://${configLoaderValue.JAZZ.SWAGGER_HOSTNAME}/?url=${swaggerDocUrl}"
                echoServiceInfo(lambdaARN.arn)
                qcpModule.sendQCPEvent("Post", "success")
                events.sendCompletedEvent(_event, 'successfully uploaded API specification')
                events.sendCompletedEvent('UPDATE_ENVIRONMENT', 'Environment Update event for deployment completion', environmentDeploymentMetadata.generateEnvironmentMap("deployment_completed", envMetadata, null, endpointUrl))
                events.sendCompletedEvent('UPDATE_DEPLOYMENT',  "Deployment completion Event for ${environmentLogicalId} deployment", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.successful))
                events.sendCompletedEvent('DEPLOY_TO_AWS', 'successfully deployed services to AWS')
            }

        }catch(ex) {
            if (_event){
                println "_event: $_event"
                events.sendFailureEvent(_event, ex.message)
            }
            events.sendFailureEvent('UPDATE_ENVIRONMENT', "awsDeploy failed.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_failed"))
            events.sendFailureEvent('UPDATE_DEPLOYMENT', "awsDeploy failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
            qcpModule.sendQCPEvent("Post", "failure")
            slack.sendSlackNotification("DEPLOY_TO_AWS", ex.message, "FAILED")
            throw new Exception("Error in UPDATE_ENVIRONMENT", ex)
        } finally {
            // reset Credentials
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


def setLambdaExecutionRole(props) {
    def userDefinedConfig = props['userDefinedConfig']
    def serviceConfig = props['serviceConfig']
    def userRole = userDefinedConfig['iamRoleARN']
    def env = System.getenv()
    sh("sed -i -- 's|DEFAULT_LAMBDA_EXE_ROLE|${props['deploymentRole']}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/serverless.yml")
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


def serverlessDeploy(env, serviceName, credsId){
    def envs = System.getenv()
    def ip = sh("curl ifconfig.co");
    println "Build agent IP: ${ip}";
    try{
        println sh("ls -al ${PROPS.WORKING_DIRECTORY}/${serviceName}")
        println "----------------------serverless.yml-----------------------------"
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${serviceName}/serverless.yml") 
        println "----------------------deployment-env.yml----------------------"
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${serviceName}/deployment-env.yml") 
        println "-----------------------------------------------------"
        println "deploying $serviceName to $env"
        sh("cd ${PROPS.WORKING_DIRECTORY}/${serviceName};serverless deploy --force --stage $env --aws-profile ${credsId} -v  > ../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log" )
        println "==================================================="
        println sh("cat ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log")
        println "==================================================="
        println "-----------------DEPLOYMENT DONE-------------------"
        println "==================================================="
        return "success"
    } catch (ex) {
        println "Serverless deployment failed due to $ex"
    }
    sh("cat  ${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log")
    def outputLog = new java.io.File("${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-deploy.log").text
    println "serverless deployment log $outputLog"
    return outputLog
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
        def owner = serviceConfig['created_by']
        def application = serviceConfig['appTag']
        def platform = 'jazz'
        def tagResult = sh("aws logs tag-log-group --log-group-name ${logGroupName} --tags '{\"Service\":\"${serviceName}\", \"Domain\":\"${domain}\", \"EnvironmentId\":\"${environmentLogicalId}\", \"FunctionName\":\"${functionName}\", \"ApplicationId\":\"${applicationId}\", \"Owner\":\"${owner}\", \"Application\":\"${application}\", \"GitCommitHash\":\"${gitCommitHash}\", \"Environment\":\"${environmentName}\", \"Platform\":\"${platform}\"}' --profile ${credsId} --region ${deploymentRegion}", true)
        println "Set tags result: $tagResult"

    } catch (Exception ex) {
        println "Error occured while attaching tags to logGroup ${ex}"
    } 
}

def echoServiceInfo(arn) {
    println "======================================================================="
    println "API deployed: $arn"
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
            filter_json = sh("aws logs describe-subscription-filters  --log-group-name \"${lambda}\" --region ${props['deploymentRegion']} --profile ${credsId}", true)
            // println "${filter_json}"
            def resultJson = JSON.parseJson(filter_json)
            filtername = resultJson.subscriptionFilters[0].filterName
            println "removing existing filter... $filtername"
            if(filtername != "" && !filtername.equals(lambda)) {
                sh("aws logs delete-subscription-filter  --log-group-name \"${lambda}\" --filter-name \"${filtername}\" --region ${props['deploymentRegion']} --profile ${credsId}" )
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
            println "error occured in createSubscriptionFilters: $ex" 
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

// TODO use from UtilityModule
def awsSetProfile (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_DEFAULT_REGION,  credsId) {
    sh("aws configure set profile.${credsId}.region ${AWS_DEFAULT_REGION}",false,false)
    sh("aws configure set profile.${credsId}.aws_access_key_id ${AWS_ACCESS_KEY_ID}",false,false)
    sh("aws configure set profile.${credsId}.aws_secret_access_key ${AWS_SECRET_ACCESS_KEY}",false,false)
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

        resetEnvironmentSpecificConfigurations()
        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
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

def productionDeploy() {
    def events = new EventsModule()
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def utilModule = new UtilityModule()
    def qcpModule = new QcpModule()
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


def requestEnvironmentArchival(events, qcpModule, slack) {
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


/*
    create new/different authorizer in API Gateway if not available in authorizer list
*/
def createAuthorizerInAPIGateway(functionArn, apiId, credsId, serviceConfig, accountDetails){
    //check whether authorizer exist
    def authIdList = getAuthorizerId(functionArn, apiId, credsId)
    def authName = functionArn.tokenize(':').last()

    // create authorizer if not exist
    try{
        if(authIdList == null ){
            def createNewAuth = sh(
                "aws apigateway create-authorizer --rest-api-id ${apiId} --name "+authName+" --type REQUEST --identity-source 'method.request.header.Authorization' --authorizer-uri arn:aws:apigateway:${serviceConfig.region}:lambda:path/2015-03-31/functions/"+functionArn+"/invocations --authorizer-credentials "+ accountDetails.IAM.PLATFORMSERVICES_ROLEID +" --profile ${credsId} --output json",
                true
            )
            println "createNewAuth: $createNewAuth"
        } else {
            println "$authName already exist"
        }
    }catch(ex){
        println "createAuthorizerInAPIGateway Error: $ex"
        throw new Exception("createAuthorizerInAPIGateway Error", ex)
    }
}

def getEnvironmentInfo (environmentDeploymentMetadata, stage, props, credsId, accountDetails) {
    environmentDeploymentMetadata.getEnvironmentInfo()
    def env_info = JSON.getValueFromPropertiesFile('environmentInfo')
    def envMetadata = [:]					
    if (env_info && env_info["metadata"]) {
        envMetadata = env_info["metadata"]
    }
    def apigatewayModule = new AWSApiGatewayModule()
    if (!envMetadata["AWS_API_ID"]  || !envMetadata["AD_AUTHORIZER"]) {
        getApiId(stage, apigatewayModule)
        getApiName(stage, apigatewayModule)
        getApiHostName(stage, apigatewayModule)
        
        def apiId = JSON.getValueFromPropertiesFile('apiId:ID')
        println "apiId: $apiId"
        def userDefinedConfig = props['userDefinedConfig'];
        getADAuthorizerID(stage, apiId, credsId, apigatewayModule, userDefinedConfig['authorizer_arn'])
        envMetadata["AWS_API_ID"] = apiId
        envMetadata["AWS_API_NAME"] = JSON.getValueFromPropertiesFile('apiId:NAME')
        envMetadata["DNS_HOSTNAME"] = JSON.getValueFromPropertiesFile('apiId:DNS_HOSTNAME')
        envMetadata["AD_AUTHORIZER"] = JSON.getValueFromPropertiesFile('apiId:AD_AUTHORIZER')
    }  
    println "envMetadata : $envMetadata"
    return envMetadata
}


/**
 * Get the API Id of the gateway specific to an environment. The values will be pulled ENV vars set
 * @param  stage the environment
 * @return  api Id
 */
def getApiId(stage, apigatewayModule) {
    return apigatewayModule.getApiGatewayInfo(stage, 'ID')
}

def getApiHostName(stage, apigatewayModule) {
    return apigatewayModule.getApiGatewayInfo(stage, "DNS_HOSTNAME")
}

def getApiName(stage, apigatewayModule) {
    return apigatewayModule.getApiGatewayInfo(stage, "NAME")
}

def getADAuthorizerID(stage, apiId, credsId, apigatewayModule, getAuthARN){
    println "getAuthARN: $getAuthARN"
    if(getAuthARN && getAuthARN != null) {
        def authid = getAuthorizerId(getAuthARN, apiId, credsId)
        if (authid) {
            JSON.setValueToPropertiesFile('apiId:AD_AUTHORIZER', authid)
        } else {
            apigatewayModule.getApiGatewayInfo(stage, "AD_AUTHORIZER")
        }
    } else {
        apigatewayModule.getApiGatewayInfo(stage, "AD_AUTHORIZER")	
    }
}

def getAuthorizerId(function_arn, apiId, credsid){

    //get authorizers list
    def authList = sh("aws apigateway get-authorizers --rest-api-id ${apiId} --profile ${credsid} --output json")
    println "authList: $authList"
    def authobjparsed = JSON.parseJson(authList)
    def items = authobjparsed["items"]
    def reqId = null

    //search for the user provided arn
    if (function_arn != null){
        for(authObj in items){
            String currentarn = authObj["authorizerUri"];
            if(currentarn.indexOf(function_arn) > -1)
            {
                println "arn matched $currentarn"
                reqId = authObj.id
                break;
            }
        }
    }
    return reqId
}

/* method to read the file and add operation id */
def addOperationId(filePath, repoName) {
    try {
          // readFile intermittently fails, added these to see if the file exists at this point or not.
        sh("ls -lrt")
        sh("ls -lrt ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/")
          def content = JSON.readFile(filePath)
        if (content){
            def swaggerJson = updateOperationId(content)
            JSON.writeFile(filePath, swaggerJson)
        } else {
          println "Failed to read swagger file while updating operationId, cannot proceed.." + ex
          throw new Exception( "Failed to read swagger file while updating operationId", ex)
        }
    } catch(ex) {
        println "Error adding operation id " + ex.message
        throw new Exception("Error adding operation id", ex)
    }
}

/* method to add operation id */
def updateOperationId(inputJSON) {
    println "Parsing the swagger file"
    def httpVerbs = ['get', 'post', 'delete', 'put', 'connect', 'head', 'options', 'patch', 'trace'];
    def pathKeys = inputJSON.paths.keySet()
    for (key in pathKeys) {
        def pathMethods = inputJSON.paths[key].keySet()
        for (method in pathMethods) {
            def path = inputJSON.paths[key]
            def foundMethod = httpVerbs.find { element -> element == method }
            if (foundMethod != null) {
                println "Adding operation id for method $foundMethod"
                if (path[foundMethod].operationId) {
                    println "operation id found for method $foundMethod"
                    if (path[foundMethod].operationId == "{operationId}") {
                        path[foundMethod].operationId = key.substring(1).replaceAll("[/{}]", "_") + "_" + foundMethod
                    }
                } else {
                    println "operation id not found for method $foundMethod"
                    path[foundMethod] << [operationId: key.substring(1).replaceAll("[/{}]", "_") + "_" + foundMethod]
                }
            }
        }
    }

    // println "swaggerJson: $inputJSON"
    return inputJSON
}

def sanitizeSwaggerSpec(filepath) {
    if (JSON.isFileExists(filepath)) {
        try {
            println "sanitizeSwaggerSpec: $filepath"
            def swaggerStr = JSON.readFile(filepath)	
            if(swaggerStr) {
                def modifiedContent = RemoveUserDefinedIntegrationSpec(swaggerStr)
                JSON.writeFile(filepath, modifiedContent)
                println "------------------ Swagger File ------------------"
                println sh( "cat $filepath")
                println "=================================================="
            }
        } catch (ex) {
            throw new Exception( "Error occured in sanitizeSwaggerSpec. ", ex)
        }
    } else {
        throw new Exception( "Swagger file not available in the path")
    }
}

def RemoveUserDefinedIntegrationSpec(parsedJson){
    println "Remove User defined  integration spec..."
    try {
        def keys  = parsedJson.keySet() as String[]; 
        def keys_of_paths = parsedJson.paths.keySet() ; 

        for (key_of_a_path in keys_of_paths) {
            def methods_of_each_path = parsedJson.paths[key_of_a_path].keySet() 
            for (method in methods_of_each_path) {
                def temp = parsedJson.paths[key_of_a_path]
                def ret = temp[method].remove("x-amazon-apigateway-integration")
            }
        }

    return parsedJson

    } catch (ex) {
        println " RemoveUserDefinedIntegrationSpec :::: Error occurred"  +  ex.message
        throw new Exception(" RemoveUserDefinedIntegrationSpec :::: Error occurred", ex)
    }
}

/**
* Function to load amazon gateway integration spec, and merge it into swagger.json
*/
def addAmazonIntegrationSpec(repoName, integrationRepo){
    println "Injecting Amazon apigateway lambda integration spec..."
    if( JSON.isFileExists("${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}") ) {
        println "aws-apigateway-lambda-integration-spec folder found"
        sh( "rm -rf ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}")
    }
    sh("git clone -b master ${integrationRepo} ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}")
    println sh("ls -al ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}")
    
    
    println "amazon apigateway lambda integration spec "

    sh(  "sed -i '/\"options\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-options.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh(  "sed -i '/\"get\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-generic.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh(  "sed -i '/\"post\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-generic.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh(  "sed -i '/\"delete\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-generic.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh(  "sed -i '/\"connect\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-generic.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh(  "sed -i '/\"put\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-generic.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh(  "sed -i '/\"head\":.*{/ r ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}/amazon-swagger-spec-generic.txt' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")

    sh( "rm -rf ${PROPS.AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY}")
}

def updateSwaggerWithAccountSpecificDetails(serviceConfig, accountDetails, repoName){
    def roleId = JSON.getValueFromPropertiesFile('roleId')
    def roleValue = accountDetails.IAM.PLATFORMSERVICES_ROLEID;	
    println "Updating swagger with deployment information specific to the environment.."
    sh( "sed -i -- 's/{conf-region}/${serviceConfig.region}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh( "sed -i -- 's/{conf-accId}/${roleId}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    sh( "sed -i -- 's|{conf-role}|${roleValue}|g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
}

/**
    Generate the swagger file specific to each environment
*/
def updateSwaggerWithServiceDetails(service, environmentLogicalId, deploymentNode, apiHostName, domain, repoName) {
    def env = System.getenv()
    sh( "sed -i -- 's/{lambda_function_name}/" + domain + "_" + service  + "_" + environmentLogicalId + "/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json")
    sh( "sed -i -- 's/{api_deployment_node_title}/" + deploymentNode + "/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json") // {api_deployment_node_title}
    sh( "sed -i -- 's/{service_name}/" + service + "/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json") // {service_name}
    sh( "sed -i -- 's/{api_host_name}/" + apiHostName + "/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json") // {api_host_name}
    sh( "sed -i -- 's/{domain}/" + domain + "/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json") // {domain}
}

def validateSwaggerFile( service, domain, apiHostName, deployment_target, repoName){
    println "================================ validating swagger before deployement ================================"
    if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")) {
        def swaggerFile = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
        println "after reading swagger file....."
        def swaggerHost = swaggerFile.host
        def swaggerService = swaggerFile.paths.keySet() as List
        def swaggerDomain = swaggerFile.basePath

        /* validate domain name*/
        def firstToken = swaggerDomain
        println "firstToken = " + firstToken
        assert swaggerDomain.trim().size()!=0
        assert swaggerDomain.startsWith("/")
        assert !swaggerDomain.trim().substring(1).startsWith("/")
        assert swaggerDomain.trim().startsWith("/"+domain)
        firstToken = swaggerDomain.trim().substring(1)
        if(firstToken.indexOf("/")>0)
            firstToken = firstToken.substring(0, firstToken.indexOf("/"))
        if(domain.trim().size()!=0)
            assert firstToken.equals(domain)
        
        if(deployment_target == "aws_apigateway") {
            def swaggerStr = JSON.objectToJsonString(swaggerFile);
            assert swaggerStr.indexOf("x-amazon-apigateway-integration") > -1
        }
    }
    sh( "sed -i -- 's/host\": \"[^\"]*\"/host\": \"" + apiHostName +   "\"/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    println "============================= validated swagger before deployement =================================="
}

/**
    Update context stage to use the dev properties file.
*/
def updateStageForDev(environment, repoName) {
    sh( "sed -i -- 's#basePath\": \"/#basePath\": \"/$environment/#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
}

def deploySwagger(stage, api_id, apiName, credsId, config, repoName, events, environmentDeploymentMetadata){
    try{
        println "deploying swagger to $stage"
        def filepath = "${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json"
        println "------------------ swaggerFile before deploy ------------------"
        println sh( "cat $filepath")
        println "=================================================="
        sh( "aws apigateway put-rest-api --rest-api-id ${api_id} --mode merge --parameters basepath=prepend --body 'file://${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json'"+ " --profile ${credsId} --region ${config['region']}")
        sh( "aws apigateway update-rest-api --rest-api-id ${api_id} --patch-operations op=replace,path=/name,value=${apiName} --output json --profile ${credsId} --region ${config['region']}")
        sh( "aws apigateway create-deployment --rest-api-id ${api_id} --stage-name ${stage} --profile ${credsId} --description 'Deployment request for ${config['domain']}_${config['service']}' --region ${config['region']}")
    } catch (ex) {
        println "deploySwagger error: " + ex.message
        events.sendFailureEvent('DEPLOY_SWAGGER_TO_APIGATEWAY', ex.message)
        events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message, environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("SWAGGER API IMPORT FAILED! This error can sometimes be caused by invalid swagger file", ex)
    }
}

/** 
 * Apply AD Authorization if security is enabled.
 * 
 */
 def enabledAPIAuth(events, service_config, environmentLogicalId, enable_api_security_flag, api_id, authId, credsId, deployApiStageName, repoName) {
    events.sendStartedEvent( 'ENABLE_AD_AUTHORIZATION', 'enable AD authorization')
    try {
        if (enable_api_security_flag && enable_api_security_flag.equals("true")){
            def resource_path = ""
            def isDevEnv = false
            if ((environmentLogicalId != "prod") && (environmentLogicalId != "stg")) {
                isDevEnv = true
            }
            basePath = getBasePath(repoName)
            formatBase = basePath.split("/")
            versionTxt = "v1"
            if(isDevEnv) {
                if(formatBase.contains(versionTxt)) {
                    resource_path = "/${environmentLogicalId}/${service_config['domain']}/${versionTxt}/${service_config['service']}"
                } else {
                   resource_path = "/${environmentLogicalId}/${service_config['domain']}/${service_config['service']}" 
                }
            } else {
                if(formatBase.contains(versionTxt)) {
                    resource_path = "/${service_config['domain']}/${versionTxt}/${service_config['service']}"
                } else {
                   resource_path = "/${service_config['domain']}/${service_config['service']}" 
                }
            }

            println "resource_path: $resource_path"
            def resourcePathList = GetSecurityUpdates(api_id, resource_path, credsId)
            println "resourcePathList: $resourcePathList"
            def isChanged = false
            for(updates in resourcePathList){
                if (updates.size() == 2){
                    def resource_id = updates[0]["resource_id"].toString()
                    def methods = updates[1]["methods"]
                    for (method in methods){
                        isChanged = true
                       sh("aws apigateway update-method  --rest-api-id ${api_id} --resource-id ${resource_id} --http-method ${method} --patch-operations '[{\"op\":\"replace\",\"path\":\"/authorizationType\",\"value\":\"CUSTOM\"},{\"op\":\"replace\",\"path\":\"/authorizerId\",\"value\":\"${authId}\"}]' --output json --profile ${credsId} --region ${service_config['region']}")
                    }
               }
            }
            if(isChanged) {
                sh("aws apigateway create-deployment  --rest-api-id ${api_id} --stage-name ${deployApiStageName} --output json --profile ${credsId} --region ${service_config['region']}")
            }

        }
    } catch (ex) {
            events.sendFailureEvent('ENABLE_AD_AUTHORIZATION', ex.message)
            events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message)
            // qcpModule.sendQCPEvent("Post", "failure", environmentLogicalId)
            throw new Exception("Enabling AD Authorization failed. ", ex)
    }
    events.sendCompletedEvent('ENABLE_AD_AUTHORIZATION', 'successfully enabled AD authorization')
 }

 /**
 * Get API Resource Arns
 *
 */
def getSwaggerResourceARNs(region, apiId, deployStg, deploymentTarget, serviceConfig, repoName){
    def basePath = getBasePath(repoName)
    basePath = basePath.replaceAll("^api/", "/");
    if(!basePath.startsWith("/")) {
        basePath = "/"+basePath
    }
    def resourceArns = []
    def resourcesMap = createPathMethodList(repoName) //Returns a list maps of path and methods
    
    if(resourcesMap) {
        for (_map in resourcesMap){
            for (path in _map) {
                def arn = createSwaggerResourceArn(region, apiId, deployStg, path.value, path.key, basePath, deploymentTarget, serviceConfig)
                resourceArns.add(arn)
            }
        }
    }
    return resourceArns;
}


/**
 * create apigateway arns pattern
 */
def createSwaggerResourceArn(region, apiId, deployStg, method, path, basePath, deploymentTarget, config) {
    def apiResourceArn = ""
    def _region = config.region
    def _account = config.account
    def _apiId = apiId
    def _stgName = deployStg
    def _method = method
    def _path = path
    def _basePath = basePath
    
    if(deploymentTarget == 'gcp') {
        apiResourceArn = _method+_basePath+_path
        return apiResourceArn
    }
    
    apiResourceArn = "arn:aws:execute-api:"+_region+":"+_account+":"+_apiId+"/"+_stgName+"/"+_method+_basePath+_path
    return apiResourceArn;
}

//Creates a list consisting of all path-method combination from the swagger which is serializable.
def createPathMethodList(repoName) {
    def lazyMap = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    def jsonMap = [:]
    jsonMap.putAll(lazyMap.paths)
    def composedList = []

    for (path in jsonMap){
        def pathKey = path.key
        def pathVal = [:]
        pathVal.putAll(path.value)

        //loop through all methods in path
        for (method in pathVal){
            def methodVal = method.key.toUpperCase()
            def pathMap = [:]
            pathMap[pathKey] = methodVal
            composedList.add(pathMap)
        }
    }
    return composedList;
}

/**
    Read basepath from swagger
*/
def getBasePath(repoName) {
    def propValue = "/"
    if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")) {
        def swaggerFile = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
        propValue = swaggerFile.basePath
    }
    def propVal = propValue.replace( /{domain}/, '' ).trim()
    if (propVal.startsWith('/')) {
        propVal = propVal.substring(1)
    }
    return propVal
}

/**
    Update the Api doc host url to include 'api'
**/
def updateApiDocUrl(env, configLoaderValue, config, repoName, envMetadata) {
    def hostnameValue
    if(env != 'default'){
        for(item in configLoaderValue.AWS.ACCOUNTS){
            if(item.ACCOUNTID == config.account){
                hostnameValue = envMetadata["DNS_HOSTNAME"]
                println "hostnameValue: $hostnameValue"
                if(hostnameValue.contains("execute-api")){
                    sh( "sed -i -- 's#basePath\": \"/#basePath\": \"/${env}/#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
                    sh( "sed -i -- 's#basePath\": \"/${env}/\"#basePath\": \"/${env}\"#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
                } else {
                    sh( "sed -i -- 's#basePath\": \"/#basePath\": \"/api/#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
                    sh( "sed -i -- 's#basePath\": \"/api/\"#basePath\": \"/api\"#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
                }
            }
        }
    } else {
        sh( "sed -i -- 's#basePath\": \"/#basePath\": \"/api/#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
        sh( "sed -i -- 's#basePath\": \"/api/\"#basePath\": \"/api\"#g' ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
    }
}

def getEndpoint(configLoaderValue, config, env, service, envMetadata, repoName){
    def basePathValue = getBasePath(repoName);
    for(item in configLoaderValue.AWS.ACCOUNTS){
        if(item.ACCOUNTID == config.account){
            if(!item.PRIMARY){
                basePathValue = basePathValue.replaceAll("^api/", "${env}/");
                return "https://${envMetadata['DNS_HOSTNAME']}/" + basePathValue + "/"+ service
            } else {
                return "https://${envMetadata['DNS_HOSTNAME']}/" + basePathValue + "/"+ service
            }
        }
    }
}

def gcpDeploy(events, repoName, lambdaARN, environmentLogicalId, config, swaggerDocUrl, configLoaderValue, accountDetails, envMetadata) {
    events.sendStartedEvent('DEPLOY_TO_GCP_APIGEE', 'Deployment started for APIGEE')
    def apigeeModule = new ApigeeModule()
    def envUpper = (environmentLogicalId == 'prod' || environmentLogicalId == 'stg') ? environmentLogicalId.toUpperCase() : 'DEV'
    def apiHostName ="${configLoaderValue.APIGEE.API_ENDPOINTS[envUpper].SERVICE_HOSTNAME}"
    updateSwaggerWithServiceDetails(config['service'], environmentLogicalId, config['service'], apiHostName, config['domain'], repoName)
    validateSwaggerFile(config['service'], config['domain'], apiHostName, "gcp_apigee", repoName)
    if(environmentLogicalId != 'stg' && environmentLogicalId != 'prod') {
        updateStageForDev(environmentLogicalId, repoName)
    }
    updateApiDocUrl('default', configLoaderValue, config, repoName, envMetadata)
    def endpointUrl = apigeeModule.deploy("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json", lambdaARN, envUpper, environmentLogicalId, "api")
    println "API Proxy created at: ${endpointUrl}"
    events.sendCompletedEvent('DEPLOY_TO_GCP_APIGEE', 'Deployment complete for APIGEE')
    //adding svc, domain and environment as the provider id since that is used as apiproxy(resource)
    def apiResources = getSwaggerResourceARNs(config['region'], "${config['domain']}-${config['service']}-${environmentLogicalId}", environmentLogicalId, "gcp", config, repoName)
    if(apiResources) {
        for (path in apiResources) {
            println "API Path $path"
            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("gcp", "${config['domain']}-${config['service']}/${environmentLogicalId}/${path}", "apigee_proxy", config['created_by']));
        }
    }
    events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("gcp", swaggerDocUrl, "swagger_url", config['created_by']));
    events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("gcp", endpointUrl, "endpoint_url", config['created_by']));
    return endpointUrl
}


def uploadToS3(repoName, environmentLogicalId, serviceConfig, events, utilModule, configLoaderValue, environmentDeploymentMetadata, qcpModule, slack){
    def credsId = UUID.randomUUID().toString();
    try {
        def env = System.getenv()
        def awsAccessKey = env['AWS_302890901340_ACCESS_KEY']
        def awsSecretKey = env['AWS_302890901340_SECRET_KEY']
        def awsDefaultRegion = env['AWS_DEFAULT_REGION']
        awsSetProfile(awsAccessKey, awsSecretKey, awsDefaultRegion,  credsId)
        println "aws s3 cp ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/ s3://${configLoaderValue.AWS.S3.API_DOC}/${repoName}/${environmentLogicalId} --profile ${credsId} --region ${serviceConfig.region} --recursive"
        sh( "aws s3 cp ${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/ s3://${configLoaderValue.AWS.S3.API_DOC}/${repoName}/${environmentLogicalId} --profile ${credsId} --region ${serviceConfig.region} --recursive ")
        if(JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/flows")) {
            println sh("ls -al ${PROPS.WORKING_DIRECTORY}/${repoName}/flows")
            // check under flows folder for {repoName}.png
            println "${PROPS.WORKING_DIRECTORY}/${repoName}/flows/${repoName}.png"
            if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/flows/${repoName}.png")) {
                sh( "aws s3 cp ${repoName}.png s3://${configLoaderValue.AWS.S3.API_DOC}/${repoName}/${environmentLogicalId}/flow.png --profile ${credsId} --region ${serviceConfig.region}")
            
                def sequence_diagram_url = "https://${configLoaderValue.AWS.S3.API_DOC}/${repoName}/${environmentLogicalId}/flow.png"
                events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", sequence_diagram_url, "sequence_diagram_url", serviceConfig['created_by']), environmentLogicalId);
            }
        }

    } catch(ex){
        println "Upload to S3 failed " + ex
        events.sendFailureEvent('UPDATE_ENVIRONMENT', ex.message, environmentDeploymentMetadata.generateEnvironmentMap("deployment_failed"))
        events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message, environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("DEPLOY_TO_AWS", ex.message, "FAILED")
        throw new Exception("Upload to S3 failed", ex)
    } finally {
        // reset Credentials
        resetCredentials(credsId)
    }
     
}

/*
 * Prepare swagger for Clearwater metrics
 */
def getSwaggerjsonForClearWaterMetrics(folderPath, repoName) {
    try {
        def fileName = "${PROPS.WORKING_DIRECTORY}/${repoName}/${folderPath}/swaggerCWM.json"
        sh( "cp ${PROPS.WORKING_DIRECTORY}/${repoName}/${folderPath}/swagger.json ${fileName}")
        sanitizeSwaggerSpec(fileName)
        def swaggerAsString = JSON.readFile(fileName)
        sh( "rm -rf ${fileName}")
        return swaggerAsString

    } catch(ex) {
        println "Could not load swagger file for Clearwater metrics" + ex.message
        throw new Exception("Could not load swagger file for Clearwater metrics", ex)
    }
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

/**
 * Update the authorizer for all methods of a resource
 * @param  stage string
 * @param  resourcepath string
 */
def GetSecurityUpdates(apiId, resourcePath, credsId) {
    def resource_id = null
    println resourcePath
    def resourcePathList = []
    def updates = []
    def resourceMap = [:]
    def methodsMap = [:]
    def methods = []

    try {
        def outputStr = sh("aws apigateway get-resources  --rest-api-id ${apiId} --output json --profile ${credsId}")
        def list = JSON.parseJson(outputStr)
        println "getResource output:: $list"
        for (item in list["items"]) {
            updates = []
            resourceMap = [:]
            methodsMap = [:]
            methods = []
            if(item["path"].startsWith(resourcePath)) {
                println "itm path matches......"
                resourceMap["resource_id"] = item["id"]
                if(item["resourceMethods"]){
                    if(item["resourceMethods"].toString().contains("POST")){methods << "POST"}
                    if(item["resourceMethods"].toString().contains("GET")){methods << "GET"}
                    if(item["resourceMethods"].toString().contains("PUT")){methods << "PUT"}
                    if(item["resourceMethods"].toString().contains("DELETE")){methods << "DELETE"}
                }
                methodsMap["methods"] = methods
                updates.add(resourceMap)
                updates.add(methodsMap)
                resourcePathList.add(updates);
               // break
            }

        }
        println "resourcePathList: $resourcePathList"
        return resourcePathList
    } catch(ex) {
        println "Update Security Failed " + ex.message
        throw new Exception("Update Security Failed ", ex)
    }
}

/**
    Pre-build validation of swagger api specifications
**/
def validateSwaggerSpec(custom_integration_spec) {
    println "================================ validating swagger spec ================================"
    // generating swagger with dummy values for validation
    def env = System.getenv()
    if(custom_integration_spec == null || custom_integration_spec.equals("false")) {
        sanitizeSwaggerSpec("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json")
        addAmazonIntegrationSpec(env.REPO_NAME, env.APIGATEWAY_LAMBDA_INTEGRATION_REPO)
    }
    def apiHostName = "test-cloud-api.corporate.t-mobile.com"
    updateSwaggerWithServiceDetails("test-service-for-validation", "test", "test-cloud-api", apiHostName, "domain", "test-service-for-validation")
    sh("swagger-tools validate ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/swagger/swagger.json")
    println "================================ validating swagger spec ================================"
}
