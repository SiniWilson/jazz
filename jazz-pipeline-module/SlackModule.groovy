#!groovy?

/*
* SlackModule.groovy
* This module deals with sending notification messages to slack channel
* @author: Saurav Dutta
* @version: 2.0
*/

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import common.util.Shell as ShellUtil
import common.util.Json as JSON
import static common.util.Shell.sh as sh

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/*
* Function to return color codes based on status
* @param status
*/
def getColorCodes(status) {
    println "In SlackModule.groovy:getColorCodes"

    def colorCodes = [
        "INFO": "#808080",
        "COMPLETED": "#5cae01",  // for completion
        "FAILED": "#d0011b",    // for failures
        "STARTED": "#4300ff"    // for start
    ]

    return colorCodes[status]
}

/*
* Function to get notification fields
* @param 
*/
def getNotificationFields() {
    println "In SlackModule.groovy:getNotificationFields"

    try {
        def env = System.getenv()
        def serviceConfig = JSON.getValueFromPropertiesFile("serviceConfig")
        def environmentInfo = JSON.getValueFromPropertiesFile("environmentInfo")
        def pipelineId = env.CI_PIPELINE_ID
        def pipelineUrl = env.CI_PIPELINE_URL        

        if(((serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ||
          (environmentInfo && environmentInfo.user_pipeline instanceof Boolean && environmentInfo.user_pipeline )) &&
            env.USER_CI_PIPELINE_URL && env.USER_CI_PIPELINE_ID) {
            pipelineUrl =  env.USER_CI_PIPELINE_URL
            pipelineId =  env.USER_CI_PIPELINE_ID
        }
        
        def fields = [
            [
                "title": "Job console",
                "value": "${pipelineUrl}",
                "short": false
            ],
            [
                "title": "Service name",
                "value": "${serviceConfig.service}",
                "short": true
            ],
            [
                "title": "Domain",
                "value": "${serviceConfig.domain}",
                "short": true
            ],
            [
                "title": "Service Type",
                "value": serviceConfig.type,
                "short": true
            ],
            [
                "title": "Service Runtime",
                "value": serviceConfig.providerRuntime ? serviceConfig.providerRuntime : 'NA',
                "short": true
            ],		
            [
                "title": "Repository",
                "value": "${serviceConfig.repository}",
                "short": false
            ]
        ]
        return fields 

    } catch(ex) {
        println "Something went wrong while getting notification fields " + ex.message
        throw new Exception ("Something went wrong while getting notification fields ", ex)
    }
}

/*
* Function to prepare the payload and send the notification to slack
* @param jobContext
* @param eventType
* @param status
*/
def sendNotification (eventName, message = "No Message", status, jobContext = [:]) {
    println "In SlackModule.groovy:sendNotification"

    try {
        def env = System.getenv()
        def props = JSON.getAllProperties()
        def configData = props.configData
        def serviceConfig = props.serviceConfig
        def slackChannel = configData.SLACK.SLACK_CHANNEL
        def slackNotifierUserName = configData.SLACK.SLACK_NOTIFIER_NAME
        def slackToken = configData.SLACK.SLACK_TOKEN
        def pipelineId = env.CI_PIPELINE_ID
        def pipelineUrl = env.CI_PIPELINE_URL        
        def environmentInfo = props['environmentInfo']

        if(((serviceConfig.userPipeline instanceof Boolean && serviceConfig.userPipeline) ||
            (environmentInfo && environmentInfo.user_pipeline instanceof Boolean && environmentInfo.user_pipeline )) &&
            env.USER_CI_PIPELINE_URL && env.USER_CI_PIPELINE_ID) {
            pipelineUrl =  env.USER_CI_PIPELINE_URL
            pipelineId =  env.USER_CI_PIPELINE_ID
        }

        def contextMap = ['Branch': props["repoBranch"],
                          'EVENT': eventName,
                          'Error Message':  message]
            
        		
        contextMap.putAll(contextMap)
        def image = ':gitlabicon:'
        long ts = System.currentTimeMillis() / 1000
        def fieldCtxMap = []

        if (env.EVENT_TYPE != 'SERVICE CREATION') {
            fieldCtxMap = getNotificationFields()
        }
        
        for(ctx in jobContext) {
            def field = [
                "title": ctx.key,
                "value": ctx.value,
                "short": true
            ]

            if(ctx.key == 'Repository' || ctx.key == 'Job console') {
                field.short = false
            }

            fieldCtxMap.push(field)
        }

        def attachments = [
            [
                "text": "${env.EVENT_TYPE} ${status}",
                "title": "${env.CI_JOB_NAME}",
                "title_link": "${pipelineUrl}",
                "fallback": "Gitlab Pipeline Alert",
                "color": getColorCodes(status),
                "fields": fieldCtxMap,
                "ts": ts,
                "mrkdwn_in": [
                    "footer",
                    "title"
                ]
            ]
        ]

        def notificationMessageContext = [
            "channel": slackChannel,
            "color": getColorCodes(status),
            "text": "Job: ${env.CI_PROJECT_DIR} with buildnumber ${env.CI_PIPELINE_ID} is ${status.toLowerCase()}",
            "icon_emoji": image,
            "as_user": false,
            "attachments": attachments
        ]

        def payload = JSON.objectToJsonString(notificationMessageContext)
        println "slack payload: $payload"
        def outputStr = sh("curl  -X POST  -k -v -H 'Content-Type:application/json' -H 'Authorization: Bearer ${slackToken}' -d '${payload}' 'https://slack.com/api/chat.postMessage' ", true)
        println "outputStr: $outputStr"

    }
    catch (ex) {
        println "Error occured while sending notification through slack: " + ex.message
    }
}

/*
* Function to send slack Notification
* @param jobContext
* @param eventName
* @param status
*/

def sendSlackNotification(eventName, message = "No Message", status, jobContext = [:]) {
    println "In SlackModule.groovy:sendSlackNotification"
    def env = System.getenv()
    def environmentLogicalId = JSON.getValueFromPropertiesFile('environmentLogicalId')
    if (env.EVENT_TYPE == 'SERVICE DEPLOYMENT') {
        if(status == 'FAILED' && environmentLogicalId && (environmentLogicalId == 'prod' || environmentLogicalId == 'stg')){
            sendNotification (eventName, message, status, jobContext)
        }
    }else {
        sendNotification (eventName, message, status, jobContext)
    }
}
