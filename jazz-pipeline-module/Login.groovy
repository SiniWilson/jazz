#!groovy?


import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*

/*
* Login.groovy
* @author: Sini Wilson
* @version: 2.0
*/

// Main method
static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

//Getting auth token
def getAuthToken() {
    println "Login.groovy:getAuthToken"
    def env = System.getenv()
    def requestId
    try {
        requestId = JSON.getValueFromPropertiesFile('REQUEST_ID')
    } catch (ex) {
        println "Ignore file not found"
    }
    // set the requestId if not already set in the file
    if (!requestId) {
        def request = env.REQUEST_ID ?: sh("uuidgen -t")
        JSON.setValueToPropertiesFile('REQUEST_ID', request)  // its a pipline param
    }
    JSON.setValueToPropertiesFile('apiBaseUrl', env.API_BASE_URL)
    
    try {
        def utilModule = new UtilityModule()
        def principalCreds = utilModule.getSecret(env.AWS_302890901340_ACCESS_KEY, env.AWS_302890901340_SECRET_KEY, env.AWS_DEFAULT_REGION, env.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
      //  println "principalCreds- $principalCreds"
        def resp = sh("curl --location --request POST 'https://login.microsoftonline.com/${env.AAD_SERVICE_PRINCIPAL_TENANT_ID}/oauth2/v2.0/token' --header 'Content-Type: application/x-www-form-urlencoded' --data-urlencode 'grant_type=client_credentials' --data-urlencode 'client_id=${principalCreds.username}' --data-urlencode 'client_secret=${principalCreds.password}' --data-urlencode 'scope=https://graph.microsoft.com/.default'", false, false)
        def authToken
        if(resp) {
            def tokenObj = JSON.parseJson(resp)
            if(tokenObj && tokenObj.access_token && tokenObj.access_token) {
                authToken = tokenObj.access_token
                JSON.setJazzConfigurations("authToken", authToken)
            } else {
                println "login API Failed"
                throw new Exception("login Failed with response: " + resp)
            }                
        } else {
            println "login Failed"
            throw new Exception("login Failed")
        }
    } catch (ex) {
        println "Exception occured while doing authentication"
        throw new Exception("Exception occured while doing authentication", ex)
    }     
}


