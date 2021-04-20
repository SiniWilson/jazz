#!groovy?
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import common.util.File as FILE

/**
 * The Events module for gitlab pipleines
 * @author: Sini Wilson
 * @date: Thursday, May 21, 2020
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/**
 * Send a started event specific to an environment.
 * @param event_name
 * @param message
 * @param moreCxt - more contexual info if needed as a map (key, value pair)
 * @param message
 * @return      
 */
def sendStartedEvent(eventName, message = "No Message", contextMap = [:]) {
    println "EventsModule.groovy:sendStartedEvent"
    def eventStatus = "STARTED"
    sendEvent(eventName, message, eventStatus, contextMap)
}
/**
 * Send a completed event specific to an environment .
 * @param event_name
 * @param message
 * @param moreCxt - more contexual info if needed as a map (key, value pair)
 * @param message
 * @return      
 */ 
def sendCompletedEvent(eventName, message = "No Message", contextMap = [:])  {
    println "EventsModule.groovy:sendCompletedEvent"
    def eventStatus = "COMPLETED"
    sendEvent(eventName, message, eventStatus, contextMap)
}
 

/**
 * Send a failure event specific to an environment .
 * @param event_name
 * @param message
 * @param moreCxt - more contexual info if needed as a map (key, value pair)
 * @param message
 * @return      
 */
def sendFailureEvent(eventName, message = "No Message", contextMap = [:]) {
    println "EventsModule.groovy:sendFailureEvent"
    def eventStatus = "FAILED"	
    sendEvent(eventName, message, eventStatus, contextMap)
}

def sendEvent(eventName, message, eventStatus, contextMap = [:]) {
    println "EventsModule.groovy:sendEvent"  
    def guid = sh("uuidgen -t").trim()
    try {
        def props = JSON.getAllProperties()
        def env = System.getenv()
        def serviceConfig = props.serviceConfig
        def pipelineId = env.CI_PIPELINE_ID
        def pipelineUrl = env.CI_PIPELINE_URL        
        def environmentInfo = props['environmentInfo']

        if(((serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ||
        (environmentInfo && environmentInfo.user_pipeline instanceof Boolean && environmentInfo.user_pipeline )) &&
            env.USER_CI_PIPELINE_URL && env.USER_CI_PIPELINE_ID) {
            pipelineUrl =  env.USER_CI_PIPELINE_URL
            pipelineId =  env.USER_CI_PIPELINE_ID
        }

        

        def apiBaseUrl = props.apiBaseUrl
        def contextJson = [:]       
        def eventJson = [:]
        def environment = props.environmentId;
        def environmentLogicalId = props.environmentLogicalId;
        println "*** ENVIRONMENT *** $environment"
       
        contextJson = [
                "service_type": serviceConfig.type,
                "branch": props["repoBranch"],
                "runtime": serviceConfig.providerRuntime,
                "domain": serviceConfig.domain,
                "iam_role": props.deploymentRole,
                "environment": environment,
                "environmentLogicalId": environmentLogicalId,
                "region": props.deploymentRegion,
                "message": message,
                "buildUrl": pipelineUrl
            ]
        /*
        * sending userData in case of deletion of service or environments
        */
        if(props.USER && ((eventName === 'DELETE_ENVIRONMENT') || (eventName === 'DELETE_PROJECT'))) {
            contextJson.userIdentifier = props.USER
        }
        // sending logicalId in this case as that changes and not the env guid
        if (props.archivedEnvironmentId && props.archivedEnvironmentId != null)
        {
            contextJson.archivedLogicalId = props.archivedEnvironmentId;
        }

        contextJson.putAll(contextMap)        
        
        eventJson = [
            "request_id": props.REQUEST_ID ,
            "event_handler": env.EVENT_HANDLER,
            "event_name": eventName,
            "service_name": serviceConfig.service,
            "service_id": serviceConfig.id,
            "event_status": eventStatus,
            "event_type": env.EVENT_TYPE,
            "username": serviceConfig.created_by,
            "event_timestamp": sh("date -u +'%Y-%m-%dT%H:%M:%S:%3N'", true ).trim(),
            "service_context": contextJson
        ]

        eventJson.service_context = contextJson
        def payload = JSON.objectToJsonString(eventJson)
        println "Sending Event -- Type: ${env.EVENT_TYPE}, Name: ${eventName}, Status: ${eventStatus}"
        // println "events payload - $payload"
    
        FILE.writeFile("./${guid}.json",payload)
        println sh("cat ./${guid}.json")

        def response = sh ("curl -X POST  -k -s -H 'Content-Type: application/json' \
                            'https://cloud-api.corporate.t-mobile.com/api/platform/events' \
                            -d @./${guid}.json ")  // TODO $apiBaseUrl/events
        // println "event resp: $response"
             
    } catch(ex){
        println "Error occured when recording event: " + ex.message
        throw new Exception("Error occured when recording event", ex) 
    } finally {
        println "delete guid.json if it exists"
        sh("rm -rf ${guid}.json")
    }
}
