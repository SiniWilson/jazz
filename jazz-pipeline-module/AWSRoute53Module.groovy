#!groovy?

/** AWS Route53 module
* This module deals specifically with
* @Creating a Record
* @Gettting Details of a Record
* @Deleting a Record
* @Checking if a Record exists
*/

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import groovy.json.*
import java.lang.*

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/**
* Creating DNS Record
* @fqdn -> Fully Qualified Domain Name provided by user (test.jazz.t-mobile.com)
* @zoneId -> Route 53 Hosted ZoneId
* @recordZoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
* @dnsEndpointType -> endpoint type if it is public or private
* @awsProfile -> aws profile which is configured
*/
def createRecord(fqdn, zoneId, recordZoneId, dnsName, awsProfile) {
    println "In AWSRoute53Module.groovy:createRecord"

    try{
        println "zoneId: $zoneId"
        println "recordZoneId: $recordZoneId"
        def service = JSON.getValueFromPropertiesFile('serviceName')
        def domain = JSON.getValueFromPropertiesFile('serviceDomain')
        createPayloadForRecord(recordZoneId, dnsName, fqdn)
        def responseValue = createDNSRecord(zoneId, awsProfile)
        println "responseValue: $responseValue"
        return responseValue
    } catch(ex) {
        println "Error while creating DNS Record " + ex
        throw new Exception("Error while creating DNS Record ", ex)
    }
}

/**
* Creating Cname Record
* @fqdn -> Fully Qualified Domain Name provided by user (test.jazz.t-mobile.com)
* @zoneId -> Route 53 Hosted ZoneId
* @recordZoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
* @dnsEndpointType -> endpoint type if it is public or private
* @awsProfile -> aws profile which is configured
*/
def createCnameRecord(fqdn, recordValue, zoneId, awsProfile) {
    println "In AWSRoute53Module.groovy:createCnameRecord"

    try{
        def service = JSON.getValueFromPropertiesFile('serviceName')
        def domain = JSON.getValueFromPropertiesFile('serviceDomain')
        createPayloadForCnameRecord(recordValue, fqdn)
        def responseValue = createCertificateRecord(zoneId, awsProfile)
        return responseValue
    } catch(ex) {
        println "Error while creating DNS Record " + ex
        throw new Exception("Error while creating DNS Record ", ex)
    }
}

/**
* Get recordDetails of the fqdn for records list
* @fqdn -> Fully Qualified Domain Name provided by user
* @zoneId -> Route 53 Hosted ZoneId
* @awsProfile -> aws profile which is configured
*/
def getRecordDetails(fqdn, zoneId, awsProfile) {
    println "In AWSRoute53Module.groovy:getRecordDetails"

    try {
        def recordSet = sh("aws route53 list-resource-record-sets --hosted-zone-id $zoneId --query \"ResourceRecordSets[?Name == '$fqdn']\" --profile $awsProfile")
        println "recordSet: $recordSet"
        recordSet = JSON.jsonParse(recordSet)
        /*
        * returns the matching array of objects
        * caller needs to filter out
        */
        return recordSet
    } catch(ex) {
        println "Error while getting Record Details " + ex.message
        if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Error while getting Record Details  ", ex)
        }
    }
}

/**
* Checking if DNS exists with the same fqdn
* @fqdn -> Fully Qualified Domain Name provided by user
* @zoneId -> Route 53 Hosted ZoneId
* @awsProfile -> aws profile which is configured
* @endpoint -> endpoint of the service
*/
def checkIfRecordExists(fqdn, zoneId, awsProfile, endpoint) {
    println "In AWSRoute53Module.groovy:checkIfRecordExists"
    
    try {
        def isExists = false;
        def recordSet = sh("aws route53 list-resource-record-sets --hosted-zone-id $zoneId --query \"ResourceRecordSets[?Name == '$fqdn']\" --profile $awsProfile")
        println "recordSet: $recordSet"
        recordSet = JSON.jsonParse(recordSet)
        if(recordSet.size() > 0 && recordSet[0].AliasTarget.DNSName == endpoint){
            isExists = true
        }
        println "isExists: $isExists"
        return isExists
    } catch(ex) {
        println "Failed while checking Record " + ex.message
        if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Failed while checking Record  ", ex)
        }
    }
}

/**
* Making payload for creating DNS Record
* @fqdn -> Fully Qualified Domain Name provided by user
* @zoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
*/
def createPayloadForRecord(zoneId, dnsName, fqdn) {
    println "In AWSRoute53Module.groovy:createPayloadForRecord"

    def service = JSON.getValueFromPropertiesFile('serviceName')
    def domain = JSON.getValueFromPropertiesFile('serviceDomain')
    def AliasTarget = [:]
    AliasTarget.put("HostedZoneId", zoneId)
    AliasTarget.put("DNSName", dnsName)
    AliasTarget.put("EvaluateTargetHealth", false)

    def ResourceRecordSet = [:]
    ResourceRecordSet.put("Name", fqdn)
    ResourceRecordSet.put("Type", "A")
    ResourceRecordSet.put('AliasTarget', AliasTarget)

    def action = [:]
    action.put("Action", "CREATE")
    action.put("ResourceRecordSet", ResourceRecordSet)

    def Changes = []
    Changes.add(action)

    def finalMap = [:]
    finalMap.put("Comment", "Endpoint for service:${service} domain:${domain}")
    finalMap.put("Changes", Changes)

    def payloadRecord = JSON.objectToJsonString(finalMap)

    println "finalMap: $finalMap"

    if (JSON.isFileExists('createBatch.json')) {
        println "file exists"
        sh("rm -rf createBatch.json")
    }
    sh("echo '$payloadRecord' >> createBatch.json")
}

/**
* Making payload for creating Certificate
* @recordValue -> ACM RecordValue
* @fqdn -> Fully Qualified Domain Name provided by user
*/
def createPayloadForCnameRecord(recordValue, fqdn) {
    println "In AWSRoute53Module.groovy:createPayloadForCnameRecord"

    def service = JSON.getValueFromPropertiesFile('serviceName')
    def domain = JSON.getValueFromPropertiesFile('serviceDomain')
    def ResourceRecords = [["Value": recordValue]]

    def ResourceRecordSet = [:]
    ResourceRecordSet.put("Name", fqdn)
    ResourceRecordSet.put("Type", "CNAME")
    ResourceRecordSet.put("TTL", 500)
    ResourceRecordSet.put('ResourceRecords', ResourceRecords)

    def action = [:]
    action.put("Action", "CREATE")
    action.put("ResourceRecordSet", ResourceRecordSet)

    def Changes = []
    Changes.add(action)

    def finalMap = [:]
    finalMap.put("Comment", "Endpoint for service:${service} domain:${domain}")
    finalMap.put("Changes", Changes)

    def payloadRecord = JSON.objectToJsonString(finalMap)

    if (JSON.isFileExists('createCnameBatch.json')) {
        sh("rm -rf createCnameBatch.json")
    }
    sh("echo '$payloadRecord' >> createCnameBatch.json")
}

/**
* Making payload for deleting DNS Record
* @zoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
* @fqdn -> Fully Qualified Domain Name provided by user
*/
def createPayloadForDeletingRecord(zoneId, dnsName, fqdn) {
    println "In AWSRoute53Module.groovy:createPayloadForDeletingRecord"

    def AliasTarget = [:]
    AliasTarget.put("HostedZoneId", zoneId)
    AliasTarget.put("DNSName", dnsName)
    AliasTarget.put("EvaluateTargetHealth", false)

    def ResourceRecordSet = [:]
    ResourceRecordSet.put("Name", fqdn)
    ResourceRecordSet.put("Type", "A")
    ResourceRecordSet.put('AliasTarget', AliasTarget)

    def action = [:]
    action.put("Action", "DELETE")
    action.put("ResourceRecordSet", ResourceRecordSet)

    def Changes = []
    Changes.add(action)

    def finalMap = [:]
    finalMap.put("Comment", "Delete record set ${dnsName}")
    finalMap.put("Changes", Changes)

    def deletePayload = JSON.objectToJsonString(finalMap)

    println "finalMap: $finalMap"
    if (JSON.isFileExists('deleteBatch.json')) {
        sh("rm -rf deleteBatch.json")
    }
    sh("echo '$deletePayload' >> deleteBatch.json")
}

/**
* Making payload for deleting Certificate
* @zoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
* @fqdn -> Fully Qualified Domain Name provided by user
*/
def createPayloadForDeletingCnameRecord(recordValue, fqdn) {
    println "In AWSRoute53Module.groovy:createPayloadForDeletingCnameRecord"

    def service = JSON.getValueFromPropertiesFile('serviceName')
    def domain = JSON.getValueFromPropertiesFile('serviceDomain')
    def ResourceRecords = [["Value": recordValue]]

    def ResourceRecordSet = [:]
    ResourceRecordSet.put("Name", fqdn)
    ResourceRecordSet.put("Type", "CNAME")
    ResourceRecordSet.put("TTL", 500)
    ResourceRecordSet.put('ResourceRecords', ResourceRecords)

    def action = [:]
    action.put("Action", "DELETE")
    action.put("ResourceRecordSet", ResourceRecordSet)

    def Changes = []
    Changes.add(action)

    def finalMap = [:]
    finalMap.put("Comment", "Delete record set for service:${service} domain:${domain}")
    finalMap.put("Changes", Changes)

    def deletePayload = JSON.objectToJsonString(finalMap)

    if (JSON.isFileExists('deleteCnameBatch.json')) {
        sh("rm -rf deleteCnameBatch.json")
    }

    sh("echo '$deletePayload' >> deleteCnameBatch.json")
}

/**
* Creating DNS Record
* @zoneId -> Route 53 Hosted ZoneId
* @awsProfile -> aws profile which is configured
*/
def createDNSRecord(zoneId, awsProfile) {
    println "In AWSRoute53Module.groovy:createDNSRecord"

    try {
        def response = sh("aws route53 change-resource-record-sets --hosted-zone-id $zoneId --change-batch file://createBatch.json --profile ${awsProfile}")
        println "response: $response"
        response = JSON.jsonParse(response)
        if(response.ChangeInfo.Status == "PENDING"){
            return response
        } else {
            return null
        }
    } catch(ex) {
        println "Error while creating DNS " + ex.message
        if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Error while creating DNS ", ex)
        }
    }
}

/**
* Creating Certificate Record
* @zoneId -> Route 53 Hosted ZoneId
* @awsProfile -> aws profile which is configured
*/
def createCertificateRecord(zoneId, awsProfile) {
    println "In AWSRoute53Module.groovy:createCertificateRecord"

    try {
        def response = sh("aws route53 change-resource-record-sets --hosted-zone-id $zoneId --change-batch file://createCnameBatch.json --profile ${awsProfile}")
        println "response: $response"
        response = JSON.jsonParse(response)
        if(response.ChangeInfo.Status == "PENDING"){
            return response
        } else {
            return null
        }
    } catch(ex) {
        println "Error while creating DNS " + ex.message
        if(ex.message.contains('but it already exists')){
            def errorResponse = [:]
            errorResponse.put('duplicate', true)
            return errorResponse
        } else if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Error while creating DNS ", ex)
        }
    }
}

/**
* Delete the DNS Record
* @fqdn -> Fully Qualified Domain Name provided by user (test.jazz.t-mobile.com)
* @zoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
* @recordZoneId -> Route 53 Hosted ZoneId
* @awsProfile -> aws profile which is configured
*/
def deleteDNSRecord(fqdn, zoneId, recordZoneId, dnsName, awsProfile){
    println "In AWSRoute53Module.groovy:deleteDNSRecord"

    try{
        println "zoneId: $zoneId"
        println "recordZoneId: $recordZoneId"
        createPayloadForDeletingRecord(recordZoneId, dnsName, fqdn)
        def response = sh("aws route53 change-resource-record-sets --hosted-zone-id $zoneId --change-batch file://deleteBatch.json --profile ${awsProfile}")
        println "response: $response";
        response = JSON.jsonParse(response);
        if(response.ChangeInfo.Status == "PENDING"){
            println "Record deleted for ${dnsName}"
        } else {
            println "Something went wrong while deleting record for ${dnsName}"
        }
    } catch(ex){
        println "Error while deleting records: " + ex.message
        if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Error while deleting records:  ", ex)
        }
    }
}

/**
* Delete the Certificate Record
* @fqdn -> Fully Qualified Domain Name provided by user (test.jazz.t-mobile.com)
* @zoneId -> Route 53 Hosted ZoneId
* @dnsName -> Environment endpoint of the service
* @recordZoneId -> Route 53 Hosted ZoneId
* @awsProfile -> aws profile which is configured
*/
def deleteCertificateRecord(fqdn, recordValue, zoneId, awsProfile){
    println "In AWSRoute53Module.groovy:deleteCertificateRecord"

    try{
        createPayloadForDeletingCnameRecord(recordValue, fqdn)
        def response = sh("aws route53 change-resource-record-sets --hosted-zone-id $zoneId --change-batch file://deleteCnameBatch.json --profile ${awsProfile}")
        println "response: $response";
        response = JSON.jsonParse(response);
        if(response.ChangeInfo.Status == "PENDING"){
            println "Record deleted"
        } else {
            println "Something went wrong while deleting the record"
        }
        return response
    } catch(ex){
        println "Error while deleting records: " + ex.message
        if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Error while deleting records:  ", ex)
        }
    }
}


/*
* Function to assume a role to get temporary credentials to access Route53 service in a different account 
* @awsProfile -> aws profile which is configured
* @region -> aws region
*/
def assumeTempRole(awsProfile, region) {
    println "In AWSRoute53Module.groovy:assumeTempRole"

    def utilModule = new UtilityModule()
    try {
        def configData = JSON.getValueFromPropertiesFile('configData')
        def roleArn = configData.JAZZ.DNS.AWS.IAM.ROLE
        def response = sh("aws sts assume-role --role-arn $roleArn --role-session-name dns-access --profile ${awsProfile}")
        response = JSON.parseJson(response);
        def accessKey = response.Credentials.AccessKeyId
        def secretKey = response.Credentials.SecretAccessKey
        def sessionToken = response.Credentials.SessionToken
        /*
        * Configuring aws credentials with the temporary credentials and returning back the awsProfile
        */
        def tempCreds = utilModule.configureAWSProfile(accessKey, secretKey, region, sessionToken)
        return tempCreds

    } catch(ex) {
        println "Error occurred while assuming role " + ex
        throw new Exception("Error occurred while assuming role ", ex)
    }
}

/**
* Get the route53 hosted zone id
* @region -> service region
* @endpointConfigType -> is the service is edge-optimized or regional
* @endpointType -> is the service website or api
*/
def getHostedZoneId(region, endpointConfigType, endpointType){
    println "In AWSRoute53Module.groovy:getHostedZoneId"

    def configData = JSON.getValueFromPropertiesFile('configData')
    region = region.toUpperCase()
    if(endpointType == "website"){
        region = "US-EAST-1"
        return configData.JAZZ.DNS.AWS.ROUTE53.MANAGED_HOSTED_ZONES.CLOUDFRONT[region] // Z2FDTNDATAQYW2
    } else if(endpointType == "api" && endpointConfigType == "EDGE"){
        region = "US-EAST-1"
        return configData.JAZZ.DNS.AWS.ROUTE53.MANAGED_HOSTED_ZONES.CLOUDFRONT[region] // Z2FDTNDATAQYW2
    } else if(endpointType == "api" && endpointConfigType != "EDGE"){
        return configData.JAZZ.DNS.AWS.ROUTE53.MANAGED_HOSTED_ZONES.API_GATEWAY[region] //Z1UJRXOUMOOFQ8 ZOJJZC49E0EPZ Z2OJLYMUO9EFXC
    }
}

/*
* Get the request status of a change batch request
* @id -> the ID of the change batch request
* @awsProfile -> aws profile which is configured
*/
def getRequestStatus(id, awsProfile){
    println "In AWSRoute53Module.groovy:getRequestStatus"

    try {
        def response = sh("aws route53 get-change --id ${id} --profile ${awsProfile}")
        println "response: $response"
        response = JSON.jsonParse(response)
        return response
    } catch(ex) {
        println "Error while getting status: " + ex.message
        if(ex.message.contains('Throttling')) {
            return 'Throttling error'
        } else {
            throw new Exception("Error while getting status:  ", ex)
        }
    }
}