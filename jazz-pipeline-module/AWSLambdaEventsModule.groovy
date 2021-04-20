#!groovy?
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import common.util.Props as PROPS
import java.lang.*

/*
* The AWSLambdaEventsModule.groovy module - Lambda event notification module
* This module deals with all the lambda utils methods for checking, creating and removing resources
* for S3, DynamoDB, Sqs, Kinesis
* @author: Saurav Dutta
* @version: 2.0
*/

static main( args ) {
  if( args ) {
    "${args.head()}"( *args.tail() )
  }
}

/*
* Function to check if kinesis stream exists with a given stream name
*/

def checkKinesisStreamExists(stream_name, credsId) {
  println "In AWSLambdaEventsModule.groovy:checkKinesisStreamExists"

  try {
    sh("aws kinesis describe-stream --stream-name ${stream_name} --profile ${credsId} --output json")
    println "Stream exists and have access"
    return true
  } catch (ex) {
    def response
    try {
      response = sh("aws kinesis describe-stream --stream-name ${stream_name} --profile ${credsId} --output json 2<&1 | grep -c 'ResourceNotFoundException'", true)
    } catch (e) {
      println "Error occured while describing the stream: " + e.getMessage()
    }
    if (response) {
      println "Stream does not exists"
      return false
    } else {
      throw new Exception("Error occured while describing the stream details", ex.getMessage())
    }
  }
}

/*
* Function to update kinesis resources
*/

def updateKinesisResourceServerless(event_stream_arn, repoName){
  println "In AWSLambdaEventsModule.groovy:updateKinesisResourceServerless"

  sh("sed -i -- 's/resources/resourcesDisabled/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- '/#Start:streamGetArn/,/#End:streamGetArn/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- 's/arnDisabled/arn/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- 's|{event_stream_arn}|${event_stream_arn}|g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")

  sh("sed -i -- '/#Start:kinesisStreamGetArn/,/#End:kinesisStreamGetArn/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- 's|{event_kinesis_stream_arn}|${event_stream_arn}|g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- 's/#ResourceKinesisDisabled/Resource/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")

}

/*
* Function to get the role Arn from a role name
*/

def getRoleArn(role_name, credsId) {
  println "In AWSLambdaEventsModule.groovy:getRoleArn"

  def role_arn
  try {
    def response = sh("aws iam get-role --role-name ${role_name} --profile ${credsId} --output json", true)
    def mappings = JSON.parseJson(response)
    println "role details : $mappings "
    if(mappings.Role){
      role_arn = mappings.Role.Arn
    }
    return role_arn
  } catch (ex) {
    println "Error occured while describing the role details: " + ex.getMessage()
  }
}

/*
* Function to check if given sqs queue exists
*/
def checkSqsQueueExists(queueName, credsId) {
  println "In AWSLambdaEventsModule.groovy:checkSqsQueueExists"

  try {
    sh("aws sqs get-queue-url --queue-name $queueName --profile ${credsId} --output json")
    println "Queue exists and have access"
    return true
  } catch (ex) {
    def response
    try {
      response = sh("aws sqs get-queue-url --queue-name $queueName --profile ${credsId} --output json 2<&1 | grep -c 'NonExistentQueue'", true)
    } catch (e) {
      println "Error occured while fetching the queue details: " + e.getMessage()
    }
    if (response) {
      println "Queue does not exists"
      return false
    } else {
      throw new Exception("Error occured while fetching the queue details", ex)
    }
  }
}

/*
* Function to check if a different function trigger is attached
*/
def checkIfDifferentFunctionTriggerAttached(event_source_arn, lambda_arn, credsId){
  println "In AWSLambdaEventsModule.groovy:checkIfDifferentFunctionTriggerAttached"

  try {
    response = sh("aws lambda list-event-source-mappings --event-source-arn  ${event_source_arn} --profile ${credsId} --output json", true)
    println "mapping_details : $response"
    def mapping_details = JSON.parseJson(response)
    def isDifferentLambdaAttached  = false
    if(mapping_details.EventSourceMappings.size() > 0) {
      for (details in mapping_details.EventSourceMappings) {
        if(details.FunctionArn) {
          if (details.FunctionArn != lambda_arn ){
            isDifferentLambdaAttached  = true
            println "Function trigger attached already: ${details.FunctionArn}"
            break;
          }
        }
      }
    }
    return isDifferentLambdaAttached
  } catch (ex) {
    throw new Exception("Exception occured while listing the event source mapping", ex)
  }
}

/*
* Function to update the sqs resources
*/
def updateSqsResourceServerless(repoName){
  println "In AWSLambdaEventsModule.groovy:updateSqsResourceServerless"
  sh("sed -i -- '/#Start:isSqsResourceNotExist/,/#End:isSqsResourceNotExist/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}

/*
* Function to remove S3 events
*/

def removeS3EventsFromServerless(isEventSchdld, repoName){
  println "In AWSLambdaEventsModule.groovy:removeS3EventsFromServerless"

  def sedCommand = "/#Start:isS3EventEnabled/,/#End:isS3EventEnabled/d"
  sh("sed -i -- '$sedCommand' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  if (isEventSchdld == false) {
    sh("sed -i -- 's/events:/ /g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  }
}

/*
* Function to check if a given S3 bucket exists
*/

def checkS3BucketExists(s3BucketName, credsId){
  println "In AWSLambdaEventsModule.groovy:checkS3BucketExists"

  try {
    sh("aws s3api head-bucket --bucket $s3BucketName --profile ${credsId} --output json")
    println "Bucket exists and have access"
    return true
  } catch (ex) {
    /*
    * bucket exists but with no access
    */

    def res
    try {
      res = sh("aws s3api head-bucket --bucket $s3BucketName --profile ${credsId} --output json 2<&1 | grep -c 'Forbidden'", true)
    } catch (e) {
      println "Bucket does not exist " + e.getMessage()
      return false
    }
    if (res) {
      println "Bucket exists and don't have access: " + ex.message
      throw new Exception("Bucket exists and don't have access ", ex)
    }
  }
}

/*
* Function to update lambda permission and notification
*/

def updateLambdaPermissionAndNotification(lambdaARN, s3BucketName, action, credsId, region) {
  println "In AWSLambdaEventsModule.groovy:updateLambdaPermissionAndNotification"

  try {
    println "update lambda config using cli"
    UUID uuid = UUID.randomUUID();
    def statementId = uuid.toString();
    sh("aws lambda --region ${region} add-permission --function-name $lambdaARN --statement-id $statementId --action lambda:InvokeFunction --principal s3.amazonaws.com --source-arn arn:aws:s3:::$s3BucketName  --profile ${credsId} --output json")
    def existing_notifications = getBucketNotificationConfiguration(s3BucketName, credsId)
    putBucketNotificationConfiguration(existing_notifications, lambdaARN, s3BucketName, action, credsId)
  } catch (ex) {
    println "Error while updating permission and lambda configuration: " + ex.message
    throw new Exception("Error while updating permission and lambda configuration", ex)
  }
}

/*
* Function to retrieve bucket notification configuration
*/

def getBucketNotificationConfiguration(s3BucketName, credsId){
  println "In AWSLambdaEventsModule.groovy:getBucketNotificationConfiguration"

  def existing_notifications = [:]
  try {
    def existing_notificationsObj = sh("aws s3api get-bucket-notification-configuration --bucket $s3BucketName --profile ${credsId} --output json", true)
    println "existing_notificationsObj: $existing_notificationsObj"
    existing_notifications = JSON.parseJson(existing_notificationsObj)
    return existing_notifications
  } catch (ex) {
    println "Something went wrong: " + ex.message
    return existing_notifications
  }
}

/*
* Function to put notification configuration
*/

def putBucketNotificationConfiguration(existing_notifications, lambdaARN, s3BucketName, action, credsId){
  println "In AWSLambdaEventsModule.groovy:putBucketNotificationConfiguration"

  def new_lambda_configuration = [:]
  def new_s3_event_configuration = [:]
  def events = action.split(",")
  def lambdaFunctionConfigurations = []
  def new_events = []
  new_lambda_configuration.LambdaFunctionArn = lambdaARN

  if (existing_notifications != null && existing_notifications.size() > 0) {
    if(checkIfDifferentFunctionTriggerAttachedForS3(existing_notifications, lambdaARN, events)) {
      throw new Exception("S3 bucket contains a different event source with same or higher priority event trigger already. Please remove the existing event trigger and try again.")
    }
    new_s3_event_configuration = getS3Events(existing_notifications, events, lambdaARN)
  } else {
    new_events = checkAndConvertEvents(events)
    new_lambda_configuration.Events = new_events
    lambdaFunctionConfigurations.add(new_lambda_configuration)
    new_s3_event_configuration.LambdaFunctionConfigurations = lambdaFunctionConfigurations
  }

  println "new existing_notifications : $new_s3_event_configuration"
  if (new_s3_event_configuration != null && new_s3_event_configuration.size() > 0) {
    def newNotificationJson = JSON.objectToJsonString(new_s3_event_configuration)
    def response = sh("aws s3api put-bucket-notification-configuration --bucket $s3BucketName --notification-configuration \'${newNotificationJson}\' --profile ${credsId} --output json", true)
  }
}

/*
* Function to check if any different function trigger is attached to S3
*/

def checkIfDifferentFunctionTriggerAttachedForS3(existing_notifications, lambdaARN, events_action) {
  println "In AWSLambdaEventsModule.groovy:checkIfDifferentFunctionTriggerAttachedForS3"
  
  def existing_events = []
  def isDifferentEventSourceAttached = false
  def isExistsHigherPriorityEventTrigger = false

  for (item in existing_notifications) {
    eventConfigs = item.value
    for (event_config in eventConfigs) {
      existing_events.addAll(event_config.Events)
    }
  }

  for (item in events_action) {
    if ((item.contains("ObjectCreated") && existing_events.contains("s3:ObjectCreated:*")) ||
      (item.contains("ObjectRemoved") && existing_events.contains("s3:ObjectRemoved:*")) ||
      existing_events.contains(item)) {
      isExistsHigherPriorityEventTrigger = true
      break
    }
  }

  if(isExistsHigherPriorityEventTrigger) {
    if(existing_notifications.LambdaFunctionConfigurations) {
      for (lambdaConfigs in existing_notifications.LambdaFunctionConfigurations) {
        if(lambdaConfigs.LambdaFunctionArn != lambdaARN ){
          isDifferentEventSourceAttached = true
          println "Function trigger attached already: ${lambdaConfigs.LambdaFunctionArn}"
          break;
        }
      }
    }
  }
  return isDifferentEventSourceAttached
}

/*
* Function to get the S3 events
*/

def getS3Events(existing_notifications, events_action, lambdaARN){
  println "In AWSLambdaEventsModule.groovy:getS3Events"

  def existing_events = []
  def new_events = []
  def lambdaFunctionConfigurations = []
  def new_lambda_configuration = [:]
  def existing_event_configs = [:]
  def existing_event_configs_copy = [:]
  new_lambda_configuration.LambdaFunctionArn = lambdaARN

  for (item in events_action) {
    new_events.add(item)
  }

  for (item in existing_notifications) {
    existing_event_configs[item.key] = item.value
    existing_event_configs_copy[item.key] = item.value
    eventConfigs = item.value
    for (event_config in eventConfigs) {
      existing_events.addAll(event_config.Events)
    }
  }

  def cleanupIndex = -1
  println "events . $events_action"

  /*
  * Checking the existing events
  * If the new event has (*)
  */

  def isCreationEvent = false
  def isRemovalEvent = false
  if (new_events.contains("s3:ObjectCreated:*")) {
    isCreationEvent = true
  }
  if (new_events.contains("s3:ObjectRemoved:*")) {
    isRemovalEvent = true
  }

  def events_list = []
  if (new_events.size() > 0 && new_events != null) {
    events_list = checkAndConvertEvents(new_events)
  }

  cleanupIndex = -1
  def eventStr

  for (item in existing_notifications) {
    eventConfigs = item.value
    cleanupIndex = -1
    for (event_config in eventConfigs) {
      cleanupIndex++
       eventStr = event_config.Events.join(",")
      if ((eventStr.contains("ObjectCreated") && !eventStr.contains("s3:ObjectCreated:*") && isCreationEvent == true) ||
        (eventStr.contains("ObjectRemoved") && !eventStr.contains("s3:ObjectRemoved:*") && isRemovalEvent == true)) {
        existing_event_configs[item.key][cleanupIndex] = null
      }
    }
  }

  for (item in existing_notifications) {
    existing_event_configs[item.key].removeAll([null])
  }

  for (item in existing_event_configs) {
    if (item.value.size() <= 0) {
      existing_event_configs_copy.remove(item.key);
    }
  }

  if (existing_event_configs_copy.LambdaFunctionConfigurations) {
    for (item in existing_event_configs_copy.LambdaFunctionConfigurations) {
      if(item.LambdaFunctionArn != lambdaARN ){
        lambdaFunctionConfigurations.add(item)
      }
    }
  }

  if (events_list != null && events_list.size() > 0) {
    new_lambda_configuration.Events = new_events
    lambdaFunctionConfigurations.add(new_lambda_configuration)
  }
  if (lambdaFunctionConfigurations != null && lambdaFunctionConfigurations.size() > 0) {
    existing_event_configs_copy.LambdaFunctionConfigurations = lambdaFunctionConfigurations
  }

  println "s3 event config : $existing_event_configs_copy"
  return existing_event_configs_copy
}

/*
* Function to check and convert new events to * event
*/

def checkAndConvertEvents(events){
  println "In AWSLambdaEventsModule.groovy:checkAndConvertEvents"

  def new_events = []
  def cleanupIndex = -1
  def isCreationEvent = false
  def isRemovalEvent = false

  if (events.size() > 0 && events != null) {
    for (item in events) {
      new_events.add(item)
    }

    if (new_events.contains("s3:ObjectRemoved:*")) {
      isRemovalEvent = true
    }

    for (item in events) {
      cleanupIndex++
      if (item.contains("ObjectCreated")) {
        isCreationEvent = true
        new_events[cleanupIndex] = null
      }
      if (isRemovalEvent == true && item.contains("ObjectRemoved")) {
        new_events[cleanupIndex] = null
      }
    }
    new_events.removeAll([null])
    if (isCreationEvent == true) {
      new_events.add("s3:ObjectCreated:*")
    }
    if (isRemovalEvent == true) {
      new_events.add("s3:ObjectRemoved:*")
    }
  }
  println "new_events : $new_events"
  return new_events
}

/*
* Function to check if a given dynamoDB table exists
*/
def checkDynamoDbTableExists (event_source_dynamodb, region, credsId) {
  println "In AWSLambdaEventsModule.groovy:checkDynamoDbTableExists"

  try {
    sh("aws dynamodb describe-table --table-name ${event_source_dynamodb} --region ${region} --profile ${credsId} --output json")
    println "${event_source_dynamodb} exist."
    return true
   } catch (ex) {
    def response
    try {
      response = sh("aws dynamodb describe-table --table-name ${event_source_dynamodb} --region ${region} --profile ${credsId} --output json 2<&1 | grep -c 'ResourceNotFoundException'", true)
    } catch (e) {
      println "Error occured while describing the dynamodb details: " + e.getMessage()
    }
    if (response) {
      return false
      println "${event_source_dynamodb} does not exists"
    } else {
      throw new Exception("Error occured while describing the dynamodb details", ex)
    }
  }
}

/*
* Function to update DynamoDB resources
*/

def updateDynamoDbResourceServerless(event_stream_arn, repoName){
  println "In AWSLambdaEventsModule.groovy:updateDynamoDbResourceServerless"

  sh("sed -i -- '/#Start:isDynamoDbtableNotExist/,/#End:isDynamoDbtableNotExist/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- '/#Start:dynamoDbstreamGetArn/,/#End:dynamoDbstreamGetArn/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- 's/streamArnDisabled/arn/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
  sh("sed -i -- 's|{event_dynamodb_stream_arn}|${event_stream_arn}|g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")

  sh("sed -i -- '/#Start:dynamoDbstreamGetArn/,/#End:dynamoDbstreamGetArn/d' ${PROPS.WORKING_DIRECTORY}/${repoName}/policyFile.yml")
  sh("sed -i -- 's|{event_dynamodb_stream_arn}|${event_stream_arn}|g' ${PROPS.WORKING_DIRECTORY}/${repoName}/policyFile.yml")
  sh("sed -i -- 's/#ResourceDynamoDbDisabled/Resource/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/policyFile.yml")

}

/*
* Function to get stream details of a given DynamoDB table
*/
def getDynamoDbStreamDetails(event_source_dynamodb, region, credsId) {
  println "In AWSLambdaEventsModule.groovy:getDynamoDbStreamDetails"

  def stream_details
  try {
    def streamList = sh("aws dynamodbstreams list-streams --table-name ${event_source_dynamodb} --profile ${credsId} --region ${region} --output json", true)
    println "dynamodb table stream details : $streamList"
    def streamListJson = JSON.parseJson(streamList)
    if (streamListJson.Streams.size() == 0) {
      println "No streams are defined for the table."
      stream_details = createDynamodbStream(event_source_dynamodb, region, credsId)
    } else {
      stream_details = checkDynamoDbTableHasEnabledStream (streamListJson.Streams, region, credsId)
      if (!stream_details.isEnabled) {
        stream_details = createDynamodbStream(event_source_dynamodb, region, credsId)
      }
    }
    return stream_details
  } catch (ex) {
    throw new Exception("Exception occured while listing the stream details of dynamodb table $event_source_dynamodb", ex)
  }
}

/*
* Function to check if a given DynamoDB table has stream enabled
*/
def checkDynamoDbTableHasEnabledStream (Streams, region, credsId) {
  println "In AWSLambdaEventsModule.groovy:checkDynamoDbTableHasEnabledStream"

  def stream_details = [
    "isEnabled" : false
  ]
  for (stream in Streams) {
      def streamDetails = sh("aws dynamodbstreams describe-stream --stream-arn ${stream.StreamArn} --profile ${credsId} --region ${region} --output json", true)
      def streamDetailsJson = JSON.parseJson(streamDetails)
      if ((streamDetailsJson.StreamDescription.StreamStatus == "ENABLED") || (streamDetailsJson.StreamDescription.StreamStatus == "ENABLING")) {
        stream_details.isEnabled = true
        stream_details.isNewStream = false
        stream_details.StreamArn = stream.StreamArn
        break
      }
  }
  return stream_details
}

/*
* Function to create a dynamoDB stream
*/
def createDynamodbStream(tableName, region, credsId) {
  println "In AWSLambdaEventsModule.groovy:createDynamodbStream"

   def stream_details = [
    "isEnabled" : true,
    "isNewStream" : true
  ]

  try {
    def tableDetails = sh("aws dynamodb update-table --table-name ${tableName} --stream-specification StreamEnabled=true,StreamViewType=NEW_AND_OLD_IMAGES --profile ${credsId} --region ${region} --output json", true)
    def tableDetailsJson = JSON.parseJson(tableDetails)
    stream_details.StreamArn = tableDetailsJson.TableDescription.LatestStreamArn
  } catch (ex){
    throw new Exception("Exception occured while creating the dynamodb stream.", ex);
  }
  return stream_details
}

/*
* Function to return event resource name for the environment
*/
def getEventResourceNamePerEnvironment(resource, env, concantinator) {
  println "In AWSLambdaEventsModule.groovy:getEventResourceNamePerEnvironment"

  if(env != "prod"){
    resource = "${resource}${concantinator}${env}"
  }
  return resource
}

/*
* Function to extract queue name from Sqs resource arn and return it
*/
def getSqsQueueName(event_source_sqs_arn, env) {
  println "In AWSLambdaEventsModule.groovy:getSqsQueueName"

  def event_source_sqs = event_source_sqs_arn.split(":(?!.*:.*)")[1]
  return getEventResourceNamePerEnvironment(event_source_sqs, env, "_")
}

/*
* Function to split env from resource arn
*/
def splitAndGetResourceName(resource, env) {
  println "In AWSLambdaEventsModule.groovy:splitAndGetResourceName"

  def resource_arn = resource.split("/")[1]
  return getEventResourceNamePerEnvironment(resource_arn, env, "_")
}
