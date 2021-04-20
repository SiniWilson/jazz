#!groovy?
import common.util.Shell as ShellUtil
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.Yaml as YAML
import common.util.File as FILE
import static common.util.Shell.sh as sh
import java.lang.*


/*
* CreateServiceUtilityModule.groovy
* @author: Sini Wilson
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

//Validating creation flow input
def validateInput () {
    println "CreateServiceUtilityModule.groovy:validateInput"
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    if(!serviceConfig) {
      println "No Service configuration"
      throw new Exception("No Service configuration")
    }
    if(!serviceConfig.approvers ) {
        println "Invalid Admin Group"
        throw new Exception("Invalid Admin Group")
    }
    if (!(serviceConfig.type in ["website", "sls-app"])) {
        println "Invalid service type: ${serviceConfig.type}"
        throw new Exception("Invalid service type: ${serviceConfig.type}")
    }
    if (serviceConfig.type == "website"){
        println "serviceConfig.providerRuntime - ${serviceConfig.providerRuntime}"
        if (serviceConfig.providerRuntime && !(serviceConfig.providerRuntime in ['','n/a'])) {
            println "Invalid runtime for services of type: ${serviceConfig.type}"
            throw new Exception("Invalid runtime for services of type: ${serviceConfig.type}")
        }
    }

}

/*
* Function to initialize and setup all the modules
*/
def initializeModules() {
    println "CreateServiceUtilityModule.groovy: initializeModules"
    showServiceEnvParams()
    def loginModule = new Login()
    def configLoaderModule = new ConfigLoader()
    def serviceMetadaLoader = new ServiceMetadataLoader()
    def eventsModule = new EventsModule()
    def aclModule = new AclModule()
    /*
    * Initializing and setting up the modules
    */
    loginModule.getAuthToken()
    configLoaderModule.getConfigData()
    serviceMetadaLoader.getServiceDetails()
    // update only non-code policies
    def policyOnly = true
    def readOnly = true
    // setting to readonly initially to force update of casbin policy in acl job
    aclModule.updateServiceACL(policyOnly, readOnly)
    
    eventsModule.sendStartedEvent('VALIDATE_INPUT')
    validateInput()
    /*
    * Changing the permission of the script in order to run it
    */
    sh("chmod +rx ${PROPS.WORKING_MODULE_DIRECTORY}/scripts/OnCompletionJob.sh")
    eventsModule.sendCompletedEvent('VALIDATE_INPUT')
}

//Cloning the service template
def getServiceTemplate () {
    println "CreateServiceUtilityModule.groovy:getServiceTemplate"
    
    def env = System.getenv()
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    try {
        def repoBaseUrl = env['TEMPLATE_BASE_URL']
        def gitRepoUrl = repoBaseUrl + serviceConfig.templateRepository.split('templates/')[1]
        /**
        * Cloning the template
        */
        sh("git clone -b master --depth 1 ${gitRepoUrl} ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}")
    } catch(ex) {
        ex.printStackTrace()
        throw new Exception("getServiceTemplate - failed", ex)
    }
}

//Writing deployment descriptor to the serverlss.yml file
def writeDeploymentDescriptor () {
    println "CreateServiceUtilityModule.groovy: writeDeploymentDescriptor"
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    // Update serverless.yml file with service info  
    sh("sed -i -- 's/{service_name}/${serviceConfig.service}/g' ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}/serverless.yml")
    sh("sed -i -- 's/{provider_name}/${serviceConfig.provider}/g' ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}/serverless.yml")
    sh("sed -i -- 's/{runtime}/${serviceConfig.runtime}/g' ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}/serverless.yml")
    println "updated serverless.yml"
}

/*
* Function to print env variables of service pipeline
*/
def showServiceEnvParams() {
    def env = System.getenv()
    
    println "======================================"
    println "========= BUILD PARAMETERS ==========="
    println "REQUEST_ID: $env.REQUEST_ID"
    println "SERVICE_ID: $env.SERVICE_ID"
    println "SCM_MANAGED: $env.SCM_MANAGED"
    println "======================================"
}

/*
* Function to create template with service details
*/
def createTemplate() {
    println "CreateServiceUtilityModule.groovy: createTemplate"

    def eventsModule = new EventsModule()
    def scmModule = new ScmModule()
    def env = System.getenv()
    def eventName;
    try {
        /*
        * Cloning the template
        */
        eventName = 'CLONE_TEMPLATE'
        eventsModule.sendStartedEvent(eventName)
        getServiceTemplate();
        eventsModule.sendCompletedEvent(eventName)

        /*
        * Updating the template
        */
        eventName = 'MODIFY_TEMPLATE'
        eventsModule.sendStartedEvent(eventName)
        updateTemplate();
        eventsModule.sendCompletedEvent(eventName)

        /*
        * creating service repository
        */
        eventName = 'CREATE_SERVICE_REPO';
        eventsModule.sendStartedEvent(eventName)
        scmModule.createProject(env['GITLAB_SVC_ACCT_PASSWORD']);
        eventsModule.sendCompletedEvent(eventName)

        /*
        * adding webhook
        */
        eventName = 'ADD_WEBHOOK';
        eventsModule.sendStartedEvent(eventName)
        scmModule.addWebhook(env['GITLAB_SVC_ACCT_PASSWORD'], env['WEBHOOK_URL']);
        eventsModule.sendCompletedEvent(eventName)

        /*
        * pushing template
        */
        eventName = 'PUSH_TEMPLATE_TO_SERVICE_REPO';
        eventsModule.sendStartedEvent(eventName)
        pushTemplate();
        eventsModule.sendCompletedEvent(eventName)

        /*
        * set branch permission
        */
        eventName = 'LOCK_MASTER_BRANCH';
        eventsModule.sendStartedEvent(eventName)
        scmModule.setBranchPermissions(env['GITLAB_SVC_ACCT_PASSWORD']);
        eventsModule.sendCompletedEvent(eventName)


        
    } catch(ex) {
        ex.printStackTrace()
        eventsModule.sendFailureEvent(eventName, 'Failure happened while creating template')
        throw new Exception("createTemplate - failed", ex)
    }
}

/*
* Function to update the cloned template with service details
*/
def updateTemplate() {
    println "CreateServiceUtilityModule.groovy: updateTemplate"

    def env = System.getenv()
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    try {
        def serviceId = env['SERVICE_ID']
        /*
        * Updating the service template
        */
        sh("echo -e '\nservice_id: ${serviceId}' >> ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}/deployment-env.yml")

        if(serviceConfig.type == 'sls-app') {
            println "service type is sls-app"
            /*
            * write user provided deployment descriptor to file which will be checked in
            * using writeFile instead of writeYaml as the deployment descriptor is already valid YAML
            */
            writeDeploymentDescriptor();
            /*
            * Relative/Absolute path doesn't work in Go
            * So we need this piece of code
            */
            if(serviceConfig.providerRuntime.startsWith("go")) {
                println "Updating go template in the serverless app"

                def dirs = getFunctionsInDir()
                println "dirs: ${dirs}"
                def size = dirs.size()
                if (size > 0) {
                    for (item in dirs) {
                        def functionPath = "./${item}"
                        println "functionPath: $functionPath"
                        sh("sed -i -- 's/{go_template}/${serviceConfig.domain}_${serviceConfig.service}/g' ${functionPath}/main.go")
                    }
                } else {
                    println "No functions found.. Skipping code updates to the template!"
                }

            }
        }


    } catch(ex) {
        ex.printStackTrace()
        throw new Exception("updateTemplate - failed", ex)
    }
}

/**
 * Get all functions in the current directory under a specific folder - "./functions/"
 */
def getFunctionsInDir() {
    println "CreateServiceUtilityModule.groovy: getFunctionsInDir"

    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def contents = sh("ls -al ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}")
    println "contents: ${contents}"
    def targetPaths
    try {
        targetPaths = sh("ls -d ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}/functions/*").split("\n")
    } catch (ex) {
        println "Error occcured while getting functions in the current directory, error: " + ex
    }
    return targetPaths
}

/*
* Function to push template
*/
def pushTemplate() {
    println "CreateServiceUtilityModule.groovy: pushTemplate"

    def env = System.getenv()
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def configData = JSON.getValueFromPropertiesFile('configData')
    def svcAdmin = env['JAZZ_SVC_ACCT_USER']
    try {
        def gitRepoUrl = env['REPO_BASE_URL'] + serviceConfig.domain + '_' + serviceConfig.service + '.git'
        println "gitRepoUrl: ${gitRepoUrl}"

        sh("git clone ${gitRepoUrl}")

        sh("mv -nf ${PROPS.WORKING_TEMPLATE_DIRECTORY}/${serviceConfig.domain}_${serviceConfig.service}/* ${serviceConfig.domain}_${serviceConfig.service}/")
        if(serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) {
            println "Renaming the gitlab-ci.yml to .gitlab-ci.yml since userPipeline is true."
            sh("mv -nf ${serviceConfig.domain}_${serviceConfig.service}/gitlab-ci.yml ${serviceConfig.domain}_${serviceConfig.service}/.gitlab-ci.yml")
        }
        
        sh("git config --global user.email ${configData.JAZZ.ADMIN}")
        sh("git config --global user.name ${svcAdmin}")

        sh("cd ${serviceConfig.domain}_${serviceConfig.service}; git add --all; git add . --force; git commit -m 'Code from the standard template'; git remote -v; git push -u origin master")

    } catch(ex) {
        ex.printStackTrace()
        throw new Exception("pushTemplate - failed", ex)
    }
}

/*
* Function to update acl permissions
*/
def updateAcl() {
    println "CreateServiceUtilityModule.groovy: updateAcl"

    def eventsModule = new EventsModule()
    def aclModule = new AclModule()
    def eventName;;
    try {
        eventName = 'ADD_POLICIES_AND_REPO_PERMISSIONS'
        eventsModule.sendStartedEvent(eventName)
        
        aclModule.updateServiceACL();

        eventsModule.sendCompletedEvent(eventName)

    } catch(ex) {
        ex.printStackTrace()
        eventsModule.sendFailureEvent(eventName, 'Failure happened while upadting acl permissions')
        throw new Exception("updateAcl - failed", ex)
    }
}
