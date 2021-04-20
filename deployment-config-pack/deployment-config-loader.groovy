#!groovy?
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import groovy.transform.Field
echo "Configuration loadeder module loaded successfully"

/**
 * The Configuration loader module.
 * @author: Deepu Sundaresan(DSundar3)
 * @date: Thursday, January 4, 2018
*/

@Field def g_configBasePath
@Field def g_environment
@Field def g_provider
@Field def ENV_VARS = [:]

/**
 * Initialize the module
 */
def initialize(provider, configBasePath) {
	setConfigBasePath(configBasePath)
	setProvider(provider)
	loadGlobalConfig()
}

/**
 * Load Global configurations across providers, environments
 */
def loadGlobalConfig() {
	def globalConfig
	def configPath = g_provider+"/global.json"
	try {
		if (fileExists(configPath)) {
			def jsonStr = readFile(configPath).trim()
			globalConfig = parseJson(jsonStr)	
			
			if(globalConfig) {
				for(e in globalConfig) {
					ENV_VARS.put(e.key, e.value)
				}
			}
		} else {
			echo "No global configurations to load"
		}
		
	} catch(ex) {
		error "loadGlobalConfig failed with error "+ex.getMessage()
	}

}

/**
 * Load configuration for environment
 */
def loadConfig(module, setModulePrefix=false) {
	def envConfig
	def configPath = g_configBasePath+"/"+g_provider+"/"+g_environment+"/"+module+".json"
	try {
		if (fileExists(configPath)) {
			def jsonStr = readFile(configPath).trim()
			envConfig = parseJson(jsonStr)	
			if(envConfig) {
				if(setModulePrefix) {
					ENV_VARS.put(module, envConfig)
				} else {
					ENV_VARS.putAll(envConfig)
				}
				
			}
		} else {
			error "Configuation file missing for environment $g_environment and module $module for provider $g_provider"
		}
		
	} catch (ex) {
		error "loadEnvConfig failed with "+ex.getMessage()
	}
}

/**
 * Load default environment configuration
 */
def loadConfig() {
	loadConfig("config")
}
 
/**
 * Load modules configurations
 */
 def loadModuleConfig(module) {
	if(!module) {
		error "Provide a valid configuration module name to load"
	}
	loadConfig(module, true) 
	 
 }
 
 /**
 * Load modules configurations
 */
 def loadEnvironmentConfig(environment) {
	setEnvironment(environment)
	loadConfig()
	 
 }

/**
 * Print environment vars
 */
 def echoEnv() {
	 echo JsonOutput.prettyPrint(JsonOutput.toJson(ENV_VARS))
 }

/**
 * JSON parser
 */
@NonCPS
def parseJson(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}


/**
 * Set environment
 */
def setEnvironment(environment) {
	g_environment = environment
}

/**
 * Set provider
 */
def setProvider(provider) {
	g_provider = provider
}

/**
 * Set config path
 */
def setConfigBasePath(path) {
	g_configBasePath = path
}

/**
 * Clean up config folders/Files
 */
def cleanUpConfigLoader() {
	echo "Cleaned up config files"
	sh "rm -rf ./"+g_configBasePath
}

/**
 * Get property loaded from modules
 * @param key - Example: "sonar.enable_sonar"
 */
def get(propertyKey) {
	if(!propertyKey) {
		error "Key can not be null or empty. Provide a valid key"
	}
	def propertyValue = null
	def env = getConfig()
	if(env == null) {
		error "Environment configurations are not loaded or empty"
	} else {
		def _keyItems = [] 
		_keyItems = propertyKey.tokenize('.')
		propertyValue = env
		for (String item : _keyItems) {
			if(propertyValue) {
				propertyValue = propertyValue.get(item)
			} else {
				break
			}
		   
		}
	}
	//echo "$propertyKey....$propertyValue"
	return propertyValue
}

def getConfig() {
	if(ENV_VARS && ENV_VARS.size()!=0) {
		return ENV_VARS
	}
	return null
}

return this;


