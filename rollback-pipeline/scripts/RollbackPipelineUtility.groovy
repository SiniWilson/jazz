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


static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

def validatePipelineTriggerParams() {
    def env = System.getenv()
    if(!env.TIMESTAMP) {
        println "No TIMESTAMP in the request, failing the workflow!"
        throw new Exception("No TIMESTAMP in the request, failing the workflow!");
    }
    if(!env.COMMIT_SHA) {
        println "No COMMIT_SHA in the request, failing the workflow!"
        throw new Exception("No COMMIT_SHA in the request, failing the workflow!");
    }
    if(!env.SERVICE_ID) {
        println "No SERVICE_ID in the request, failing the workflow!"
        throw new Exception("No SERVICE_ID in the request, failing the workflow!");
    }   
    if(!env.DOMAIN) {
        println "No DOMAIN in the request, failing the workflow!"
        throw new Exception("No DOMAIN in the request, failing the workflow!");
    }
    if(!env.SERVICE) {
        println "No SERVICE in the request, failing the workflow!"
        throw new Exception("No SERVICE in the request, failing the workflow!");
    } 
    if(!env.ENVIRONMENT_ID) {
        println "No ENVIRONMENT_ID in the request, failing the workflow!"
        throw new Exception("No ENVIRONMENT_ID in the request, failing the workflow!");
    } 
    if(!env.ENVIRONMENT_LOGICAL_ID) {
        println "No ENVIRONMENT_LOGICAL_ID in the request, failing the workflow!"
        throw new Exception("No ENVIRONMENT_LOGICAL_ID in the request, failing the workflow!");
    }
    if(!env.TRIGGERED_BY) {
        println "No TRIGGERED_BY in the request, failing the workflow!"
        throw new Exception("No TRIGGERED_BY in the request, failing the workflow!");
    }  
    if(!env.DEPLOYMENT_ID) {
        println "No DEPLOYMENT_ID in the request, failing the workflow!"
        throw new Exception("No DEPLOYMENT_ID in the request, failing the workflow!");
    } 
}

def configRollback() {
    validatePipelineTriggerParams()

    def events = new EventsModule();
    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader()
    def serviceMeta = new ServiceMetadataLoader()
    def qcpModule = new QcpModule()
    def slack = new SlackModule()
    def utilModule = new UtilityModule();
    def env = System.getenv()
    try {
        serviceMeta.getServiceDetails(env.SERVICE_ID)
        JSON.setValueToPropertiesFile('environmentLogicalId', env.ENVIRONMENT_LOGICAL_ID)
        JSON.setValueToPropertiesFile('environmentId', env.ENVIRONMENT_ID)
        JSON.setValueToPropertiesFile('commitSha', env.COMMIT_SHA)
        environmentDeploymentMetadata.getEnvironmentInfo()
        JSON.setValueToPropertiesFile('rollback', true)
        def rollback_metadata = [:]
        rollback_metadata["DEPLOYMENT_ID"] = env.DEPLOYMENT_ID
        JSON.setValueToPropertiesFile('rollback_metadata', rollback_metadata)
        
        def props = JSON.getAllProperties()
        def configData = props['configData']
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')

        checkDeploymentTarget()

        // Get deployment bucket
        accountDetails = JSON.getValueFromPropertiesFile('accountDetails')
        println "Account Details: $accountDetails"
        def s3bucketvalue = getServerlessDeploymentBucketInfo(accountDetails)
        
        // Prepare serverless yml
        def serviceDomainStr = "${configData.INSTANCE_PREFIX}-${env.DOMAIN}--${env.SERVICE}"
        def owner = serviceConfig['owner']
        def appTag = serviceConfig["appTag"]
        def appId = serviceConfig["appId"]
        def region = JSON.getValueFromPropertiesFile("deploymentRegion")     
        def environmentLogicalId = env.ENVIRONMENT_LOGICAL_ID
        def environmentInfo = props['environmentInfo']

        JSON.setValueToPropertiesFile('repoBranch', environmentInfo['physical_id'])
        // Send rollback started event
        events.sendStartedEvent('CREATE_DEPLOYMENT', "Deployment rollback started", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.started))

        sh("sed -i -- 's/{deployment_bucket}/${s3bucketvalue}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{service_name}/${env.SERVICE}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{service_domain}/${serviceDomainStr}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{deployment_region}/${region}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{domain_name}/${env.DOMAIN}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{env_logical_id}/${env.ENVIRONMENT_LOGICAL_ID}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{service_owner}/${owner}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{appTag}/${appTag}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{appId}/${appId}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        sh("sed -i -- 's/{scm_hash}/${env.COMMIT_SHA}/g' ${PROPS.WORKING_DIRECTORY}/serverless.yml")
        
        
        // Getting temporary credentials (for cross account role if not primary account)
        def credsId = utilModule.generateCreds(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION)
        def deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, env.AWS_DEFAULT_REGION);
        
        // Run serverless rollback command
        sh("cd ${PROPS.WORKING_DIRECTORY};serverless rollback --timestamp ${env.TIMESTAMP} --stage ${env.ENVIRONMENT_LOGICAL_ID} --aws-profile ${deploymentAccountCreds} -v > ../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-rollback-result.log 2>&1;")
        println sh("cat ${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_serverless-rollback-result.log")
        resetProperties()
        events.sendCompletedEvent('UPDATE_DEPLOYMENT', "Deployment rollback completed", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.successful))

    } catch (ex) {
        println "Exception occured during rollback: ${ex.message}"
        resetProperties()
        events.sendFailureEvent('UPDATE_DEPLOYMENT', "Deployment rollback failed", environmentDeploymentMetadata.generateDeploymentMap(Status.DeploymentStatus.failed))
        throw new Exception("Exception occured during rollback: ", ex)
    }
}

def resetProperties() {
    def props = JSON.getAllProperties()
    if (props['rollback']) props.remove('rollback')
    if (props['rollback_metadata']) props.remove('rollback_metadata')
    JSON.setAllProperties(props)
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
            def deploymentRole
            deploymentRole = accountDetails.IAM.VPCSERVICES_ROLEID;

            JSON.setValueToPropertiesFile('deploymentRole', deploymentRole) 

            def roleId = deploymentRole.substring(deploymentRole.indexOf("::")+2, deploymentRole.lastIndexOf(":"))
            JSON.setValueToPropertiesFile('roleId', roleId)  
        } else {
            throw new Exception("Deployment account information is not valid for the environmentLogicalId: ${environmentLogicalId}, please set them by logging into Jazz UI.")
        }
    } else {
        throw new Exception("Deployment account information is not valid for the environmentLogicalId: ${environmentLogicalId}, please set them by logging into Jazz UI.")
    }
}
