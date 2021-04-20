import common.util.Json as JSON
import static common.util.Shell.sh as sh
import common.util.Props as PROPS
import java.lang.*


static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/**
 * Generate a unique name for S3 bucket for deploying website
 *
 */
def generateBucketNameForService(domain, service) {
    println "In UtilityModule.groovy:generateBucketNameForService"
    def bucketName
    def hashVal
    if(service) {
        bucketName = service
        if(domain) {
            bucketName = domain + "-" + bucketName
        }
        try {
            def randVal = sh("openssl rand -hex 4", true).trim()
            if(randVal && randVal.length() == 8) {
                hashVal = randVal
            } else {
                println "OpenSSL failed to generate a valid hash"
                throw new Exception("OpenSSL failed to generate a valid hash")
            }
        } catch(ex) {
            hashVal = sh("echo \${RANDOM}", true)
        }
    }
    if(hashVal) {
        bucketName = bucketName+"-"+hashVal
    }
    if(bucketName) {
        println "Bucket name  - ${bucketName.toLowerCase()}"
        return bucketName.toLowerCase()		
    } else {
        println "Could not generate bucket name for service"
        return null
    }
}

/**
 * isS3BucketAccessibleByTheCurrentAccountCredential
 *
 */
def isS3BucketAccessibleByTheCurrentAccountCredential(s3BucketName, credsId) {
    println "UtilityModule.groovy: isS3BucketAccessibleByTheCurrentAccountCredential"
    def status = true;
    def successCode = 0
    println "s3BucketName: $s3BucketName"
    println "credsId: $credsId"
    try {
        def output = sh("aws s3api wait bucket-exists --bucket ${s3BucketName} --profile ${credsId}; echo \$?")?.trim()
        println "isS3BucketAccessibleByTheCurrentAccountCredential-- Output: `$output`"
        println "output class: `${output.getClass()}`"
        if (!output.equals(successCode.toString())) {
            println "inside output if--"
            status = false
        } 
    } catch(ex) {
        println "Error while checking bucket exists or not: ${ex.message}" 
        status = false
    }
    println "status in bucket exists: $status"
    return status
}

/**
 * Check if bucket exists
 *
 */
def checkIfBucketExists(s3BucketName, credsId) {
    println "UtilityModule.groovy:checkIfBucketExists"
    def status = true;
    try {
        output = sh("aws s3 ls s3://$s3BucketName --profile ${credsId}")
    } catch(ex) {
        println "Error while listing bucket contents - bucket not created yet or we don't have access?: ${ex.message}" 
        status = false
    }
    return status
}

/*
 * Archive pipeline trigger  
 */
def triggerArchiveWorkflow(serviceName, domain, environmentId, archivedEnvId) {
	println "UtilityModule.groovy:triggerArchiveWorkflow"

    try {
        def props = JSON.getAllProperties() 
        def configData = props['configData']
        def env = System.getenv()
        // TO DO
        // def ref = configData.ARCHIVE_ENV.GITLAB_BRANCH
        // def url = configData.ARCHIVE_ENV.GITLAB_API
        def ref = "master"
        def url = "https://gitlab.com/api/v4/projects/19469943/pipeline?ref=${ref}"

        def request = "curl --request POST --header \"PRIVATE-TOKEN:${env.GITLAB_SVC_ACCT_PASSWORD}\" \
        --header \"Content-Type: application/json\" \
        --data '{ \"variables\": [ { \"key\": \"SERVICE_NAME\", \"value\": \"${serviceName}\" }, { \"key\": \"DOMAIN\", \"value\": \"${domain}\" }, { \"key\": \"ENVIRONMENT_ID\", \"value\": \"${environmentId}\" }, { \"key\": \"ARCHIVED_ENVIRONMENT_ID\", \"value\": \"${archivedEnvId}\" }] }' \
        \"${url}\" "
        println "trigger archive request: $request" 
        sh("${request}")
    } catch (ex) {
        println "Failed to archive the old environment" + ex.getMessage()
        throw new Exception("Failed to archive the old environment", ex)
    }
}



 /**
 * Get Request Id 
 * @return   
 */
def generateRequestId() {
    println "In UtilityModule.groovy:generateRequestId"
    UUID uuid = UUID.randomUUID()
    return uuid.toString() 
}

/*
* Get the primary account
*/
def getAccountInfoPrimary(configData){
    println "In UtilityModule.groovy:getAccountInfoPrimary"
    
    def dataObjPrimary = {};
    for (item in configData.AWS.ACCOUNTS) {
        if(item.PRIMARY){
            dataObjPrimary = item
        }
    }
    return dataObjPrimary;
}

/**
*  Get Account Specific S3
*/

def getAccountBucketName() {
    println "In UtilityModule.groovy:getAccountBucketName"
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    println "service config: $serviceConfig"
    def s3Object = {}
    def accountObject = getAccountInfo();
    if( accountObject.size() > 0){
        def regions = accountObject['REGIONS'];
        for (region in regions ){
            if( region['REGION'] == serviceConfig.region) { 
                s3Object = region['S3'];
            }
        }
    }
    println "Bucket name: $s3Object"
    return s3Object;
}

/*
* Get the required account
*/
def getAccountInfo(){
    println "In UtilityModule.groovy:getAccountInfo"
    def configData = JSON.getValueFromPropertiesFile('configData')
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def dataObj = {};
    for (item in configData.AWS.ACCOUNTS) {
        if(item.ACCOUNTID == serviceConfig.account){
            dataObj = item
        }
    }
    return dataObj;
}

/*
* Get region from account details
*/
def getRegionValue(config, accountDetails){
    println "In UtilityModule.groovy:getRegionValue"
    def regionsArray = accountDetails['REGIONS'];
    for (region in regionsArray ){
        if( region['REGION'] == config.region) { 
            return region['REGION']
        }
    }
}

/*
* API Gateway by Regions
*/

def getAPIGatewaybyRegion(region) {
    println "In UtilityModule.groovy:getAPIGatewaybyRegion"
    def accountInfo = JSON.getValueFromPropertiesFile('accountDetails')
    def apigateway =null
    //get regions
    def allregions = accountInfo.REGIONS
    for (regionArray in allregions ){
        if(regionArray.REGION == region ){
                apigateway = regionArray.API_GATEWAY
        }
    }
    println "getAPIGatewaybyRegion: $apigateway"
    return apigateway
}
/*
* Function to generate Creds based on account
* @param accountInfo
* @param region
* @param AWS_KEY_PRIMARY
* @param AWS_SECRET_PRIMARY
* @param AWS_REGION_PRIMARY
*/
def generateCreds(AWS_KEY_PRIMARY, AWS_SECRET_PRIMARY, AWS_REGION_PRIMARY) {
    println "UtilityModule.groovy:generateCreds"
    def credsId = null
    def accountInfo = getAccountInfo()
    def region = JSON.getValueFromPropertiesFile('deploymentRegion')
    if (!region) {
        // if there is no deployed region, then use default region
        region = AWS_REGION_PRIMARY
    }
    credsId = configureAWSProfile(AWS_KEY_PRIMARY, AWS_SECRET_PRIMARY, region)
    return credsId
}

/*
* Function to configure an AWS profile and return the profile name
*/
def configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION=null, AWS_SESSION_ID=null) {
    println "In UtilityModule.groovy:configureAWSProfile"
    
    try {
        def awsProfile = UUID.randomUUID().toString();
        sh("aws configure set profile.${awsProfile}.aws_access_key_id ${AWS_KEY}",false,false)
        sh("aws configure set profile.${awsProfile}.aws_secret_access_key ${AWS_SECRET}",false,false)
        if (AWS_REGION) {
            sh("aws configure set profile.${awsProfile}.region ${AWS_REGION}",false,false)
        }
        if (AWS_SESSION_ID) {
            sh("aws configure set profile.${awsProfile}.aws_session_token ${AWS_SESSION_ID}",false,false)
        }
        return awsProfile
    } catch(ex) {
        println "Something went wrong while configuring aws profile, error: " + ex
        throw new Exception("Something went wrong while configuring aws profile", ex)
    }
}

/*
* Function to assume a cross account role and retrieve temporary AWS IAM credentials
*/
def assumeCrossAccountRole(credsId, AWS_REGION_PRIMARY) {
    println "In UtilityModule.groovy:assumeCrossAccountRole"

    try {
        def awsProfile = UUID.randomUUID().toString();
        def accountInfo = getAccountInfo();
        def roleArn = accountInfo.IAM.PLATFORMSERVICES_ROLEID;
        def region = JSON.getValueFromPropertiesFile('deploymentRegion')
        if (!region) {
            // if there is no deployed region, then use default region
            region = AWS_REGION_PRIMARY
        }
        if(accountInfo.PRIMARY){
            return credsId
        } else {
            def assumeRoleParams = sh("aws sts assume-role --role-arn ${roleArn} --role-session-name ${awsProfile} --profile ${credsId}")
            assumeRoleParams = JSON.parseJson(assumeRoleParams)
            def deploymentCredId = configureAWSProfile(assumeRoleParams.Credentials.AccessKeyId, assumeRoleParams.Credentials.SecretAccessKey, region, assumeRoleParams.Credentials.SessionToken);
            return deploymentCredId;
        }
    } catch(ex) {
        println "Something went wrong while configuring cross account role, error: " + ex
        throw new Exception("Something went wrong while configuring cross account role", ex)
    }
}

/*
* Function to retrieve secrets from AWS Secrets Manager service using secretId (secret name) and an AWS profile
*/
def getSecret(awsProfile, secretId){
    println "In UtilityModule.groovy:getSecret"
    
    try {
        def secretsData = sh("aws secretsmanager get-secret-value --secret-id ${secretId} --profile ${awsProfile}")
        secretsData = JSON.parseJson(JSON.parseJson(secretsData).SecretString)
        /*
        * returns the jsor pair of the secret
        */
        return secretsData
    } catch(ex) {
        println "Something went wrong while retrieving secrets from AWS Secrets Manager: " + ex.message
        throw new Exception("Something went wrong while retrieving secrets from secret store", ex)
    }
}

/*
* Function to retrieve secrets from AWS Secrets Manager service using secretId (secret name) and AWS credentials
*/
def getSecret(AWS_KEY, AWS_SECRET, AWS_REGION, secretId){
    println "In UtilityModule.groovy:getSecret"
    
    try {
        def awsProfile = configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
        def secretsData = getSecret(awsProfile, secretId)
        resetAWSProfile(awsProfile)
        /*
        * returns the jsor pair of the secret
        */
        return secretsData
    } catch(ex) {
        println "Something went wrong while retrieving secrets " + ex.message
        throw new Exception("Something went wrong while retrieving secrets ", ex)
    }
}


/*
* Function to reset an AWS profile
*/
def resetAWSProfile(awsProfile) {
    println "In UtilityModule.groovy:resetAWSProfile"
    println "resetting AWS profile: " + awsProfile

    try {
        sh("aws configure set profile.${awsProfile}.aws_access_key_id XXXXXXXXXXXXXXXXXXXXXXXXXX")
        sh("aws configure set profile.${awsProfile}.aws_secret_access_key XXXXXXXXXXXXXXXXXXXXXX")
    } catch(ex) {
        println "Something went wrong while reseting aws profile: " + ex.message
        throw new Exception("Something went wrong while reseting aws profile", ex)
    }
}

/*
* Function to print env variables of archival pipeline
*/
def showArchiveEnvParams() {
    println "In UtilityModule.groovy:showArchiveEnvParams"

    def env = System.getenv()
    
    println "======================================"
    println "========= BUILD PARAMETERS ==========="
    println "SERVICE_NAME: $env.SERVICE_NAME"
    println "DOMAIN: $env.DOMAIN"
    println "ENVIRONMENT_ID: $env.ENVIRONMENT_ID"
    println "ARCHIVED_ENVIRONMENT_ID: $env.ARCHIVED_ENVIRONMENT_ID"
    println "======================================"
}

/*
* Function to print env variables of delete pipeline
*/
def showDeleteEnvParams() {
    println "In UtilityModule.groovy:showDeleteEnvParams"

    def env = System.getenv()
    
    println "======================================"
    println "========= BUILD PARAMETERS ==========="
    println "SERVICE_NAME: $env.SERVICE_NAME"
    println "DOMAIN: $env.DOMAIN"
    println "ENVIRONMENT_ID: $env.ENVIRONMENT_ID"
    println "REQUEST_ID: $env.REQUEST_ID"
    println "USER: $env.USER"
    println "======================================"
}

/*
* Function to print env variables of dns pipeline
*/
def showDnsEnvParams() {
    println "In UtilityModule.groovy:showDnsEnvParams"

    def env = System.getenv()
    
    println "======================================"
    println "========= BUILD PARAMETERS ==========="
    println "FQDN: $env.FQDN"
    println "ENVIRONMENT: $env.ENVIRONMENT"
    println "ENDPOINT: $env.ENDPOINT"
    println "ENDPOINT_TYPE: $env.ENDPOINT_TYPE"
    println "SERVICE_ID: $env.SERVICE_ID"
    println "REQUEST_ID: $env.REQUEST_ID"
    println "UPDATE_DNS: $env.UPDATE_DNS"
    println "======================================"
}

/*
* Function to print env variables of service pipeline
*/
def showServiceEnvParams() {
    println "In UtilityModule.groovy:showServiceEnvParams"

    def env = System.getenv()
    
    println "======================================"
    println "========= BUILD PARAMETERS ==========="
    println "REPO_URL: $env.REPO_URL"
    println "REQUEST_ID: $env.REQUEST_ID"
    println "REPO_BRANCH: $env.REPO_BRANCH"
    println "REPO_NAME: $env.REPO_NAME"
    println "COMMIT_SHA: $env.COMMIT_SHA"
    println "======================================"
}

/*
* Function to notify approver for production deployment for all the service pipelines
*
*/
def notifyApprover(id, timeOutMins, serviceConfig, configData){
    println "In UtilityModule.groovy:notifyApprover"

    try {
        def approver = [
            'name' : configData.JAZZ.NOTIFICATIONS.APPROVER_NAME,
            'displayname' : configData.JAZZ.NOTIFICATIONS.APPROVER_DISP_NAME,
            'emailAddress' : configData.JAZZ.NOTIFICATIONS.APPROVER_EMAIL_ID,
            'fromAddress' : configData.JAZZ.NOTIFICATIONS.FROM_ADDRESS,
            'active' : true
        ]
        def approverList;
        if (serviceConfig.approvers){
            approverList = serviceConfig.approvers
        }
        def approvalUrl = "https://${configData.JAZZ.JAZZ_HOME_BASE_URL}/services"
        def expTime = sh("TZ=America/Los_Angeles date --date=\"$timeOutMins minutes\"", true)
        def approvers = []
        def authToken = JSON.getValueFromPropertiesFile("authToken");

        if (approverList && approverList instanceof List && approverList.size() > 0) {
            for (def eachApprover in approverList) {
                try {
                    def approverDetails
                    approverDetails = sh("curl --location --request GET 'https://graph.microsoft.com/v1.0/users/$eachApprover' --header 'Authorization: Bearer ${authToken}'" , true)
                    def approverDetailsJson = JSON.parseJson(approverDetails)
                    if (approverDetailsJson && (approverDetailsJson.surname || approverDetailsJson.givenName) && approverDetailsJson.mail) {
                        def user = [
                            'first_name': approverDetailsJson.givenName,
                            'last_name': approverDetailsJson.surname,
                            'email': approverDetailsJson.mail
                        ]

                        approvers << user
                    }
                } catch(ex) {
                    println "Error while fetching approver details. : ${ex.message}"
                }

            }
        }

        if (approvers.size() == 0) {
            def user = [
                'first_name': approver.name,
                'last_name': '',
                'email': approver.emailAddress
            ]

            approvers << user
        }

        def env = System.getenv();
        def changes = sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};git log -1 --pretty=%B").trim().replaceAll("\"","&quote;")

        for(def svcAdmin in approvers){
            def body = JSON.objectToJsonString([
                from: approver.fromAddress,
                to: [[
                    emailID: svcAdmin.email,
                    name: [
                            first: svcAdmin.first_name,
                            last: svcAdmin.last_name
                            ],
                    heading: "Deployment Action Needed",
                    message: "The following service is pending approval for production deployment. <br/> Service: <b>${serviceConfig['service']}</b> <br/>Domain: <b>${serviceConfig['domain']}</b>",
                    details: "Changes that are part of this deployment:<br/>" + changes,
                    linkexpire: "Your link expires at " + expTime,
                    link: [
                            [
                                text: "Approve",
                                url: approvalUrl  + "?action=proceed&id=${id}&serviceId=${serviceConfig['id']}&serviceName=${serviceConfig['service']}&domain=${serviceConfig['domain']}&changes=${changes}"
                            ],
                            [
                                text: "Reject",
                                url: approvalUrl  + "?action=reject&id=${id}&serviceId=${serviceConfig['id']}&serviceName=${serviceConfig['service']}&domain=${serviceConfig['domain']}&changes=${changes}"
                            ]
                        ]
                    ]],
                subject: "Approve Production Deployment for ${serviceConfig['service']}" ,
                templateDirUrl: "https://s3-${configData.AWS.REGION}.amazonaws.com/asgc-email-templates/approvalv1/",
                id: id
            ])
            def sendMail = sh("set +x; curl -k -v -H 'Content-type: application/json' -d '$body' https://${configData.AWS.API.HOST_NAMES.PROD}/api/platform/send-email; set -x", true )
            def responseJSON = JSON.parseJson(sendMail);
            if(responseJSON.data){
                println "successfully sent e-mail to $svcAdmin.first_name $svcAdmin.last_name at $svcAdmin.email"
            } else {
                println "exception occured while sending e-mail: $responseJSON"
            }
        }

    } catch (ex){
        println "Exception occured: ${ex.message}"  
        throw new Exception("Exception occured while notifying approver", ex)
    }
}

/*
* Function to get deployment ID for production deployment for all the service pipelines
*/
def getDeploymentId(approvalTimeout, serviceConfig, deploymentJobName){
    try {
        //Invoking api to get deploymentid of the build
        println "In UtilityModule.groovy:getDeploymentId"
        def env = System.getenv()
        def props = JSON.getAllProperties()
        def serviceId = serviceConfig["id"]
        def approvers = serviceConfig["approvers"]
        def service = serviceConfig["service"]
        def domain = serviceConfig["domain"]
        def env_info = JSON.getValueFromPropertiesFile('environmentInfo')
        def accountInfo = '';
        def regionInfo = '';
        def requested_by = '';
        def generateId;
        if(env_info['deployment_accounts']) {
            accountInfo = env_info.deployment_accounts.account
            regionInfo = env_info.deployment_accounts.region
        }
        def commitMessage = ""
        def gitlabApi = "https://gitlab.com/api/v4/projects/${env.CI_PROJECT_ID}/pipelines/"
        def approvalLink = "${gitlabApi}${env.CI_PIPELINE_ID}/jobs"
        def authToken = JSON.getValueFromPropertiesFile("authToken");
        println "approvallink: $approvalLink"
        def approvalApi = "${env.API_BASE_URL}/approvals";
        
        if(deploymentJobName !== 'create-certificate') {
            requested_by = sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};git show -s --format='%ae' ${props.commitSha}" ).trim()
            println "url: curl -k -H 'Content-type: application/json' -H 'Authorization: ${authToken}' -d '{\"approval_link\":\"${approvalLink}\", \"approval_timeout\":\"${approvalTimeout}\", \"deployment_job_name\":\"${deploymentJobName}\", \"service_id\":\"${serviceId}\", \"approvers\":\"${approvers}\", \"accountInfo\":\"${accountInfo}\", \"regionInfo\":\"${regionInfo}\", \"commitMessage\":\"${commitMessage}\", \"service\":\"${service}\", \"domain\":\"${domain}\", \"requested_by\":\"${requested_by}\"}' $approvalApi "
            generateId = sh("curl -k -H 'Content-type: application/json' -H 'Authorization: ${authToken}' -d '{\"approval_link\":\"${approvalLink}\", \"approval_timeout\":\"${approvalTimeout}\", \"deployment_job_name\":\"${deploymentJobName}\", \"service_id\":\"${serviceId}\", \"approvers\":\"${approvers}\", \"accountInfo\":\"${accountInfo}\", \"regionInfo\":\"${regionInfo}\", \"commitMessage\":\"${commitMessage}\", \"service\":\"${service}\", \"domain\":\"${domain}\", \"requested_by\":\"${requested_by}\"}' $approvalApi " , true )
        } else {
            println "url: curl -k -H 'Content-type: application/json' -H 'Authorization: ${authToken}' -d '{\"approval_link\":\"${approvalLink}\", \"approval_timeout\":\"${approvalTimeout}\", \"deployment_job_name\":\"${deploymentJobName}\", \"service_id\":\"${serviceId}\"}' $approvalApi "
            generateId = sh("curl -k -H 'Content-type: application/json' -H 'Authorization: ${authToken}' -d '{\"approval_link\":\"${approvalLink}\", \"approval_timeout\":\"${approvalTimeout}\", \"deployment_job_name\":\"${deploymentJobName}\", \"service_id\":\"${serviceId}\"}' $approvalApi " , true )
        }

        println "getDeploymentId - generateId:- $generateId"
        def resultJson = JSON.parseJson(generateId)
        def deploymentId = null
        if(resultJson == null || resultJson.data == null || resultJson.data.deployment_id == null) {
            throw new Exception("Unable to generate deployment id")
        } else {
            deploymentId = resultJson.data.deployment_id
            resultJson = null
        }
        return deploymentId

    } catch (ex) {
        println "getDeploymentId - failed"   
        throw new Exception("Failed to get the deployment id" , ex)
    }
}

def approvalStatusUpdate(triggerPipeline) {
    try {
        //Invoking api to update deployment approval status

        println "In UtilityModule.groovy:approvalStatusUpdate"
        def env = System.getenv()
        def deploymentId = JSON.getValueFromPropertiesFile("approvalDeploymentId");
        def authToken = JSON.getValueFromPropertiesFile("authToken");
        def action = "reject"
        def status = "error"
        def approvalApi = "${env.API_BASE_URL}/approvals/${deploymentId}";
        def response = sh("curl -X GET -k -v -H 'Content-type: application/json' -H 'Authorization: ${authToken}' $approvalApi " , true )
        response = JSON.parseJson(response)
        println "response:- $response"
        if(response && response.data && response.data.build_status) {
            status = response.data.build_status
        } else {
            def updateResponse = sh("curl -X PUT -k -v -H 'Content-type: application/json' -H 'Authorization: ${authToken}' -d '{\"action\":\"${action}\", \"trigger_pipeline\":${triggerPipeline}}' $approvalApi " , true )
            updateResponse = JSON.parseJson(updateResponse);
            println "approvalUpdate response:- $updateResponse"
            if(updateResponse && updateResponse.data && updateResponse.data.status) {
                status = updateResponse.data.status
            }
        }
        return status
    } catch (ex) {
        println "approvalStatusUpdate - failed"   
        throw new Exception("Failed to update approval status" , ex)
    }
}

/*
Check serverless deploy log output contains failed stack status or not
*/
def checkCfFailed(deployOutput) {
    println "In UtilityModule.groovy:checkCfFailedStatus"

    ctStackErrItems = ["ROLLBACK_COMPLETE", "ROLLBACK_FAILED", "UPDATE_ROLLBACK_FAILED", "DELETE_FAILED", "CREATE_FAILED"]
    boolean found = false;
    for (String item : ctStackErrItems) {
        if (deployOutput.contains(item + " state and can not be updated")) {
            found = true;
            break;
        }
    }
    return found;
}

/*
Check cloudformation stack resources are failed or not
*/
def checkCfResourceFailed(credsId) {
    try {
        def props = JSON.getAllProperties()
        def environmentLogicalId = props['environmentLogicalId']
        def serviceConfig = props['serviceConfig']
        def accountDetails = props['accountDetails']
        def configData = props['configData']

        def stackResources = null
        boolean found = false;
        def cfstack = "${serviceConfig['service']}--${serviceConfig['domain']}-${environmentLogicalId}"
        if(serviceConfig['type'] == "sls-app") {
            cfstack = "${configData.INSTANCE_PREFIX}-${serviceConfig['domain']}--${serviceConfig['service']}-${environmentLogicalId}"        
        } 
        commandDesc = "aws cloudformation describe-stacks --stack-name $cfstack --region ${serviceConfig['region']} --profile ${credsId}"
        output = sh("$commandDesc", true)
        def result = JSON.parseJson(output)
        cfStackErrItems = ["ROLLBACK_COMPLETE", "ROLLBACK_FAILED", "UPDATE_ROLLBACK_FAILED", "DELETE_FAILED", "CREATE_FAILED"]
        for (item in result['Stacks']) {
            println item['StackStatus']
            if (cfStackErrItems.contains(item['StackStatus'])) {
                found = true;
                break;
            }
        }
        return found;
    } catch (ex){
        println "checkCfResourceFailed - failed"   
        throw new Exception("Failed to checkCfResourceFailed" , ex)
    }
}
/*
Delete cloudformation stack resources when serverless removal fails
*/
def deletecfstack(credsId, cfstack, region) {
    try {
        commandListStack = "aws cloudformation list-stack-resources --stack-name $cfstack --region $region --profile ${credsId}"
        try {
            existingCF = sh("$commandListStack", true)
            println "Existing $existingCF"
            commandDelStack = "aws cloudformation delete-stack --stack-name $cfstack --region $region --profile ${credsId}"
            sh("$commandDelStack", true)
        } catch(e) {
            println "No stack found to delete"
        }
       
    } catch (ex){
        println "deletecfstack - failed"   
        throw new Exception("Failed to delete stack" , ex)
    }
}

/*
* Function to check for user pipeline
*/
def checkIfUserPipeline() {
    println "In UtilityModule.groovy:checkIfUserPipeline"

    def environmentDeploymentMetadata = new EnvironmentDeploymentMetadataLoader();
    def props = JSON.getAllProperties();
    def serviceConfig = props['serviceConfig'];
    def env = System.getenv();

    try {
        def environmentInfo = environmentDeploymentMetadata.getEnvironmentInfo();
        def user_pipeline = environmentInfo.user_pipeline;
        println "user_pipeline: ${user_pipeline}"
        if(!user_pipeline || !serviceConfig.userPipeline) {
            /*
            * Check for .gitlab.yml in user repository
            */
            def fExists = JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/.gitlab-ci.yml")
            println "fExists: ${fExists}"
            if(fExists) {
                environmentDeploymentMetadata.updateEnvironmentUserPipelineFlag(true);
            }
        } 
        /*
        * Checking if the event is coming from webhook
        */
        if(env.REQUEST_ID && (user_pipeline instanceof Boolean && user_pipeline)) {
            throw new Exception("For user pipelines, commit from webhook should be ignored, hence failing deployment!")
        }

    } catch(ex) {
        println "Exception occured while checking the user repository: + $ex"
        throw new Exception("Exception occured while checking the user repository", ex)
    }
}
