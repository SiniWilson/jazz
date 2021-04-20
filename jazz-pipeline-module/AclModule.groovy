#!groovy?

/*
* AclModule.groovy
* This module deals with updating and deleting acl policies for a service
* @author: Saurav Dutta
* @version: 2.0
*/

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import common.util.Json as JSON
import static common.util.Shell.sh as sh

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}


/*
* To update the user policy and repo permission
*/
def updateServiceACL(policyOnly=false,readOnly=false) {
    println "AclModule.groovy:updateServiceACL"
    try {
        def categoryList = JSON.getValueFromPropertiesFile('categoryList')
        if (policyOnly)
        {
            categoryList.remove('code')
        }
        def baseApiUrl = JSON.getValueFromPropertiesFile('apiBaseUrl')
        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def authToken = JSON.getValueFromPropertiesFile('authToken')
        def usersList = serviceConfig.approvers
        usersList = usersList.collect{ it.toLowerCase() }
        if (serviceConfig.created_by && !usersList.contains(serviceConfig.created_by.toLowerCase()))
        {
            usersList.add(serviceConfig.created_by.toLowerCase())
        }
        def aclUrl = "$baseApiUrl/acl/policies"

        def serviceId = serviceConfig.id

        def policiesList = []
        for (user in usersList) {
            for (category in categoryList) {

                // set policy to read only
                def permission = (readOnly) ? "read" : "write"
                if (category == "manage") {
                    permission = (readOnly) ? "read" : "admin"
                }
                def eachPolicy = [
                    userId: user,
                    permission: permission,
                    category: category
                ]
                policiesList.add(eachPolicy)
            }
        }
        
        def body = JSON.objectToJsonString([
            serviceId: serviceId,
            policies: policiesList,
            policyOnly: policyOnly
        ]);
        
        def resp = sh("curl -X POST -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' 'Jazz-Service-ID: $serviceId' '${aclUrl}' -d '${body}'", true);
        def responseJSON = JSON.parseJson(resp)
        
        println("responseJSON: ${responseJSON}")
        if ((responseJSON && responseJSON.data && responseJSON.data.success == true)) {
           println "Successfully updated service policies."
        } else {
           println "Something went wrong while updating service policies. Error: ${responseJSON}"
           throw new Exception("Something went wrong while updating service policies")           
        }

    } catch(ex) {
        println "updateServiceACL failed: " + ex.message
        throw new Exception("updateServiceACL failed", ex )
    }
}


/*
* Function to delete acl policies for a service
*/
def deletePolicies() {
	println "In AclModule.groovy:deletePolicies"
	try {
		def baseApiUrl = JSON.getValueFromPropertiesFile('apiBaseUrl')
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
		def authToken = JSON.getValueFromPropertiesFile('authToken')

		def serviceId = serviceConfig.id
		def aclUrl = "$baseApiUrl/acl/policies"

		def body = JSON.objectToJsonString([
			serviceId: serviceId,
			policies: []
		]);
		println "body: $body"
		
		def responseJSON = sh("curl -X POST -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' 'Jazz-Service-ID: $serviceId' '${aclUrl}' -d '${body}'", true);
		responseJSON = JSON.parseJson(responseJSON)
		println "responseJSON $responseJSON"

		if (responseJSON && responseJSON.data && responseJSON.data.success == true) {
			println "Successfully deleted service policies."
		} else {
			println "Something went wrong while deleting service policies. Error: ${responseJSON.data}"
			throw new Exception("Something went wrong while deleting service policies")
		}
	} catch(ex) {
		println "deletePolicies failed: " + ex.message
		throw new Exception("deletePolicies failed", ex)
	}
}
