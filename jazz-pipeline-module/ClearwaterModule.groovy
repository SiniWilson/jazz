#!groovy?

import groovy.json.JsonBuilder
import groovy.transform.Field
import java.net.URLEncoder
import java.util.Base64.Encoder
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import common.util.Props as PROPS

/*
* The Clearwater module utility functions
* @author: Saurav Dutta
* @version: 2.0
*/

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/**
 * Create a fork of the Clearwater repo and copy the artifacts
 *
 */
def createForkAndCopyFiles(forkRepoName, serviceName, domainName, CLEARWATER_PUBLISH_USER, CLEARWATER_PUBLISH_PASSWORD) {
    println "In ClearwaterModule.groovy:createForkAndCopyFiles"

    try {
        def configData = JSON.getValueFromPropertiesFile('configData')
        sh("git init")
        sh("git config --global user.email \"serverlessdev@t-mobile.com\"")
        sh("git config --global user.name $CLEARWATER_PUBLISH_USER")
        sh("git config http.sslVerify \"false\"")

        sh("rm -f ./ClearwaterRepoFork")
        
        def forkedRepoProjectKey = null
        def forkedRepoUrl = null
        def forkedRepo = null
        def forkedRepoObj = null
        def encodedStr = "$CLEARWATER_PUBLISH_USER:$CLEARWATER_PUBLISH_PASSWORD".bytes.encodeBase64().toString()
        def forkAPIUrl = "${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_BASE_URL}/rest/api/1.0/projects/${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_PROJECT_KEY}/repos/${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_NAME}"
        try {
            forkedRepo = sh("curl -X POST -k -v -H 'Content-Type:application/json' -H 'authorization: Basic $encodedStr' '${forkAPIUrl}' -d '{\"name\":\"$forkRepoName\"}'", true);
            if(forkedRepo) {
                forkedRepoObj = JSON.parseJson(forkedRepo)
                if(forkedRepoObj && forkedRepoObj.project && forkedRepoObj.project.key) {
                    forkedRepoProjectKey = forkedRepoObj.project.key
                    forkProjectKey = forkedRepoObj.project.key
                } else {
                    println "Forking failed. Got invalid response object from Bitbucket API"
                    throw new Exception("Forking failed. Got invalid response object from Bitbucket API")
                }
            } else {
                println "Forking failed. Got invalid response object from Bitbucket API"
                throw new Exception("Forking failed. Got invalid response object from Bitbucket API")
            }
        } catch (ex) {
            println "Forking Clearwater repo failed." + forkedRepo
            forkedRepoObj = JSON.parseJson(forkedRepo)
            if (forkedRepoObj.errors && forkedRepoObj.errors[0].message.startsWith("This repository URL is already taken")) {
                def userSlug = CLEARWATER_PUBLISH_USER.toLowerCase()
                forkedRepoProjectKey =  "~"+userSlug
                forkProjectKey = "~"+userSlug
            } else {
                println "Error occured while forking the Clearwater repo"
                throw new Exception("Error occured while forking the Clearwater repo", ex)
            }
        }

        forkedRepoUrl = "${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_BASE_URL}/scm/${forkedRepoProjectKey}/${forkRepoName}.git"
        def encodedPwd = URLEncoder.encode(CLEARWATER_PUBLISH_PASSWORD, "utf-8")
        forkedRepoUrl = forkedRepoUrl.replace("https://", "https://$CLEARWATER_PUBLISH_USER:$encodedPwd@")
        sh("rm -f ${forkRepoName}")
        /*
        * Cloning the repo inside the ClearwaterRepoFork directory
        */
        sh("git clone ${forkedRepoUrl} ClearwaterRepoFork")

        try {
            sh("ls -al ClearWaterArtifacts")
            if (JSON.isFileExists("ClearWaterArtifacts/${forkRepoName}.png")) {
                sh("mkdir -p ClearwaterRepoFork/${forkRepoName}/sequence_diagrams/platforms/jazz/${domainName}")
                sh("cp ClearWaterArtifacts/${forkRepoName}.png ClearwaterRepoFork/${forkRepoName}/sequence_diagrams/platforms/jazz/${domainName}/${serviceName}.png")
            }
            if (JSON.isFileExists("ClearWaterArtifacts/${forkRepoName}.json")) {
                sh("mkdir -p ClearwaterRepoFork/${forkRepoName}/swagger/platforms/jazz/${domainName}")
                sh("cp ClearWaterArtifacts/${forkRepoName}.json ClearwaterRepoFork/${forkRepoName}/swagger/platforms/jazz/${domainName}/${serviceName}.json")
            }
            sh("git --git-dir=ClearWaterArtifacts/.git --work-tree=ClearWaterArtifacts/ add .")
            def res = null
            try {
                sh("git --git-dir=ClearWaterArtifacts/.git --work-tree=ClearWaterArtifacts/ commit -m \"Commiting Clearwater Artifacts\" 2>&1 > commitout.txt")
                sh("git --git-dir=ClearWaterArtifacts/.git --work-tree=ClearWaterArtifacts/ push origin master")
                res = [status: true, message: "Success"]
            } catch(ex) {
                if (JSON.isFileExists("ClearwaterRepoFork/${forkRepoName}/commitout.txt")) {
                    def commitContent = JSON.readFile("ClearwaterRepoFork/${forkRepoName}/commitout.txt")
                    if (commitContent.contains("Your branch is up-to-date")) {
                        res = [status: false, message: "No new changes to your service assets. Your changes were pushed to Clearwater as part of a previous publish request"]
                    } else {
                        res = [status: false, message: commitContent]
                    }
                } else {
                    res = [status: false, message: "Could not get result of commit. " + ex.getMessage()]
                }
            }
            finally {
                sh("rm -rf ClearwaterRepoFork/${forkRepoName}/commitout.txt")
                JSON.setValueToPropertiesFile('clearwaterCommitResult', res)
                return res
            }
        } catch (ex) {
            println "Error in commiting files. " + ex.message
        }
    } catch (ex) {
        destroy()
        println "Could not fork Clearwater repo. " + ex.message
        throw new Exception("Could not create PR", ex)
    }
}

/**
 * Raise PR for merging clearwater artifacts
 *
 */
def raisePRForPublishing(forkRepoName, description, CLEARWATER_PUBLISH_USER, CLEARWATER_PUBLISH_PASSWORD) {
    println "In ClearwaterModule.groovy:raisePRForPublishing"

    def configData = JSON.getValueFromPropertiesFile('configData')
    def prLink = null
    def prBaseUrl = "${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_BASE_URL}/projects/${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_PROJECT_KEY}/repos/${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_NAME}/pull-requests/"
    def pullRequestAPIUrl = "${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_BASE_URL}/rest/api/1.0/projects/${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_PROJECT_KEY}/repos/${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_NAME}/pull-requests"
    def encodedStr = "$CLEARWATER_PUBLISH_USER:$CLEARWATER_PUBLISH_PASSWORD".bytes.encodeBase64().toString()
    def payload = "{\"title\":\"[Jazz] Merge request for service: ${forkRepoName}\",\"description\":\"${description}\",\"fromRef\":{\"id\":\"refs/heads/master\",\"repository\":{\"slug\":\"${forkRepoName}\",\"name\":null,\"project\":{\"key\":\"${forkProjectKey}\"}}},\"toRef\":{\"id\":\"refs/heads/master\",\"repository\":{\"slug\":\"${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_NAME}\",\"name\":null,\"project\":{\"key\":\"${configData.CODE_QUALITY.CLEARWATER.PUBLISH_REPO_PROJECT_KEY}\"}}}}"
    def pullRequestRes = null
    def pullRequestResObj = null
    try {
        pullRequestRes = sh("curl -X POST -k -v -H 'Content-Type:application/json' -H 'authorization: Basic $encodedStr' '${pullRequestAPIUrl}' -d '${payload}'", true);
        pullRequestResObj = JSON.parseJson(pullRequestRes)
        if(pullRequestResObj) {
            if(pullRequestResObj.id) {
                prLink = prBaseUrl+pullRequestResObj.id
            } else if(pullRequestResObj && pullRequestResObj.errors[0]) {
                prLink = prBaseUrl+pullRequestResObj.errors[0].existingPullRequest.id
            }
        }
    JSON.setValueToPropertiesFile('clearwaterPrLink', prLink)
    } catch (ex) {
        destroy()
        println "Could not create PR. Error:" + ex.message
        throw new Exception("Could not create PR", ex)
    }
    
    return prLink

}

/**
 * Get service Owner details for notifications
 *
 */
def getServiceOwnerInfo(serviceMeta) {
    println "In ClearwaterModule.groovy:getServiceOwnerInfo"
    println "service Metadata: $serviceMeta"
    def configData = JSON.getValueFromPropertiesFile('configData')
    def serviceOwnerInfo = [:]
    def getUserInfoObj = null;
    def req_url = "https://graph.microsoft.com/v1.0/users/${serviceMeta.users}"
    def props = JSON.getAllProperties()
    def authToken = props['authToken']

    try {
        def getUserInfo = sh("curl --location --request GET '$req_url' --header 'Authorization: Bearer ${authToken}'", true)
        getUserInfoObj = JSON.parseJson(getUserInfo)
        if (getUserInfoObj) {
            if(getUserInfoObj && getUserInfoObj.mail) {
                serviceOwnerInfo.name =  getUserInfoObj.givenName + " " + getUserInfoObj.surname
                serviceOwnerInfo.displayname = getUserInfoObj.givenName + " " + getUserInfoObj.surname
                serviceOwnerInfo.emailAddress = getUserInfoObj.mail
            } else {
                println "Error respone from AD service"
                throw new Exception("Error respone from AD service")
            }
            
        } else {
            println "Invalid respone from AD service"
            throw new Exception("Invalid respone from AD service")
        }
    } catch (ex) {
        println "Could not fetch user info for sending notifications" + ex.message
    }
    JSON.setValueToPropertiesFile('clearwaterServiceOwner', serviceOwnerInfo)
    return serviceOwnerInfo
}

/**
 * Send notfications with details of PR
 * The mails should be sent to the service owner. More emails/DLs can be added later
 */
def sendNotifications(serviceOwnerInfo, cClists, serviceMeta, commitResult) {
    println "In ClearwaterModule.groovy:sendNotifications"

    try {
        def configData = JSON.getValueFromPropertiesFile('configData')
        
        def owner = [
            'name' : configData.JAZZ.NOTIFICATIONS.APPROVER_NAME,
            'displayname' : configData.JAZZ.NOTIFICATIONS.APPROVER_DISP_NAME,
            'emailAddress' : configData.JAZZ.NOTIFICATIONS.APPROVER_EMAIL_ID,
            'fromAddress' : configData.JAZZ.NOTIFICATIONS.FROM_ADDRESS,
            'active' : true
        ]
            
        if(serviceOwnerInfo && serviceOwnerInfo.emailAddress) {
            owner.name = serviceOwnerInfo.name
            owner.displayname = serviceOwnerInfo.displayname
            owner.emailAddress = serviceOwnerInfo.emailAddress

            def body = generateEmailBody(serviceMeta, owner, commitResult)

            def sendMail = sh("curl -X POST -k -v -H 'Content-Type:application/json' '${configData.JAZZ.EMAIL_API_ENDPOINT}' -d '${body}'", true);
            def responseJSON = JSON.parseJson(sendMail)
            println "responseJSON: ${responseJSON}"
            if(responseJSON.data){
                println "successfully sent Clearwater notification to ${owner.name} at ${owner.emailAddress}"
            } else {
                println "Exception occurred while sending clearwater notification to service owner. Error: $responseJSON"
            }

        } else {
            println "No serviceOwnerInfo available for sending notifications"
            throw new Exception("No serviceOwnerInfo available for sending notifications")
        }


    } catch (ex) {
        println " Clearwater notification failed: " + ex.message
    }

}

def generatePNGFromPUML(serviceName){
    println "In ClearwaterModule.groovy:generatePNGFromPUML"

    def clearwaterArtifactsFolder = "flows"
    /*
    * check if .puml available under /flows/{domain}_{service}.puml
    */
    sh("rm -f ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/*.png")
    sh("rm -f ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/*.jar*")
    /*
    * Need to verify once build pack api is implemented
    */
    if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/${serviceName}.puml")) {
        try {
            println "File exists.. ${serviceName}.puml"
            sh("wget -q -P ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/ https://sourceforge.net/projects/plantuml/files/plantuml.jar")
            sh("java -jar ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/plantuml.jar ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/${serviceName}.puml")
            sh("ls -al ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}")
        } finally {
            sh("rm -f ${PROPS.WORKING_DIRECTORY}/${clearwaterArtifactsFolder}/plantuml.jar")
        }
    }
}

/**
  * Method to generate email body
**/
def generateEmailBody(serviceMeta, owner, commitResult) {
    println "In ClearwaterModule.groovy:generateEmailBody"

    def configData = JSON.getValueFromPropertiesFile('configData')
    def details = null
    def message = null
    def subject = null
    if (commitResult.status == true) {
       subject = "PR created for publishing assets to Clearwater"
       message = "Created a Pull Request to publish your assets to Clearwater repository for the following service: <br/> Service: <b>" + serviceMeta.service + "</b> <br/>Namespace: <b>" + serviceMeta.domain + "</b>"
       details = "Pull Request Url: <a href=\"${serviceMeta.pRLink}\">${serviceMeta.pRLink}</a>"
    } else {
       subject = "Failed while creating PR for publishing assets to Clearwater"
       message = "Failed while creating a Pull Request to publish your assets to Clearwater repository for the following service: <br/> Service: <b>" + serviceMeta.service + "</b> <br/>Namespace: <b>" + serviceMeta.domain + "</b> <br/>Failure Reason: <b>" + commitResult.message + "</b>"
       details = null
    }

    def body = JSON.objectToJsonString([
        from: owner.fromAddress,
        to: [[
            emailID: owner.emailAddress,
            name: [
                first: owner.displayname,
                last: ""
            ],
            heading: "Jazz Clearwater Notification",
            message: message,
            details: details,
        ]],
        subject: subject,
        templateDirUrl: "https://s3-${configData.AWS.REGION}.amazonaws.com/asgc-email-templates/clearwaterv1/"
    ])
    println "body: $body"
    return body
}

/**
 * The module destructor function
 * Clean up all created resources and configurations
 */
def destroy() {
    println "In ClearwaterModule.groovy:destroy"

    sh("rm -f ./ClearWaterArtifacts")
    sh("rm -f ./ClearwaterRepoFork")
}
