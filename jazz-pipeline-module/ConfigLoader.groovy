#!groovy?

import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*


/*
* ConfigLoader.groovy
* @author: Sini Wilson
* @version: 2.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

//To get the config data
def getConfigData() {
  println "ConfigLoader.groovy:getConfigData"
  try {
    def authToken = JSON.getValueFromPropertiesFile('authToken')
    def env = System.getenv()
    def resp = sh ("curl  -X GET  -k -v -H 'Content-Type:application/json' -H 'Authorization: $authToken' '${env.API_BASE_URL}/admin/config' ", true)
    def configData
    if (resp) {
      def configObj = JSON.parseJson(resp)      
      if(configObj && configObj.data && configObj.data.config) {
            configData = configObj.data.config
            JSON.setJazzConfigurations("configData", configData)
      } else {
        println "No configurations defined in the config catalog."  
        throw new Exception("No configurations defined in the config catalog.")
      }
    } else {
      println "No configurations defined in the config catalog."  
      throw new Exception("No configurations defined in the config catalog.")
    }   
  } catch (ex) {
    throw new Exception("Exception occured while getting config data", ex)
  }    
}
