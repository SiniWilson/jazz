#!groovy?
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import groovy.transform.Field


echo "Vault utility module loaded successfully"


@Field def configLoader
@Field def baseUrl
@Field def serviceConfig
@Field def authToken
@Field def environmentLogicalId
@Field def events
@Field def utilModule

def initialize(config_loader,service_config, base_url, auth_token, env, event_module, util_module) {
	configLoader = config_loader
	serviceConfig = service_config
	baseUrl = base_url
	authToken = auth_token
	environmentLogicalId = env
	events = event_module
	utilModule = util_module
}

def updateCustomServicesSafeDetails(safeName, lambdaArns, credsId) {
	for (arn in lambdaArns) {
		updateSafeDetails(safeName, arn, credsId)
	}
}

def updateSafeDetails(safeName, lambdaARN, credsId) {
	def iamRoleArn	
	def safeDetails = getSafeDetails(safeName)

	if (safeDetails) {
		iamRoleArn = utilModule.getRoleDetails(lambdaARN, serviceConfig['region'], credsId)
		if(safeDetails.data.roles && safeDetails.data.roles.length != 0) {
			def isRoleArnExists = safeDetails.data.roles.find{it -> it.value.arn == iamRoleArn}
			if(!isRoleArnExists) {
				addRoleToSafe(iamRoleArn, safeName)
				events.sendCompletedEvent('CREATE_ASSET', null, utilModule.generateAssetMap(serviceConfig['provider'], iamRoleArn, "iam_role", serviceConfig), environmentLogicalId);
			} else echo "Role already exists"
		} else {
			addRoleToSafe(iamRoleArn, safeName)
			events.sendCompletedEvent('CREATE_ASSET', null, utilModule.generateAssetMap(serviceConfig['provider'], iamRoleArn, "iam_role", serviceConfig), environmentLogicalId);
		}
	} else {
		echo "Safe not configured yet."
	}
}

def addRoleToSafe(iamRoleArn, safeName) {
	try {
		def rolePayload = [
			'arn': iamRoleArn,
			'permission': 'read'
		]

		def payload = JsonOutput.toJson(rolePayload)

		def vaultApi = "${baseUrl}/jazz/t-vault/safes/${safeName}/role"
		def statusCode = sh(script: "curl -H \"Content-type: application/json\" \
			-H \"Jazz-Service-ID: ${serviceConfig['service_id']}\" \
			-H \"Authorization: $authToken \" -X POST \
			--write-out '%{http_code}\n' --silent --output /dev/null \
			-d \'${payload}\' \"${vaultApi}\" ", returnStdout: true).trim()

		if(statusCode == '200') echo "Successfully added role ${iamRoleArn} to safe ${safeName}" 
		else echo "Error in adding role ${iamRoleArn} to safe ${safeName}"
	} catch (ex) {
		echo "Error in getting safe details. ${ex}"
	}
}

def getSafeDetails(safeName) {
	def vaultApi = "${baseUrl}/jazz/t-vault/safes/${safeName}"
	def safeResponce = sh(script: "curl -H \"Content-type: application/json\" \
		-H \"Jazz-Service-ID: ${serviceConfig['service_id']}\" \
		-H \"Authorization: $authToken \" \
		-X GET \"${vaultApi}\" ", returnStdout: true).trim()

	def safeDetails
	 try {
		safeDetails = parseJson(safeResponce)
	} catch (ex) {
		echo "Error in getting safe details. ${ex}"
	}
	return safeDetails
}

def deleteSafe(safeName) {
	def vaultApi = "${baseUrl}/jazz/t-vault/safes/${safeName}"
	def statusCode = sh(script: "curl -H \"Content-type: application/json\" \
		-H \"Jazz-Service-ID: ${serviceConfig['service_id']}\" \
		-H \"Authorization: $authToken \" \
		--write-out '%{http_code}\n' --silent --output /dev/null \
		-X DELETE \"${vaultApi}\" ", returnStdout: true).trim()

	if(statusCode == '200') echo "Successfully deleted safe ${safeName}" 
	else echo "Error in deleting safe ${safeName}"
}

def setEnvironmentLogicalId (env) {
	environmentLogicalId = env
}

@NonCPS
def parseJson(jsonString) {
  def lazyMap = new groovy.json.JsonSlurperClassic().parseText(jsonString)
  def m = [:]
  m.putAll(lazyMap)
  return m
}

return this
