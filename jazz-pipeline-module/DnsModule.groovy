#!groovy?

/*
* DnsModule.groovy
* This module deals with DNS services
* @author: Saurav Dutta
* @version: 2.0
*/

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import groovy.transform.Field
import common.util.Shell as ShellUtil
import common.util.Json as JSON
import static common.util.Shell.sh as sh

static main( args ) {
    println "DNS module loaded successfully"
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/**
* Making payload for Certificate
*
*/
def createPayloadForCnameRecord(recordName, recordValue){
    println "DnsModule.groovy:createPayloadForCnameRecord"
    try{
        def configData = JSON.getValueFromPropertiesFile('configData')
        def config = JSON.getValueFromPropertiesFile('serviceConfig')
        def environment = JSON.getValueFromPropertiesFile('environmentId')
        def application = config.application
        def domain = config.domain
        def service = config.service
        def owner = configData.DNSNAP.OWNER
        def teamDL = configData.DNSNAP.TEAMDL
        def payload = JSON.objectToJsonString([
            comment: "Entry for certificate domain validation for service:${service} & domain:${domain}",
            recordType: "CNAME",        
            application: application,   // Customer's Application Name
            fqdn: recordName,           // ACM RecordName
            environment: environment,   // Environment of the service
            owner: owner,
            teamDL: teamDL,
            ResourceRecords:[["Value": recordValue]] // ACM RecordValue
        ])
        return payload
    } catch(ex) {
        println "createPayloadForCnameRecord failed: " + ex.message
        throw new Exception("createPayloadForCnameRecord failed", ex )
    }
}


/**
* Creating DNS with the body being passed as parameter
*
*/
def createDnsCnameRecord(){
    println "DnsModule.groovy:createDnsCnameRecord"
    try {
        def recordName = JSON.getValueFromPropertiesFile('recordName')
        def recordValue = JSON.getValueFromPropertiesFile('recordValue')
        def dnsEndpointType = JSON.getValueFromPropertiesFile('dnsEndpointType')
        def payload
        payload = createPayloadForCnameRecord(recordName, recordValue)
        def responseValue = createDNSRecord(dnsEndpointType, payload)
        println "responseValue is ${responseValue}"
        return responseValue
    } catch(ex) {
        println "createDnsCnameRecord failed: " + ex.message
        throw new Exception("createDnsCnameRecord failed", ex )
    }
}

/**
* Creating DNS with the body being passed as parameter
*
*/
def createDnsARecord(fqdn, zoneId, dnsName, dnsEndpointType){
    println "DnsModule.groovy:createDnsARecord"
    try {
        def payload = createPayloadForARecord(fqdn, zoneId, dnsName)
        def responseValue = createDNSRecord(dnsEndpointType, payload)
        return responseValue
    } catch(ex) {
        println "createDnsARecord failed: " + ex.message
        throw new Exception("createDnsARecord failed", ex)
    }
}

/**
* Get status of the DNS/Cert request
*
*/
def getRequestStatus(){
    println "DnsModule.groovy:getRequestStatus"
    try{
        def req_id = JSON.getValueFromPropertiesFile('req_id')
        def configData = JSON.getValueFromPropertiesFile('configData')
        def loginToken = JSON.getValueFromPropertiesFile('loginToken')
        def requestEndpoint = configData.DNSNAP.REQUEST_STATUS_ENDPOINT
        def endpoint = requestEndpoint + '/' + req_id
        def requestStatus =  sh("curl -X GET -k -v -H 'Content-Type:application/json' -H 'Authorization: $loginToken' '${endpoint}'", true);
        def result = JSON.parseJson(requestStatus)
        println "get request Status: $result"
        return result
    } catch (e){
        throw new Exception("Failed while getting request status: " + e.message)
        println "Failed while getting request status: ${e}"
    }
}