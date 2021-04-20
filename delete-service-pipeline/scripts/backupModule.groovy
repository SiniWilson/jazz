#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.Yaml as YAML
import common.util.File as FILE
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder


/*
* backupModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/**
* Backup the code if scmManaged is true
*/
def backupService() {
    println "backupModule.groovy: backupService"

    def utilModule = new UtilityModule()
    utilModule.showDeleteEnvParams()

    def eventsModule = new EventsModule()
    def slackModule = new SlackModule()
    def env = System.getenv()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    try {

        backupProject()
        deletePolicies()
        deleteGitlabProject()

        // Send slack notification
        jobContext['EVENT'] = 'DELETE_PROJECT'
        slackModule.sendSlackNotification('DELETE_PROJECT', null, 'COMPLETED', jobContext)
    } catch(ex) {
        /*
        * Sending DELETE FAILED event only in case of service deletion
        */
        if(env.ENVIRONMENT_ID == null) {
            JSON.setValueToPropertiesFile("environmentId", "NA")
        }
        println "Backup failed " + ex.message
        eventsModule.sendFailureEvent('BACKUP_PROJECT', null)
        throw new Exception("Backup failed", ex)
    }
}

/*
* Function to backup project
*/
def backupProject() {
    /*
    * Project backup disabled after Gitlab migration. Sending events for older event listeners.
    */
    def eventsModule = new EventsModule()
    eventsModule.sendStartedEvent('BACKUP_PROJECT')
    eventsModule.sendCompletedEvent('BACKUP_PROJECT', null)
}
    
/*
* Function to deletePolicies
*/
def deletePolicies() {
    println "backupModule.groovy: deletePolicies"

    def eventsModule = new EventsModule()
    def aclModule = new AclModule()
    def eventName = 'REMOVE_POLICIES_AND_REPO_PERMISSIONS'
    eventsModule.sendStartedEvent(eventName, "Started to remove policies and repo write permissions")
    try {
        aclModule.deletePolicies()
        eventsModule.sendCompletedEvent(eventName, "Removed policies and repo write permissions")
    } catch(ex) {
        println "Something went wrong while deleting policies"
        eventsModule.sendFailureEvent(eventName, "Removed policies and repo write permissions")
    }
}

/*
* Function to getGitlab token and delete project from gitlab
*
*/
def deleteGitlabProject() {
    def eventsModule = new EventsModule()
    def utilModule = new UtilityModule()

    try{
        def env = System.getenv()
        def AWS_KEY = env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET = env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION = env['AWS_DEFAULT_REGION']
        def Configs = JSON.getValueFromPropertiesFile('configData')
        eventsModule.sendStartedEvent('DELETE_PROJECT', "Project deletion started")

        def gitlabSecret = utilModule.getSecret(AWS_KEY, AWS_SECRET, AWS_REGION, Configs.GITLAB.GITLAB_SECRET_ID)
        JSON.setValueToPropertiesFile('gitlabPrivateToken', gitlabSecret.token)

        println "Delete Gitlab Project"
        deleteProject()
        eventsModule.sendCompletedEvent('DELETE_PROJECT', 'Repository deleted!')
    } catch(ex) {
        println "Repository deletion failed! " + ex
        eventsModule.sendFailureEvent('DELETE_PROJECT', 'Repository deletion failed!')
        throw new Exception('Repository deletion failed! ', ex)
    }
}

/**
* Delete the project repository from Gitlab
* @return
*/
def deleteProject() {
    // aws configure
    println "backupModule.groovy: deleteProject"

    def slackModule = new SlackModule()
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def scmManaged = serviceConfig.scmManaged;
    def scmType = serviceConfig.scmType
    def scmGitUrl = JSON.getValueFromPropertiesFile('massagedScmGitUrl')
    def privateToken = JSON.getValueFromPropertiesFile('gitlabPrivateToken')
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def deleteRepo = JSON.getValueFromPropertiesFile('deleteRepo')

    try {
        println "deleteRepo : ${deleteRepo}"
        if (scmManaged && scmType == 'gitlab' && deleteRepo) {
            
            println "Deleting repository from gitlab: ${scmGitUrl}"
            def encProjectUrl = URLEncoder.encode(scmGitUrl.replaceAll('(.*)://?(.*?)/(.*)(.git)', '$3'), 'UTF-8');
            println "Encoded project url: ${encProjectUrl}"
            
            def statusCode = sh("curl -X DELETE --header \"Private-Token: ${privateToken}\" 'https://gitlab.com/api/v4/projects/${encProjectUrl}'  --write-out '%{http_code}\n' --silent --output /dev/null")

            println "Delete repository statusCode: $statusCode"

            if(statusCode.indexOf('202') > -1) {
                println "Successfully deleted ${scmGitUrl} from gitlab" 
            } else if(statusCode.indexOf('404') > -1) {
                println "The repository ${scmGitUrl} does not exist. Might have already been deleted." 
            } else {
                println "Repository: ${scmGitUrl} deletion failed!"
                jobContext['EVENT'] = 'DELETE_PROJECT'
                jobContext['Error Message'] = "Repository: ${scmGitUrl} deletion failed!"
                slackModule.sendSlackNotification('DELETE_PROJECT', "Repository: ${scmGitUrl} deletion failed!", 'FAILED', jobContext)
                throw new Exception("Repository: ${scmGitUrl} deletion failed!")
            } 

        } else {
            println 'Nothing to cleanup during repository cleanup stage for BYOR services.. '
        }
    } catch (ex) {
        println "Repository: ${scmGitUrl} deletion failed! Error: " + ex.message
        jobContext['EVENT'] = 'DELETE_PROJECT'
        jobContext['Error Message'] = "Repository: ${scmGitUrl} deletion failed! Error: " + ex.message
        slackModule.sendSlackNotification('DELETE_PROJECT', "Repository: ${scmGitUrl} deletion failed!", 'FAILED', jobContext)
        throw new Exception("Repository: ${scmGitUrl} deletion failed!", ex)
    }
}
