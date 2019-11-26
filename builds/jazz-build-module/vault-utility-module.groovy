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

def initialize(config_loader,service_config, base_url, auth_token, env) {
	configLoader = config_loader
	serviceConfig = service_config
	baseUrl = base_url
	authToken = auth_token
	environmentLogicalId = env
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
		iamRoleArn = getRoleDetails(lambdaARN, credsId)
		if(safeDetails.data.roles && safeDetails.data.roles.length != 0) {
			def isRoleArnExists = safeDetails.data.roles.find{it -> it.value.arn == iamRoleArn}
			if(!isRoleArnExists) {
				addRoleToSafe(iamRoleArn, safeName)
			} else echo "Role already exists"			
		} else {
			addRoleToSafe(iamRoleArn, safeName)
		}
	} else {
		echo "Safe not configured yet."
	}

	return iamRoleArn 
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

def getRoleDetails(lambdaARN, credsId) {
	def lambdaName = "${configLoader.INSTANCE_PREFIX}-${serviceConfig['domain']}-${serviceConfig['service']}-${environmentLogicalId}"
	def iamRoleArn
	def functionDetails
	try {
		def getFunctionOutput = sh(returnStdout: true, script: "aws lambda get-function --function-name ${lambdaName} --output json  --profile ${credsId} --region ${serviceConfig.region}")
		if (getFunctionOutput) functionDetails = parseJson(getFunctionOutput)
		echo "Function Details : $functionDetails"
		if (functionDetails && functionDetails.Configuration) {
			iamRoleArn = functionDetails.Configuration.Role
		}
	} catch (ex) {
		echo "Error in getting function details. $ex"
	}	
	return iamRoleArn
}

@NonCPS
def parseJson(jsonString) {
  def lazyMap = new groovy.json.JsonSlurperClassic().parseText(jsonString)
  def m = [:]
  m.putAll(lazyMap)
  return m
}

return this
