#!groovy?
import groovy.json.*
import custom.sls.*
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.File as FILE
import common.util.Yaml as YAML
import common.util.Status as Status
import static common.util.Shell.sh as sh
import java.lang.*

/*
* ServerlessPipelineUtility.groovy
* @author: Sini Wilson
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

def initialize () {
    println "Initializing custom service pipeline."
    
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
        println "=====================VALIDATION STARTED======================================="
        preBuildValidation()
        println "=====================VALIDATION ENDED======================================="
        
        println "=====================InstallPlugins STARTED======================================="
        installPlugins()
        println "=====================InstallPlugins ENDED======================================="
    } catch (ex) {
        println "Exception occured while initializing the custom service pipeline: + ${ex.message}"
        throw new Exception("Exception occured while initializing the custom service pipeline", ex)
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

        configureCustomServiceDeploy()

        println "============================================================"        

        def archiveEnvironmentId = JSON.getValueFromPropertiesFile('archiveEnvironmentId')

        if(archiveEnvironmentId) {
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
        throw new Exception("Exception occured in configDeployment: ", ex)
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
        int pos = repository.lastIndexOf("/");
        def repoSubstring = repository.substring(pos + 1 );
        def derivedRepoName = repoSubstring.replaceAll('.git','');
        println "derivedRepoName:- $derivedRepoName"
        def repoName = env.REPO_NAME ? env.REPO_NAME : derivedRepoName
        JSON.setValueToPropertiesFile('REPO_NAME', repoName)
        if (env.REPO_BRANCH == 'master') environmentDeploymentMetadata.setEnvironmentLogicalId('stg')
        def environmentId = environmentDeploymentMetadata.getEnvironmentId()
        qcpModule.initialize()
        println "Cloning the upstream repo"
        sh("git clone -b ${env.REPO_BRANCH} --depth ${PROPS.CLONE_DEPTH} ${repository}   ${PROPS.WORKING_DIRECTORY}/${repoName}")
        if(!commitSha) commitSha = sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};git rev-parse HEAD" )
        JSON.setValueToPropertiesFile('commitSha', commitSha)
        if (!env.REQUEST_ID ||(serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ) {        
            events.sendStartedEvent('CREATE_DEPLOYMENT', "", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started)) 
        } else {
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
        prepareServerlessYml()        
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

def getEnvironmentDeploymentDescriptor() {
    def environmentInfo = JSON.getValueFromPropertiesFile('environmentInfo')
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    //Get deployment descriptor from environments
    def environmentDeploymentDescriptor = environmentInfo['deployment_descriptor']
    if(environmentDeploymentDescriptor != null){
        return environmentDeploymentDescriptor
    } else {
        return serviceConfig['deployment_descriptor']
    }
}

def prepareServerlessYml () {
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    // copy serverless.yml from codebase to application.yml if it exists before it is overwritten and always returns true
    sh("cp ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/application.yml || true")

    // if neither exist serverless.yml or application.yml - create it from environment config 
    def slsBuildRules = new sbr()
    def deploymentDescriptorYml = slsBuildRules.prepareServerlessYml() // Generating the deployment descriptor
    println "prepareServerlessYml => ${deploymentDescriptorYml} - ${deploymentDescriptorYml.getClass().name}"
    // LinkedHashMap deploymentDescriptorMap = getMap(deploymentDescriptorYml)
    sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml") // just to be safe remove it
    YAML.writeYaml("${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml", deploymentDescriptorYml)
    println sh("cat ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}



def checkEnvironmentConfiguration (environmentDeploymentMetadata) {
    def qcpModule = new QcpModule()
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    try {
        def env = System.getenv()        
        def environmentLogicalId
        def archiveEnvironmentId = false

        if(env.REPO_BRANCH == 'master') {
            sh("cp -r ${PROPS.WORKING_DIRECTORY}/${repoName} ${PROPS.WORKING_DIRECTORY}/${repoName}_orig") 
            environmentLogicalId = 'stg'
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
        println "checkEnvironmentConfiguration failed: ${ex.message}"
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
//Loading user defined configurations form deployment-en.yml file of the user repo
def loadUserDefinedConfigs() {
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    def prop = [:]
    // handle the case where the codebase doesn't exists or is not a jazz project
    def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml")
    if (fExists) {
        println "deployment yaml exists"
        prop = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml")
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
            sh("echo '\n${k}: ${v}\n' >> ${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml")
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
    def serviceConfig = props['serviceConfig']
    def internalAccess = null
    if (serviceConfig['require_internal_access'] != null){
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
            def userDefinedConfig = props['userDefinedConfig']
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
}

def clearVirtualEnv() {
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};rm -rf venv")
    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};rm -rf virtualenv")
}

def installPlugins() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    events.sendStartedEvent("INSTALL_PLUGINS")    
    try {
        installServerlessPlugins()      
        events.sendCompletedEvent("INSTALL_PLUGINS")
    } catch (ex) {
        println "installServerlessPlugins failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("INSTALL_PLUGINS", ex.message, "FAILED") 
        events.sendFailureEvent("INSTALL_PLUGINS")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "installServerlessPlugins failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("installServerlessPlugins failed: ", ex)    
    }
}

def installServerlessPlugins(){
    try {
        def env = System.getenv()
        def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
        def whiteListModule = new WhiteListValidatorModule()
        def serverlessyml = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        println "serverlessyml- $serverlessyml - ${serverlessyml.getClass().name}"
        def outstandingPlugins = whiteListModule.validatePlugins(serverlessyml)
        println "outstandingPlugins- $outstandingPlugins"
        if(outstandingPlugins.isEmpty()) {
            def plugins = whiteListModule.getPluginsfromYaml(serverlessyml)
            if( plugins ) {
                for (plugin in plugins){
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};npm install ${plugin}  >> ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_install-plugin.log  2>&1")
                }
            } else {
                println "No plugins listed..skipping"
            }
        } else {
            throw new Exception( "The following plugins are not allowed: ${outstandingPlugins}")
        }
    } catch(ex){
        println "Plugin Installation Failed: ${ex.message}"
        throw new Exception( "Plugin Installation Failed ", ex)
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
        def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')       
        def sonar = new SonarModule()
        def fortifyScan = new FortifyScanModule()
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "codeQualityCheck", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))

        runtimeValidation(repoName)
        sonar.configureForProject(env.REPO_BRANCH, projectKey)
        sonar.doAnalysis()
        fortifyScan.doScan(projectKey)
        clearVirtualEnv()
        events.sendCompletedEvent("CODE_QUALITY_CHECK")
    } catch (ex) {
        println "codeQualityCheck failed: ${ex.message}"
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
    def env = System.getenv()

    if(serviceConfig['runtime'].indexOf("nodejs") > -1) {
        println "skipping validations for ${serviceConfig['runtime']}"
        // sh "jshint *.js"
    } else if(serviceConfig['runtime'].indexOf("java") > -1) {
        println "running validations for ${serviceConfig['runtime']}"
        sh("mkdir -p ${configData.CODE_QUALITY.SONAR.CHECKSTYLE_LIB}")
        sh("wget ${configData.CODE_QUALITY.SONAR.CHECKSTYLE_LIB} -P ${PROPS.JAVA_CONFIG_DIRECTORY}")
        sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};java -cp ../../${PROPS.JAVA_CONFIG_DIRECTORY}/checkstyle-7.6-all.jar com.puppycrawl.tools.checkstyle.Main -c sun_checks.xml src  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_runtime-validation.log  2>&1")
    } else if(serviceConfig['runtime'].indexOf("python") > -1) {
        println "skipping validations for ${serviceConfig['runtime']}"
    } else if(serviceConfig['runtime'].indexOf("go") > -1){
        println "skipping validations for ${serviceConfig['runtime']}"
    }
}


def buildServerless() {
    def env = System.getenv()
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def buildScriptExists = false
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
    events.sendStartedEvent("BUILD")    
    try {
        events.sendStartedEvent('UPDATE_DEPLOYMENT', "buildServerless", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))
        def customBuildExecuted = false
        buildScriptExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/build.slsapp")

        // If user provides custom build script, attempt to build the app using the user provided script
        if (buildScriptExists) {
            println "Code contains custom build script. Will read and execute.."
            def buildScriptContent = new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/build.slsapp").getText('UTF-8')
            println "Custom build script provided by user: ${buildScriptContent}"
            def buildScript = "bash build.slsapp > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-custom-script_${environmentLogicalId}.log 2>&1"
            // Making variable "env" available to the user script
            def setEnv = "export env=${environmentLogicalId};cd ${PROPS.WORKING_DIRECTORY}/${repoName};sed -i '1 i\\set -e' build.slsapp;"
            if (buildScriptContent  && buildScriptContent.trim()){
                println "Using custom build script.."

                // add setEnv
                buildScript = "${setEnv}${buildScript}"

                sh(buildScript)
                println "==================================================="
                println sh("cat ${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-custom-script_${environmentLogicalId}.log")

                customBuildExecuted = true
            } else {
                println "Custom build script is empty, will attempt to build using platfrom build instructions.."
            }
        }

        if (!customBuildExecuted){
            println "Using platform build instructions.."
            buildSlsApp()
        }      
        events.sendCompletedEvent("BUILD")
    } catch (ex) {       
        println "build failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("BUILD", ex.message, "FAILED")
        if (buildScriptExists) {
            println "==================================================="
            buildOut = sh("cat ${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-custom-script_${environmentLogicalId}.log")
            printColour(buildOut)
        }
        events.sendFailureEvent("BUILD")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "build failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
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

/**	Build project based on runtime
*/
def buildSlsApp() {
    try {
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
        def env = System.getenv() 
        println "installing dependencies for ${serviceConfig['runtime']}"
        if(serviceConfig['runtime'].indexOf("nodejs") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};npm install --save > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-app.log 2>&1")
        } else if(serviceConfig['runtime'].indexOf("java") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn package --settings settings_cdp.xml > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_mvn-package.log 2>&1")
        } else if(serviceConfig['runtime'].indexOf("python") > -1) {
            // install requirements.txt in library folder, these python modules will be a part of deployment package
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};rm -rf library")
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};mkdir library")
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};touch library/__init__.py")

            if ((serviceConfig['runtime'] == "python3.6") || (serviceConfig['runtime'] == "python3.8")) {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};pip3 install -r requirements.txt -t library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-app.log 2>&1")
            } else {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};pip install -r requirements.txt -t library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_build-app.log 2>&1")
            }
        } else if (serviceConfig['runtime'].indexOf("go") > -1 ){            
            //Installing dependencies using dep ensure           
            def CI_PROJECT_DIR = env['CI_PROJECT_DIR']
            def workspacePath = "${PROPS.WORKING_DIRECTORY}/${repoName}"
            def GOPATH = "${CI_PROJECT_DIR}/${workspacePath}"
            sh("export GOPATH=${GOPATH};mkdir -p $GOPATH/src")
            // sh("export GOPATH=${GOPATH};mkdir -p $GOPATH/src/${repoName}")
            // sh("export GOPATH=${GOPATH};cp -r ${PROPS.WORKING_DIRECTORY}/${repoName}/config $GOPATH/bin/")                
            sh("export GOPATH=${GOPATH};rsync -a --exclude='.*' --exclude='src' ${workspacePath}/*  $GOPATH/src/${repoName}")
            sh("export GOPATH=${GOPATH};rm -rf $GOPATH/pkg")
            sh("export GOPATH=${GOPATH};rm -rf $GOPATH/src/${repoName}/pkg")
            sh("export GOPATH=${GOPATH};cd $GOPATH/src/${repoName};dep ensure")
            dirs = getFunctionPaths()
            println "--------------------------------------"
            println "          dirs: $dirs"
            println "--------------------------------------"
            if(dirs != null && dirs.size() > 0) {
                for(item in dirs) {
                    def functionpath = "$GOPATH/src/${repoName}/${item}"
                    println "-----functionpath:----$functionpath"
                    sh("export GOPATH=${GOPATH};cd ${PROPS.WORKING_DIRECTORY}/${repoName};env GOOS=linux GOARCH=amd64 go build -o ${item}/main ${functionpath}/main.go;pwd")
                }
            } else {
                throw new Exception("No functions found.. Cannot complete the build step")
            }
        }
    } catch (ex) {
        println "buildSlsApp failed: ${ex.message}"
        throw new Exception("buildSlsApp failed: ", ex)    
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
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};npm test > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
        }else if(serviceConfig['runtime'].indexOf("java") > -1) {
            sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn test --settings settings_cdp.xml > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
        }else if(serviceConfig['runtime'].indexOf("python") > -1) {
            if((serviceConfig['runtime'] == "python3.6") || (serviceConfig['runtime'] == "python3.8")) {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; python3 -m venv virtualenv")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; . virtualenv/bin/activate")
                def devtxtExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/requirements-dev.txt")
                if(devtxtExists) {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip3 install -r requirements-dev.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip3 freeze -r requirements-dev.txt")
                } else {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip3 install -r requirements.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip3 freeze -r requirements.txt")
                }
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip3 install pytest")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip3 install coverage")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage run -m pytest --ignore=library > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage xml -i")
            } else {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip install virtualenv")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; virtualenv venv")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; . venv/bin/activate")
                def devtxtExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/requirements-dev.txt")
                if(devtxtExists) {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip install -r requirements-dev.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip freeze -r requirements-dev.txt")
                } else {
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip install -r requirements.txt")
                    println sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip freeze -r requirements.txt")
                }
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName}; pip install pytest")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};pip install coverage")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage run -m pytest --ignore=library >  ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_unit-test.log 2>&1")
                sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};coverage xml -i")
            }
        }else if( serviceConfig['runtime'].indexOf("go") > -1){
            def CI_PROJECT_DIR = env['CI_PROJECT_DIR']
            def workspacePath = "${PROPS.WORKING_DIRECTORY}/${repoName}"
            def GOPATH = "${CI_PROJECT_DIR}/${workspacePath}"
            sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME} && go test ./... -coverprofile=cov.out")
            sh("export GOPATH=${GOPATH};cd $GOPATH/src/${env.REPO_NAME} && go tool cover -func=cov.out")
        }
        
        def sonar = new SonarModule()
        sonar.cleanUpWorkspace()
        events.sendCompletedEvent("UNIT_TEST")
    } catch (ex) {
        println "runUnitTestCases failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("UNIT_TEST", ex.message, "FAILED")
        events.sendFailureEvent("UNIT_TEST")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "runUnitTestCases failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("runUnitTestCases failed: ", ex)    
    }    
}

/** 
* Get list of paths for functions. They are expected in functions/ directory
*/
def getFunctionPaths() {
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};ls -al")
    def targetPaths = null
    try {
        targetPaths = sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};ls -d functions/*").split("\n")
        println "completed : $targetPaths"
    } catch (ex) {
        println "Error occured while getting the functions list. Please make sure that your functions are available in functions/ directory: ${ex.message}"
    }
    return targetPaths
}

def configureCustomServiceDeploy() {
    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def utilModule = new UtilityModule()
    def env = System.getenv()
    def deploymentAccountCreds = null;
    def credsId = utilModule.generateCreds(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)

    /*
    * Getting temporary credentials for cross account role if not primary account
    */
    deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, env.AWS_DEFAULT_REGION);

    try {
        println "============================================================"
        println "               SERVERLESS PACKAGE                           "
        println "============================================================"
        
        serverlessPackage(events, environmentDeploymentMetadata, qcpModule, slack, deploymentAccountCreds)

        println "============================================================"
        println "               SERVERLESS TEMPLATE VALIDATION               "
        println "============================================================"
        
        serverlessTemplateValidation(events, environmentDeploymentMetadata, qcpModule, slack, deploymentAccountCreds)

        println "============================================================"
        println "               DEPLOY TO AWS                                "
        println "============================================================"
        
        awsDeploy(events, environmentDeploymentMetadata, qcpModule, slack, deploymentAccountCreds)
    } catch (ex) {
        println "configureCustomServiceDeploy failed: ${ex.message}"
        throw new Exception("configureCustomServiceDeploy failed: ", ex)    
    } finally {
        println "configureCustomServiceDeploy:=:Resetting credentials"
        resetCredentials(deploymentAccountCreds)
    } 
    
}

def productionDeploy() {
    def qcpModule = new QcpModule()
    def events = new EventsModule()
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def utilModule = new UtilityModule();
    def status = 'failed'
    try {
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
        prepareServerlessYml()
        // Build the serverless for prod
        buildServerless()
        configureCustomServiceDeploy()
    } catch (ex) {
        println "Production deployment failed: ${ex.message}"
        if(status === 'REJECTED') {
            events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message, environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.approval_rejected))
        } else {
            events.sendFailureEvent('UPDATE_DEPLOYMENT', ex.message, environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        }
        throw new Exception("Production deployment failed", ex)
    }
}

def serverlessTemplateValidation(events, environmentDeploymentMetadata, qcpModule, slack, credsId) {
    events.sendStartedEvent("TEMPLATE_VALIDATION")    
    try {
        def props = JSON.getAllProperties()
        def env = System.getenv()
        def whiteListModule = new WhiteListValidatorModule()
        def environmentLogicalId = props.environmentLogicalId
        def currentEnvironment = (environmentLogicalId == "prod" || environmentLogicalId == "stg") ? environmentLogicalId : "dev"
        def cftJson = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${props.REPO_NAME}/.serverless/cloudformation-template-update-stack.json")
        def outstandingResources = whiteListModule.validate(cftJson)
        if(outstandingResources.isEmpty()) {
          def serverlessyml = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${props.REPO_NAME}/serverless.yml")
          def outstandingActions = whiteListModule.validateActions(serverlessyml)
          if(outstandingActions.isEmpty()) {
            def outstandingPlugins = whiteListModule.validatePlugins(serverlessyml)
            if(outstandingPlugins.isEmpty()) {
                events.sendCompletedEvent("TEMPLATE_VALIDATION")
            } else {
              throw new Exception("The following plugins are not allowed: ${outstandingPlugins}")
            }
          } else {
            throw new Exception( "The action types are not allowed: ${outstandingActions}")
          }
        } else {
          throw new Exception( "The resource types not allowed: ${outstandingResources}")
        }      
        
    } catch (ex) {
        println "serverless template validation failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("TEMPLATE_VALIDATION", ex.message, "FAILED") 
        events.sendFailureEvent("TEMPLATE_VALIDATION")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "serverless template validation failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("serverless template validation failed: ", ex)    
    }
}

def serverlessPackage(events, environmentDeploymentMetadata, qcpModule, slack, credsId) {
    events.sendStartedEvent("PACKAGE")    
    try {
        def props = JSON.getAllProperties()
        def env = System.getenv()
        def environmentLogicalId = props.environmentLogicalId
        def s3bucketvalue = getServerlessDeploymentBucketInfo(props.accountDetails)
        def currentEnvironment = (environmentLogicalId == "prod" || environmentLogicalId == "stg") ? environmentLogicalId : "dev"
        
        sh("cd ${PROPS.WORKING_DIRECTORY}/${props.REPO_NAME};serverless package --stage ${environmentLogicalId} --bucket ${s3bucketvalue} --profile ${credsId} -v > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-package.log 2>&1 || true")

        // Printing out the resulting stdout and the cloud-formation template
        def outputLog = new java.io.File("${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-package.log").text
        println "serverless packaging log $outputLog"

        // will fail if ./.serverless/cloudformation-template-update-stack.json file DOES NOT exist
        // because of error in the previous step
        def cft = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${props.REPO_NAME}/.serverless/cloudformation-template-update-stack.json")
        println "Resultant CloudFormation JSON after package step: $cft"
        
        events.sendCompletedEvent("PACKAGE")
    } catch (ex) {
        println "serverlessPackage failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("PACKAGE", ex.message, "FAILED") 
        events.sendFailureEvent("PACKAGE")
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "serverlessPackage failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("serverlessPackage failed: ", ex)    
    }  
}

def getServerlessDeploymentBucketInfo(accountDetails) {
    def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def s3BucketName;
    def deploymentStage = (environmentLogicalId == "prod" || environmentLogicalId == "stg") ? environmentLogicalId.toUpperCase(): "DEV"
    
    for (item in accountDetails.REGIONS) {
        if(item.REGION == serviceConfig.region){
            s3BucketName = item.S3[deploymentStage]
        }
    }
    return s3BucketName
}

def awsDeploy(events, environmentDeploymentMetadata, qcpModule, slack, credsId) {    
    def props = JSON.getAllProperties()
    def env = System.getenv()
    def environmentLogicalId = props['environmentLogicalId']
    def serviceConfig = props['serviceConfig']
    def accountDetails = props['accountDetails']
    def repoName = props['REPO_NAME']
    def lambdaEvents = new AWSLambdaEventsModule()
    def serviceConfigDataLoader = new ServiceConfigurationDataLoader()
    def utilModule = new UtilityModule()
    def oldArnsMap = [:]

    events.sendStartedEvent("DEPLOY_TO_AWS")
    events.sendStartedEvent('UPDATE_ENVIRONMENT', "awsDeploy started.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_started")) 
    try {
        //**************************
        //Getting the list of aws resources associated with the CF stack before deployment. This is for comparing the 
        // the stack resources with the CF stack after deployment
        def oldCloudformationStackResources = getStackResources(credsId)
        //Getting the stack resources as map
        if (oldCloudformationStackResources) oldArnsMap = createAllStackResources(oldCloudformationStackResources, false, events)

        //*************************
        
        def deployOutput = serverlessDeploy(credsId)
        if (deployOutput != 'success') {
            if (utilModule.checkCfResourceFailed(credsId) || utilModule.checkCfFailed(deployOutput)) {
                deleteAndRedeployService(credsId)
            } else {
                throw new Exception("Exception occured during deployment!")
            }
        }
        // After stack deployment
        def deployedServiceInfo = echoServiceInfo(accountDetails, environmentLogicalId, credsId)
                
        def environmentName = getEnvironmentTag(environmentLogicalId)
        def deploymentDescriptor = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        attachTagstoLogGroup(credsId, environmentName, deploymentDescriptor, props['deploymentRegion'])
        //**************
        //Getting the list of aws resources associated with the CF stack after deployment. This is for comparing the 
        // the stack resources with the CF stack after deployment
        def newCloudformationStackResources = getStackResources( credsId)
        //Getting the stack resources as map
        def arnsMap = createAllStackResources(newCloudformationStackResources, true, events)
        
        //If there is old CF stack resources, comparing the old and new stack resources
        if (oldCloudformationStackResources) archiveOldAssets( arnsMap, oldArnsMap, events)             
        //****************
        
        def deploymentDescriptorStr = new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml").text
        println "deploymentDescriptorStr:- $deploymentDescriptorStr"
        
        qcpModule.sendQCPEvent("Post", "success")
        events.sendCompletedEvent("DEPLOY_TO_AWS")
        events.sendCompletedEvent('UPDATE_ENVIRONMENT', "awsDeploy completed.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_completed", null, deploymentDescriptorStr)) 
        events.sendCompletedEvent('UPDATE_DEPLOYMENT', "awsDeploy completed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.successful))
    } catch (ex) {
        println "awsDeploy failed: ${ex.message}"
        qcpModule.sendQCPEvent("Post", "failure")
        slack.sendSlackNotification("DEPLOY_TO_AWS", ex.message, "FAILED")
        events.sendFailureEvent("DEPLOY_TO_AWS")
        events.sendFailureEvent('UPDATE_ENVIRONMENT', "awsDeploy failed.", environmentDeploymentMetadata.generateEnvironmentMap("deployment_failed")) 
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "awsDeploy failed.", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("awsDeploy failed: ", ex)    
    } 
}

def serverlessDeploy(credsId){
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    def env = System.getenv()
    def ip = sh("curl ifconfig.co");
    println "Build agent IP: ${ip}";

    try {
        def accountDetails = JSON.getValueFromPropertiesFile('accountDetails')       
        def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
        def s3bucketvalue = getServerlessDeploymentBucketInfo(accountDetails)
        
        println "----------------------serverless.yml-----------------------------"
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml") 
        println "----------------------deployment-env.yml----------------------"
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml") 
        println "-----------------------------------------------------"
        println "deploying $repoName to $environmentLogicalId"
        sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};serverless deploy --force --stage $environmentLogicalId --bucket ${s3bucketvalue} --aws-profile ${credsId} -v > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-deploy.log 2>&1" )
        println "==================================================="
        println sh("cat ${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-deploy.log")
        println "==================================================="
        println "-----------------DEPLOYMENT DONE-------------------"
        return "success"
    } catch (ex) {
        println "Serverless deployment failed due to ${ex.message}"
    }
    def outputLog = new java.io.File("${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-deploy.log").text
    println "serverless deployment log $outputLog"
    return outputLog
}

def deleteAndRedeployService(credsId){
    def props = JSON.getAllProperties()
    def serviceConfig = props['serviceConfig']
    def configData = props['configData']
    def utilModule = new UtilityModule()
    def accountDetails = JSON.getValueFromPropertiesFile('accountDetails')
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
    def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
    def s3bucketvalue = getServerlessDeploymentBucketInfo(accountDetails)
    def cfstack = "${configData.INSTANCE_PREFIX}-${serviceConfig['domain']}--${serviceConfig['service']}-${environmentLogicalId}"        
    try {
        sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};serverless remove --stage $environmentLogicalId --bucket ${s3bucketvalue} --aws-profile ${credsId} > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-remove.log 2>&1;pwd" )
    } catch (ex) {
        println "serverless remove failed"
        // GO FOR CF DELETE
        utilModule.deletecfstack(credsId, cfstack, serviceConfig['region'])
    }
    def redeployOutput = serverlessDeploy(credsId)
    if (redeployOutput != 'success') {
        throw new Exception("Exception occured while serverless deployment to ${environmentLogicalId} env")
    }
}

def getStackResources (credsId) {
    def props = JSON.getAllProperties()
    def environmentLogicalId = props['environmentLogicalId']
    def serviceConfig = props['serviceConfig']
    def accountDetails = props['accountDetails']
    def configData = props['configData']

    def cfStackName = "${configData.INSTANCE_PREFIX}-${serviceConfig['domain']}--${serviceConfig['service']}-${environmentLogicalId}"
    def stackResources = null	

    try {
        stackResources = sh("aws cloudformation describe-stack-resources --stack-name ${cfStackName} --profile ${credsId} --region ${serviceConfig['region']}")
        println "Describe Stacks are ${stackResources}"
        def parsedResources = JSON.parseJson(stackResources)
        return parsedResources
    } catch (ex) {
        println "stack does not exist."
        try {
            def resp = sh( "aws cloudformation describe-stack-resources --stack-name ${cfStackName} --region ${serviceConfig['region']} --profile ${credsId}  --output json 2<&1 | grep -c 'ValidationError'")
            if(resp != 1) throw new Exception("describe stack failed.")
            else return stackResources
        } catch (e) {
            println "Cloudformation describe stack failed: ${e.message}"
        }
    }
}

def createAllStackResources (stackResources, isCreateAsset, events) {
    def serv
    def arnsMap = [:]
    def whiteListModule = new WhiteListValidatorModule()
    def resources = stackResources['StackResources']
    def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
    if(resources != null) {
        def assetCatalogTypes = whiteListModule.getassetCatalogTypes()
        println "assetCatalogTypes - $assetCatalogTypes"
        for(resource in resources) {
            def resourceType = resource['ResourceType']
            println "resourceType - $resourceType"
            if(whiteListModule.checkAssetType(resourceType)){
                def arn = constructArn(resource['PhysicalResourceId'], resourceType)
                if(arn.startsWith('arn') || (resourceType == "AWS::ApiGateway::RestApi" || resourceType == "AWS::ApiGatewayV2::Api" || resourceType == "AWS::CloudFront::Distribution")) {
                    
                    // Here we splitting the arn itself in hope to obtain type of the resource. 
                    // Should be the third element: arn:aws:dynamodb:us-east-1:123456:table/jazzUsersTable53
                    def arnAsArray = arn.split(':')

                    if(arnAsArray.size() > 2) { // Making sure that the third element exists
                        def artifactType = arnAsArray[2]
                        arnsMap = addStackResourcesToMap(arnsMap, assetCatalogTypes[artifactType], arn)
                        
                        // We need to send events only after deployment, and we don't need to send the event while we are calling this method only for creating the resource map
                        if (isCreateAsset) {
                            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", arn, assetCatalogTypes[artifactType]))
                        }

                        // Determining endpoint_url for certain asset types!
                        def endpoint_url = "";
                        if (resourceType == "AWS::ApiGateway::RestApi" || resourceType == "AWS::ApiGatewayV2::Api") {
                            endpoint_url = constructArn(resource['PhysicalResourceId'], 'AWS::ApiGateway::Endpoint')
                            
                            if (resourceType == "AWS::ApiGateway::RestApi"){
                                endpoint_url = endpoint_url + "/${environmentLogicalId}"
                            } else {
                                // TODO: Api v2 doesn't require $stage.
                                // This requires additional investigation & follow-up.
                            }
                        }
                        if (resourceType == "AWS::CloudFront::Distribution") {
                            if (isCreateAsset) {
                                endpoint_url = getCloudFrontEndpoint()
                            }
                        }
                        endpoint_url = endpoint_url.trim();
                        // Creating asset for endpoint url
                        if (isCreateAsset && !endpoint_url.isEmpty()) {
                            events.sendCompletedEvent('CREATE_ASSET', null, generateAssetMap("aws", endpoint_url, 'endpoint_url'))
                        }
                        if (!endpoint_url.isEmpty()) {
                            arnsMap = addStackResourcesToMap(arnsMap, 'endpoint_url', endpoint_url)
                        }
                    }
                } 
            }
        }
    }

  println "arnsMap: $arnsMap"

  return arnsMap
}

def getCloudFrontEndpoint() {
    println "DeployServerlessUtilityModule.groovy:getCloudFrontEndpoint"
    def env = System.getenv()
    def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')

    //read from serverless deployment output file
    
    def endpoint_url = "";
    def fExists = JSON.isFileExists("../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-deploy.log")
    if (fExists) {
        def svlsOutputFile = new File("../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-deploy.log");
        
        //find entry for Cloudfront distribution endpoint
        def data = svlsOutputFile.filterLine { line ->
            line.contains('WebAppCloudFrontDistributionDomainName: ')
        }

        def stringData = data.toString();
        println "Read from file: $stringData"

        endpoint_url = 'https://' + stringData.split(': ')[1];
    }

    println "endpoint_url is: $endpoint_url"
    return endpoint_url
}


def addStackResourcesToMap(arnsMap, resourceType, arn ) {
    if (arnsMap[resourceType] && arnsMap[resourceType].size() > 0) 
        arnsMap[resourceType].add(arn)
    else {
        arnsMap[resourceType] = new ArrayList()
        arnsMap[resourceType].add(arn)
    }

    return arnsMap
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

def constructArn(resourceName, resourceType) {   
    try{
        def whiteListModule = new WhiteListValidatorModule()
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def arn = whiteListModule.getArnTemplate(resourceType)
        //Get queueName from url
        if( resourceType == "AWS::SQS::Queue"){
            resourceName  = resourceName.substring(resourceName.lastIndexOf("/") + 1)
        }
        if( arn != null ) {
            arn = arn.replaceAll("\\{region\\}", "${serviceConfig['region']}")
            arn = arn.replaceAll("\\{account-id\\}", "${serviceConfig['account']}")
            arn = arn.replaceAll("\\{resourceName\\}", "${resourceName}")
        } else {
            throw new Exception("Arn Templates not found for Resource Type : ${resourceType}")
        }
        return arn
    } catch (ex){
        println "Exception occured on getting Arn templates for Resource Type : ${resourceType}:- ${ex.message}"
        throw new Exception("Exception occured on getting Arn templates for Resource Type : ${resourceType}", ex)
    }    
}

def echoServiceInfo(accountDetails, env, credsId) {
    try {
        def s3bucketvalue = getServerlessDeploymentBucketInfo(accountDetails)
        def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
        println "Deployment output information - "
        sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};serverless --stage $env --bucket ${s3bucketvalue} --aws-profile ${credsId} info -v > deploy-info.txt 2>&1")
        sh("cat ${PROPS.WORKING_DIRECTORY}/${repoName}/deploy-info.txt")
        return new java.io.File("${PROPS.WORKING_DIRECTORY}/${repoName}/deploy-info.txt").text
    } catch (Exception ex) {
        println "Error while getting service info: " + ex.message
    }
}

def archiveOldAssets( newMap, oldMap, events) {  
    def newKeys = newMap.keySet()
    def oldKeys = oldMap.keySet()

    def removedKeys = oldKeys - newKeys
    def addedKeys = newKeys - oldKeys
    def commonKeys =newKeys - removedKeys - addedKeys
    def changedKeys = commonKeys.findAll { oldMap[it] != newMap[it] }

    def removed = oldMap.findAll { it.key in removedKeys }
    def added = newMap.findAll { it.key in addedKeys }
    def changed = oldMap.findAll { it.key in changedKeys }
    def changedLists = changed.each {key, list -> list.removeAll(newMap[key]) }

    println "removedKeys: $removedKeys"
    println "addedKeys: $addedKeys"
    println "changedKeys: $changedKeys"
    println "removed: $removed"
    println "added: $added"
    changedLists << removed
    println "changedLists: $changedLists"
    changedLists.each {key, list -> list.each {it -> 
            events.sendCompletedEvent('UPDATE_ASSET', "Archiving the old assets since user changed the yml.", generateAssetMap("aws", it, key, "archived"))
        }
    }
}

def generateAssetMap(provider, providerId, type, status = null) {
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def serviceCtxMap = [
        provider: provider,
        provider_id: providerId,
        type: type,
        created_by: serviceConfig['created_by']
    ]
    if (status) serviceCtxMap.status = status
    return serviceCtxMap;
}

def attachTagstoLogGroup(credsId, environmentName, deploymentDescriptor, deploymentRegion){
    try {
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
        def gitCommitSha = JSON.getValueFromPropertiesFile('commitSha')
        // If no descriptor present then simply making an empty one. The readYaml default behavior is to return empty string back that is harmful as Map not String is expected below
        def doc = deploymentDescriptor ?: [:]
        def functionsElem = doc['functions']

        // changing variable names appropriately
        def serviceDomain = "${serviceConfig['domain']}_${serviceConfig['service']}"
        functionsElem.each { functionName, funcConfig ->
            def lambdaName = funcConfig['name'] ? funcConfig['name'] : "${serviceDomain}_${functionName}_${environmentLogicalId}"
            def logGroupName = "/aws/lambda/${lambdaName}"
            def serviceName = serviceConfig['service']
            def namespace = serviceConfig['domain']
            def applicationId = serviceConfig['appId']
            def owner = serviceConfig['owner']
            def application = serviceConfig['appTag']
            def platform = 'jazz'
            def environment = environmentLogicalId
            def tagResult = sh("aws logs tag-log-group --log-group-name ${logGroupName} \
            --tags '{\"Service\":\"${serviceName}\", \"Domain\":\"${namespace}\", \"EnvironmentId\":\"${environment}\", \"FunctionName\":\"${lambdaName}\", \"ApplicationId\":\"${applicationId}\", \"Owner\":\"${owner}\", \"Application\":\"${application}\", \"GitCommitHash\":\"${gitCommitSha}\", \"Environment\":\"${environmentName}\", \"Platform\":\"${platform}\"}' --profile ${credsId} --region ${deploymentRegion}", true)

            println "Set tags result: $tagResult"
        
        }
    } catch (ex) {
        println "Error occured while attaching tags to logGroup ${ex.message}"
    } 
}

def echoServiceInfo(arn) {
    println "======================================================================="
    println "Function deployed: $arn"
    println "========================================================================"
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
        def fromStr = 'Jazz Admin <' + configData.JAZZ.ADMIN + '>'
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
        } catch (ex) {
            println "Failed while sending build status notification:- ${ex.message}"
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

def resetCredentials(credsId) {
    println "resetting AWS credentials"
    sh("aws configure set profile.${credsId}.aws_access_key_id XXXXXXXXXXXXXXXXXXXXXXXXXX")
    sh("aws configure set profile.${credsId}.aws_secret_access_key XXXXXXXXXXXXXXXXXXXXXX")
}

def configureProductionDeploy() {
    try {
        def env = System.getenv()
        def events = new EventsModule()
        def repoName = JSON.getValueFromPropertiesFile('REPO_NAME')
        def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
        resetEnvironmentSpecificConfigurations()
        environmentDeploymentMetadata.setEnvironmentLogicalId("prod")
        // calling the method sets the environmentId in the properties file
        def environmentId = environmentDeploymentMetadata.getEnvironmentId()

        sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml")
        sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        
        sh("cp ${PROPS.WORKING_DIRECTORY}/${repoName}_orig/serverless.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        
        // deployment-env.yml is not a required file!
        def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}_orig/deployment-env.yml")
        if (fExists) {
            sh("cp ${PROPS.WORKING_DIRECTORY}/${repoName}_orig/deployment-env.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/deployment-env.yml")
        }
        
        checkDeploymentConfiguration (environmentDeploymentMetadata)
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
