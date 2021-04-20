#!groovy?
import java.net.URLEncoder

import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*


/*
* Module that handles managing repos (create, delete) in the user's preferred scm
*/

/*
* ScmModule.groovy
* @author: Sini Wilson
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

//Creating a project in gitlab
def createProject(gitCredsToken) {
    println "ScmModule.groovy:createProject"
    try {
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def configData = JSON.getValueFromPropertiesFile('configData')
        def repoName = "${serviceConfig.domain}_${serviceConfig.service}"
        
        //gitlabs username is restricted to alphanumeric and . _ - characters,
        // so using email all email characters (except -, _) replaced with -
        def gitlabUsername = serviceConfig.created_by // .replaceAll("[^a-zA-Z0-9_-]", "-")
        def user_id = getGitlabUserId(configData, gitlabUsername, gitCredsToken)
        def userServicesGroupId = getUserServicesGroupId(configData, gitCredsToken)
        def gitlabRepoOutput = sh("curl --header \"PRIVATE-TOKEN: ${gitCredsToken}\" -X POST \"https://${configData.REPOSITORY.BASE_URL}/api/v4/projects?name=$repoName&path=$repoName&visibility=private&request_access_enabled=true\"", true)
        def repoDetails = JSON.parseJson(gitlabRepoOutput)
        
        if (repoDetails == null || repoDetails.equals("") || repoDetails.id == null || repoDetails.id.equals("")) {
            println "project creation failed in gitlab" 
            throw new Exception("project creation failed in gitlab") 
        }

        repoId = repoDetails.id
        JSON.setValueToPropertiesFile('repoId', repoId)
        transferProject(userServicesGroupId, repoId, gitCredsToken, configData)
        println "project created successfully!"
    } catch (ex) {
        println "project creation failed in gitlab - " + ex
        throw new Exception("project creation failed in gitlab", ex)
    }
}

//To get gitlab user id
def getGitlabUserId(configData, gitlabUsername, gitCredsToken){
    println "ScmModule.groovy:getGitlabUserId"
    try {
        def output = sh( "curl --header \"PRIVATE-TOKEN: ${gitCredsToken}\" -X GET \"https://${configData.REPOSITORY.BASE_URL}/api/v4/search?scope=users&search=$gitlabUsername\"", true)
        def userObject = JSON.jsonParse(output)

        if (userObject == null || userObject.equals("") || userObject[0] == null || userObject[0].equals("")
            || userObject[0].id == null || userObject[0].id.equals("")) {
            println "get user data in gitlab failed"
            throw new Exception("get user data in gitlab failed")
        }
        return userObject[0].id        
    } catch (ex) {
      println "getGitlabUserId failed: " + ex
      throw new Exception("getGitlabUserId faileds", ex)
    }
}

//To get user services group id
def getUserServicesGroupId(configData, gitCredsToken){
    println "ScmModule.groovy:getUserServicesGroupId"
    try {
        def encodedPath = URLEncoder.encode(configData.REPOSITORY.REPO_BASE_SERVICES, "utf-8")
        def output = sh( "curl --header \"PRIVATE-TOKEN: ${gitCredsToken}\" -X GET \"https://${configData.REPOSITORY.BASE_URL}/api/v4/groups/${encodedPath}\"", true)
        def groupObject = JSON.jsonParse(output)

        if (groupObject == null || groupObject.equals("") || groupObject.id == null || groupObject.id.equals("")) {
            println "Unable to find the user services group id "
            throw new Exception("Unable to find the user services group id ")
        }
        return groupObject.id        
    } catch (ex) {
      println "getUserServicesGroupId failed: " + ex
      throw new Exception("getUserServicesGroupId failed", ex)
    }
}

//To get gitlabs project id
def getGitLabsProjectId(repoName, configData, gitCredsToken) {
    println "ScmModule.groovy:getGitLabsProjectId"
    try {
        def encodedPath = URLEncoder.encode(configData.REPOSITORY.REPO_BASE_SERVICES, "utf-8")
        def output = sh("curl --header \"PRIVATE-TOKEN: ${gitCredsToken}\" -X GET \"https://${configData.REPOSITORY.BASE_URL}/api/v4/groups/${encodedPath}/projects?search=${repoName}\"", true )
        def projectObject = JSON.jsonParse(output)

        if (projectObject == null || projectObject.equals("") || projectObject[0] == null || projectObject[0].equals("")
            || projectObject[0].id == null || projectObject[0].id.equals("")) {
            println "getGitLabsProjectId failed to find project with name $repoName"
            throw new Exception("getGitLabsProjectId failed to find project with name $repoName")
        }
        println "getGitLabsProjectId - completed successfully!"
        return projectObject[0].id
        
    } catch (ex) {
      println "getGitLabsProjectId failed: " + ex 
      throw new Exception("getGitLabsProjectId failed", ex)
    }
}

//To transfer project
def transferProject(casId, projectId, gitCredsToken, configData){
    println "ScmModule.groovy: transferProject"
    try {
        sh ("curl --header \"PRIVATE-TOKEN: ${gitCredsToken}\" -X PUT \"https://${configData.REPOSITORY.BASE_URL}/api/v4/projects/$projectId/transfer?namespace=$casId\"" )
        println "transferProject completed successfully."
    } catch (ex) {
        println "transferProject failed: " + ex
        throw new Exception("transferProject failed", ex)
    }
}

//To set branch permission of the repo
def setBranchPermissions(gitCredsToken) {
    println "ScmModule.groovy:setBranchPermissions"
    try {        
        def configData = JSON.getValueFromPropertiesFile('configData')
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def repoName = "${serviceConfig.domain}_${serviceConfig.service}"

        def projectId = getGitLabsProjectId(repoName, configData, gitCredsToken)
        println "setBranchPermissions- projectId- $projectId"
        def resp = sh ("curl --request DELETE --header \"PRIVATE-TOKEN: ${gitCredsToken}\" \"https://${configData.REPOSITORY.BASE_URL}/api/v4/projects/$projectId/protected_branches/master\"", true)
        def permissionResp = sh ("curl --request POST --header \"PRIVATE-TOKEN: ${gitCredsToken}\" \"https://${configData.REPOSITORY.BASE_URL}/api/v4/projects/$projectId/protected_branches?name=master&push_access_level=0&merge_access_level=30\"", true)
        println "Setting branch permission completed successfully."
    } catch (ex) {
        println "set branch permissions failed: " + ex.message
        throw new Exception("set branch permissions failed", ex)
    }
}

//Adding webhook to the repo
def addWebhook(gitCredsToken, webhookUrl) {
    println "ScmModule.groovy:addWebhook"
    try {        
        def configData = JSON.getValueFromPropertiesFile('configData')
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def repoName = "${serviceConfig.domain}_${serviceConfig.service}"
        def projectId = getGitLabsProjectId(repoName, configData, gitCredsToken)
        def scmWebHookApi = "https://${configData.REPOSITORY.BASE_URL}/api/v4/projects/${projectId}/hooks?enable_ssl_verification=false&push_events=true&tag_push_events=true&note_events=true&merge_requests_events=true&url="
        sh("curl --request POST --header \"PRIVATE-TOKEN:${gitCredsToken}\"  \"${scmWebHookApi}$webhookUrl\"" )    
    } catch(ex) {
        println "Add webhook failed"
        throw new Exception("Add webhook failed", ex)
    }
}

// Getting the committer infor of the repo
def getRepoCommitterInfo(commitHash, gitCredsToken) { 
    println "ScmModule.groovy:getRepoCommitterInfo"
    try {        
        def configData = JSON.getValueFromPropertiesFile('configData')
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def repoName = "${serviceConfig.domain}_${serviceConfig.service}"

        def committerId = null
        if (commitHash) {
            def projectId = getGitLabsProjectId(repoName, configData, gitCredsToken)
            def scm_commit_api = "https://${configData.REPOSITORY.BASE_URL}/api/v4/projects/${projectId}/repository/commits/${commitHash}"
            def scmCommitResponse = sh("curl --header \"PRIVATE-TOKEN: ${gitCredsToken}\"  \"${scm_commit_api}\"", true)
            
            if (scmCommitResponse != null) {
                def commitDetails = JSON.parseJson(scmCommitResponse)
                if (commitDetails != null) {
                    committerId = commitDetails.author_name
                }
            }

            return committerId
        }
    } catch(ex) {
        println "get repo committer info failed"
        throw new Exception("get repo committer info failed", ex)
    }
}

