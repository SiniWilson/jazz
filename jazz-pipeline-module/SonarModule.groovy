#!groovy?
import java.net.URLEncoder
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import java.lang.*


/**
 * The Sonar module to run static code analysis
 * @author: Dimple
 * @date: Thu, May 7, 2020
*/

/**
 * Configure sonar and create the map for project specific sonar
 * properties
 *
 */
 
static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}


/**
  * Generate Project key to Configure Project
  * @param service - Service name
  * @param domain - domain
  * @param branch - current branch being built
  * @param keyPrefix - name of project used as key
  */
def getProjectKey(service, domain, branch, keyPrefix) {
	println "In SonarModule.groovy:getProjectKey"
	try {
		def key = null
		def config = JSON.getValueFromPropertiesFile('serviceConfig');
		def serviceRepo = config.repository
		def env = System.getenv()
		def groupNameStr = serviceRepo.split("https://gitlab.com/")
		def finalStr = groupNameStr[1].split("\\.git")
		def base_group_path = URLEncoder.encode(finalStr[0], "utf-8")
		def gitlab_base_url = "https://gitlab.com/api/v4"
		def gitlabrepoStatus = sh("curl --header 'PRIVATE-TOKEN: ${env.GITLAB_SVC_ACCT_PASSWORD}' -X GET '${gitlab_base_url}/projects/${base_group_path}'")
		def repoResponse = new groovy.json.JsonSlurperClassic().parseText(gitlabrepoStatus)
		if (repoResponse.id) {
			key = repoResponse.id
		} else {
			key =  env.PROJECT_ID
		}
		if(key !== null) {
			key = "prj-" + key
			println "Key is: $key"
			return key
		} else {
			throw new Exception("getProjectKey failed")
		}
	} catch (ex) {
		println "getProjectKey failed: " + ex.message
		throw new Exception("getProjectKey failed", ex)
	}
}

/**
  * Generate Project key to Configure Project
  * @param service - Service name
  * @param domain - domain
  * @param branch - current branch being built
  * @param keyPrefix - name of project used as key
  */
def getProjectName(service, domain, branch, keyPrefix) {
	println "In SonarModule.groovy:getProjectName"
	try {
		def projectKey = "${service}"
		if (domain) {
			projectKey = "${domain}_${projectKey}"
		}
		projectKey = "${keyPrefix}_${projectKey}"
		println "projectName: ${projectKey}"
		return projectKey
	} catch (ex) {
		println "getProjectName failed: " + ex.message
		throw new Exception("getProjectName failed", ex)
	}
	
}

/**
  * Configure Project for static code analysis
  * @param branch - current branch being built
  * @param keyPrefix - name of project used as key
  */
def configureForProject(branch, keyPrefix) {
	try {
		println "In SonarModule.groovy:configureForProject"
		def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig');
		def service = serviceConfig.service
		def domain = serviceConfig.domain
		def runtimeOrFramework
		if (serviceConfig.framework) {
			runtimeOrFramework = serviceConfig.framework //In case of website (Angular/React)
		} else {
			runtimeOrFramework = serviceConfig.runtime //In case of other services
		}
		def configData = JSON.getValueFromPropertiesFile('configData');
		def enableSonar = configData.CODE_QUALITY.SONAR.IS_ENABLED
		def sonarProfile = configData.CODE_QUALITY.SONAR.JAZZ_PROFILE
		def isVSScanEnabled = configData.CODE_QUALITY.SONAR.ENABLE_VULNERABILITY_SCAN && configData.CODE_QUALITY.SONAR.ENABLE_VULNERABILITY_SCAN == "true"
		if(service && domain) {
			def projectName = getProjectName(service, domain, branch, keyPrefix)
			def projectKey = getProjectKey(service, domain, branch, keyPrefix)
			JSON.setValueToPropertiesFile('projectKey', projectKey)
			JSON.setValueToPropertiesFile('projectName', projectName)
			configureSonarProperties(projectKey, runtimeOrFramework, sonarProfile, isVSScanEnabled)
			addProfile(projectKey, projectName)
		} else {
			throw new Exception("Invalid project configurations for Sonar") 
		}
	} catch (ex) {
		println "configureForProject failed: " + ex.message
		throw new Exception("configureForProject failed: ", ex)
	}

 }

def addProfile(projectKey, projectName) {
	try {
		def env = System.getenv()
		def configData = JSON.getValueFromPropertiesFile('configData');
		def sonarToken = env.CDP_SONAR_TOKEN
		def host = configData.CODE_QUALITY.SONAR.HOST_NAME
		def sonarProfile = configData.CODE_QUALITY.SONAR.JAZZ_PROFILE
		projects = sh("curl -X GET -u \"${sonarToken}\": \"https://${host}/api/projects/search?projects=${projectKey}\"")
		println "projects: $projects"
		def responseJSON = JSON.parseJson(projects)
		if(responseJSON.components) {
			//Project found
			println "$responseJSON"
		} else {
			//project not found, so create one
			println sh("curl -X POST -u \"${sonarToken}\": \"https://${host}/api/projects/create?project=${projectKey}&name=${projectName}&organization=default-organization\"")
		}

		//attach profile
		for (language in ["js", "go", "web", "java", "py"]) {
			println sh("curl -X POST -u \"${sonarToken}\": \"https://${host}/api/qualityprofiles/add_project\" --data-urlencode \"project=${projectKey}\" --data-urlencode \"qualityProfile=${sonarProfile}\" --data-urlencode \"language=${language}\" ")
		}
	} catch (ex) {
		println "addProfile Failed. " + ex.message
		throw new Exception("addProfile failed:", ex)
	}
}
/**
 * Configure sonar and create the map for project specific sonar
 * properties
 * @param projectKey - use generated Project key
 * @param runtimeOrFramework - runtime
 * @param sonarProfile - name of Sonar Profile used for project
 * @param isVSScanEnabled - boolean to specify state of VSS SCAN
 */
def configureSonarProperties(projectKey, runtimeOrFramework, sonarProfile, isVSScanEnabled) {
	try {
		println "In SonarModule.groovy:configureSonarProperties"
		def sonarProjectProperties = [:]
		def env = System.getenv()
		def projectVersion = "1.0"
		def sources = "${env.REPO_NAME}/"
		def exclusions = "**/dist/**,**/jazz-modules/**,**/virtualenv/**,**/venv/**,**/*.json,**/*.spec.js,**/vendor/**,**/pkg/**,**/library/**,**/node_modules/**,**/coverage/**,**/target/**,**/.git/**"
		def javaBinaries = "target/"
		def sonarPropertyFile = "${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/sonar-project.properties"
		if (JSON.isFileExists("${sonarPropertyFile}")) {
			def resultList = []
			println "sonar file exists: ${sonarPropertyFile}"
			resultList = new java.io.File("${sonarPropertyFile}").readLines()
			def cleanedList = []
			for (i in resultList) {
				if (!i.toLowerCase().startsWith('#')) {
					cleanedList.add(i)
				}
			}
			for (item in cleanedList) {
				item = item.replaceAll(' ', '');
				def eachItemList = item.tokenize('=')
				if (eachItemList[0] && eachItemList[0].trim()) {
					if (eachItemList[1] == null) { eachItemList[1] = '' }
					sonarProjectProperties.put(eachItemList[0].trim(), eachItemList[1].trim())
				}
			}
		}
        println "sonar file properties: ${sonarProjectProperties}"

		def CI_PROJECT_DIR = env['CI_PROJECT_DIR']
		def workspacePath = "${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}"
		def projectPath = "${CI_PROJECT_DIR}/${env.REPO_NAME}"

		// override with platform specified values
		sonarProjectProperties["sonar.projectKey"] = projectKey
		sonarProjectProperties["sonar.profile"] = sonarProfile
		sonarProjectProperties["sonar.branch.name"] = env.REPO_BRANCH
		// use user provided values, else use default platform specified values
		if (!sonarProjectProperties["sonar.projectName"]) {
			sonarProjectProperties["sonar.projectName"] = JSON.getValueFromPropertiesFile('projectName')
		}
		if (!sonarProjectProperties["sonar.projectVersion"]) {
			sonarProjectProperties["sonar.projectVersion"] = projectVersion
		}
		if (!sonarProjectProperties["sonar.sources"]) {
			sonarProjectProperties["sonar.sources"] = "."
		}
		if (!sonarProjectProperties["sonar.exclusions"]) {
			sonarProjectProperties["sonar.exclusions"] = exclusions
		}
		
		if (runtimeOrFramework.indexOf("java") > -1) {
			if (!sonarProjectProperties["sonar.java.binaries"]) {
				sonarProjectProperties["sonar.java.binaries"] = javaBinaries
			}
			if (!sonarProjectProperties["sonar.coverage.jacoco.xmlReportPaths"]) {
				sonarProjectProperties["sonar.coverage.jacoco.xmlReportPaths"] = "target/site/jacoco/jacoco.xml"
			}
		}
		
		if (runtimeOrFramework.indexOf("go") > -1) {
			if (!sonarProjectProperties["sonar.tests"]) {
				sonarProjectProperties["sonar.tests"] = "."
			}
			if (!sonarProjectProperties["sonar.test.inclusions"]) {
				sonarProjectProperties["sonar.test.inclusions"] = "**/*_test.go"
			}
			if (!sonarProjectProperties["sonar.go.coverage.reportPaths"]) {
				sonarProjectProperties["sonar.go.coverage.reportPaths"] = "src/${env.REPO_NAME}/cov.out"
			}
		}
		if (runtimeOrFramework.indexOf("node") > -1) {
			if (!sonarProjectProperties["sonar.javascript.lcov.reportPaths"]) {
				sonarProjectProperties["sonar.javascript.lcov.reportPaths"] = "coverage/lcov.info"
			}
		}
		if (runtimeOrFramework.indexOf("python") > -1) {
			if (!sonarProjectProperties["sonar.python.coverage.reportPath"]) {
				sonarProjectProperties["sonar.python.coverage.reportPath"] = "coverage.xml"
			}
		}
		if (runtimeOrFramework.indexOf("angular") > -1) {
			if (!sonarProjectProperties["sonar.javascript.lcov.reportPaths"]) {
				sonarProjectProperties["sonar.javascript.lcov.reportPaths"] = "app/coverage/angular-template/lcov.info"
			}
		}
		if (runtimeOrFramework.indexOf("react") > -1) {
			if (!sonarProjectProperties["sonar.javascript.lcov.reportPaths"]) {
				sonarProjectProperties["sonar.javascript.lcov.reportPaths"] = "app/coverage/lcov.info"
			}
		}

		if (isVSScanEnabled) {
			sonarProjectProperties["sonar.dependencyCheck.reportPath"] = "dependency-check-report.xml"
		}
		println "sonarProjectProperties: ${sonarProjectProperties}"
		def sonarJson = JSON.objectToJsonString(sonarProjectProperties)
		JSON.setValueToPropertiesFile('sonarProjectProperties', sonarJson)
	} catch (ex) {
		println "configureSonarProperties Failed. " + ex.message
		throw new Exception("configureSonarProperties failed:", ex)
	}
}



/**
 * Configure and initiate code analyzer.
 */
def doAnalysis() {
	println "In SonarModule.groovy:doAnalysis"
	def enableSonar = false
	try{
		def configData = JSON.getValueFromPropertiesFile('configData');
		enableSonar = configData.CODE_QUALITY.SONAR.IS_ENABLED
		def isVSScanEnabled = configData.CODE_QUALITY.SONAR.ENABLE_VULNERABILITY_SCAN && configData.CODE_QUALITY.SONAR.ENABLE_VULNERABILITY_SCAN == "true"
		def projectKey = JSON.getValueFromPropertiesFile('projectKey')
		def host = configData.CODE_QUALITY.SONAR.HOST_NAME
		def dependencyCheckNISTFilesLocation = configData.CODE_QUALITY.SONAR.DEPENDENCY_CHECK_NIST_FILES_LOCATION
		def dataNISTMirrorUtility = configData.CODE_QUALITY.SONAR.DEPENDENCY_CHECK_NIST_MIRROR_UTILITY
		def dependencyCheckNumberOfHoursBeforeUpdate = configData.CODE_QUALITY.SONAR.DEPENDENCY_CHECK_ELAPSED_HOURS_BEFORE_UPDATES
		if(enableSonar) {
			configureScanner(host)

			if (isVSScanEnabled) {
				runVulnerabilityScan(projectKey, dependencyCheckNISTFilesLocation, dataNISTMirrorUtility, dependencyCheckNumberOfHoursBeforeUpdate)
			}
			runReport()	
		}
		
	} catch(ex) {
		println "Sonar Analysis Failed:" + ex.message
		throw new Exception("Sonar Analysis failed:", ex)
	}
	finally {
		if(enableSonar) { 
          resetConfig()
        }
    }
}

/**
 * Setup, configure and run dependency-check
 * @param projectKey - use generated Project key
 * @param dependencyCheckNISTFilesLocation - dependency check NIST files location 
 * @param dataNISTMirrorUtility - data NIST Mirror utility
 * @param dependencyCheckNumberOfHoursBeforeUpdate - dependency check number of hours before update
 */
def runVulnerabilityScan(projectKey, dependencyCheckNISTFilesLocation, dataNISTMirrorUtility, dependencyCheckNumberOfHoursBeforeUpdate) {
	try {
		println "In SonarModule.groovy:runVulnerabilityScan"
		// create dir if not exists
		sh("mkdir -p $dependencyCheckNISTFilesLocation")
		
		def isDirEmpty_cl = "[ -z \"\$(find $dependencyCheckNISTFilesLocation -maxdepth 1 -type f)\" ];"
		
		def downloadFiles_cl = " wget $dataNISTMirrorUtility -q -O nist-data-mirror.jar && java -jar nist-data-mirror.jar nist_files && mv nist_files/* $dependencyCheckNISTFilesLocation"
		sh("if $isDirEmpty_cl then $downloadFiles_cl; fi;")

		// run dependency check on the current dir
		sh("ls -al $dependencyCheckNISTFilesLocation")
		def dependencyCheckProperties = [:]
		dependencyCheckProperties["project"] = projectKey
		dependencyCheckProperties["scan"] = "."
		dependencyCheckProperties["exclude"] = "**/*.zip"
		dependencyCheckProperties["out"] = "."
		dependencyCheckProperties["format"] = "XML"
		dependencyCheckProperties["cveUrl12Modified"] = "file://$dependencyCheckNISTFilesLocation/nvdcve-Modified.xml"
		dependencyCheckProperties["cveUrl20Modified"] = "file://$dependencyCheckNISTFilesLocation/nvdcve-2.0-Modified.xml"
		dependencyCheckProperties["cveUrl12Base"] = "file://$dependencyCheckNISTFilesLocation/nvdcve-%d.xml"
		dependencyCheckProperties["cveUrl20Base"] = "file://$dependencyCheckNISTFilesLocation/nvdcve-2.0-%d.xml"
		dependencyCheckProperties["cveValidForHours"] = dependencyCheckNumberOfHoursBeforeUpdate

		def dependencyCheckCl = "dependency-check.sh "
		
		for(item in dependencyCheckProperties) {
			dependencyCheckCl += " --${item.key} ${item.value} "
		}
		println "dependencyCheckProperties: $dependencyCheckProperties"
		sh(dependencyCheckCl)
		println "successfully ran vulnerability scan"
	} catch (ex) {
		println "error occured while running Vulnerability Scan: " + ex.message
		throw new Exception ("error occured while running Vulnerability Scan:", ex)
	}
}

/**
 * Reset the configuration file
 */
def resetConfig() {
	try {
		println "In SonarModule.groovy:resetConfig"
		sh("rm -rf ./sonar-project.properties")
	} catch (ex) {
		println "resetConfig Failed. "+ ex.message
		throw new Exception("resetConfig Failed", ex)
	}
}

/**
  * Configure the scanner
  * Update the sonar-scanner properties file
  * @param host - sonar host name
  */
 def configureScanner(host) {
	try {
		println "In SonarModule.groovy:configureScanner"
		def hostName = "https://"+host
		def sonarPropertiesFile = 'sonar-project.properties'
		def env = System.getenv()

		setValues('sonar.host.url', hostName, sonarPropertiesFile)
		setValues('sonar.login', env.CDP_SONAR_TOKEN, sonarPropertiesFile)
	} catch (ex) {
		println "configureScanner Failed:" + ex.message
		throw new Exception("configureScanner failed:", ex)
	}
 }

 /**
 * Run the scanner for code analysis report based on the project settings
 */
def runReport() {
	try {
		println "In SonarModule.groovy:runReport"
		def getAllProp = JSON.getValueFromPropertiesFile('sonarProjectProperties')
		def propList = JSON.jsonParse(getAllProp)
		def env = System.getenv()
		def sonarScannerCl = "cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};sonar-scanner -Dproject.settings=sonar-project.properties "
		for(item in propList) {
			sonarScannerCl += " -D${item.key}=${item.value}"
		}
		println "sonarScannerCl: ${sonarScannerCl}"

		def sonarPropertyFile = "${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/sonar-project.properties"
		if (JSON.isFileExists("${sonarPropertyFile}")) {
			// rename user's project-settings.properties file
			sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};mv sonar-project.properties sonar-project-user.properties")
		}
		// copy project-settings.properties with sonar credentials to project directory
		sh("cp sonar-project.properties ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/")

		def resp = sh("${sonarScannerCl}")
		println "Sonar resp: $resp"
		
		// remove project-settings.properties with sonar credentials to project directory
		sh("rm ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/sonar-project.properties")

		sonarPropertyFile = "${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/sonar-project-user.properties"
		if (JSON.isFileExists("${sonarPropertyFile}")) {
			// restore user's project-settings.properties file
			sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};mv sonar-project-user.properties sonar-project.properties")
		}

	}catch(ex) {
		println "runReport Failed:" + ex.message
		throw new Exception("runReport failed", ex)
	}
}

/**
 * Clean up the build workspace folder for fresh code analysis
 * docker instances will be reused based on availability which may come with build artifacts from
 * previous builds. 
 */
def cleanUpWorkspace() {
	println "In SonarModule.groovy:cleanUpWorkspace"
	def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig');
	def env = System.getenv()
	def runtimeOrFramework
	if (serviceConfig.framework) {
		runtimeOrFramework = serviceConfig.framework //In case of website (Angular/React)
	} else {
		runtimeOrFramework = serviceConfig.runtime //In case of other services
	}
	if (runtimeOrFramework.indexOf("nodejs") > -1) {
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf ./node_modules")
	} else if (runtimeOrFramework.indexOf("java") > -1) {
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};cp ../jazz-pipeline-module/settings_cdp.xml .; mvn clean --settings settings_cdp.xml  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_mvn-clean.log  2>&1")
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};mvn verify --settings settings_cdp.xml  > ../../${PROPS.LOGS_DIRECTORY}/${env.CI_JOB_NAME}_mvn-verify.log  2>&1")
	} else if(runtimeOrFramework.indexOf("python") > -1) {
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf ./library")
	} else if(runtimeOrFramework.indexOf("go") > -1) {
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME};rm -rf ./pkg")
	} else if(runtimeOrFramework.indexOf("angular") > -1) {
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app;rm -rf ./node_modules")
	} else if(runtimeOrFramework.indexOf("react") > -1) {
		sh("cd ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app;rm -rf ./node_modules")
	}
}

/**
 * Set the values to file
 * @param keyName: Key to add in file
 * @param keyValue: Value of the key to add in file
 * @param fileName: File name to store values
 */
def setValues(keyName, keyValue, fileName) {
	println "In SonarModule.groovy:setValues"
	command = "echo '$keyName=${keyValue}' >> ${fileName}"
	['sh', '-c', command].execute()
	// println "fileName: ${fileName}"
}

/*
 * create Sonar measure for clear water metrics 
 * @param sonarHostName - Sonar host name
 * @param metricName - metric name
 * @param score - Clearwater score
 * @param service - service name
 * @param domain - domain 
 * @param branch - branch
 * @param keyPrefix - name of project used as key
 * @param username - sonar username
 * @param password - sonar password
 */
 def createSonarMeasure(sonarHostName, metricName, score, service, domain, branch, keyPrefix, username, password) {
	println "SonarModule.groovy:createSonarMeasure"
	def createUrl = "https://"+sonarHostName+"/api/custom_measures/create"
	def updateUrl = "https://"+sonarHostName+"/api/custom_measures/update"
	def key = getProjectKey(service, domain, branch, keyPrefix)
	def payload = "projectKey=${key}&metricKey=${metricName}&value=${score}"
	def createOrUpdateRes = null
	try {
		def metricId = getMetricIdForProject(key, sonarHostName, metricName, username, password)
		if(metricId) {
			def updatePayload = "id=${metricId}&value=${score}"
			createOrUpdateRes = sh("curl  -X POST  -k -v -u \"$username:$password\" \
						${updateUrl}  \
						-d \'${updatePayload}\'", true)
		} else {
			createOrUpdateRes = sh("curl  -X POST  -k -v -u \"$username:$password\" \
						${createUrl}  \
						-d \'${payload}\'", true)
		}
		println "createOrUpdateRes: $createOrUpdateRes"
		if(createOrUpdateRes) {
			def createOrUpdateResObj =  JSON.parseJson(createOrUpdateRes)
			if(createOrUpdateResObj && createOrUpdateResObj.value) {
				println "${metricName} metric updated successfully"
			} else {
				println "Could not push ${metricName} metric: " + ex.message
				throw new Exception("Could not push ${metricName} metric")
			}
		} else {
			throw new Exception("Invalid response from Sonar while pushing custom measure")
		}
	}
	catch(ex) {
		throw new Exception("error occured while pushing ${metricName} metrics", ex)
	}
}

/**
 * Generic method to push metrics to Sonar
 */
def createSonarMeasureForFortifyScan(sonarHostName, metricName, score, service, domain, branch, keyPrefix, username, password) {
	println "Pushing metrics to Sonar..."
	createSonarMeasure(sonarHostName, metricName, score, service, domain, branch, keyPrefix, username, password)
}

/**
 * Get the Id of the measure for update
 * @param key - project key
 * @param sonarHostName - Sonar host name
 * @param metricName - metric name
 * @param username - sonar username
 * @param password - sonar password
 */
def getMetricIdForProject(key, sonarHostName, metricName, username, password) {
	println "SonarModule.groovy:getMetricIdForProject"
	def measureId = null
	def queryParam = "projectKey=${key}"
	def searchUrl = "https://"+sonarHostName+"/api/custom_measures/search?"+queryParam
	try {
		def res = sh("curl -k -v -u \"$username:$password\" ${searchUrl}", true)
		if(res) {
			println "metric res: $res"
			def resObject = JSON.parseJson(res)
			if(resObject && resObject.customMeasures) {
				for (customMeasure in resObject.customMeasures){
					if(customMeasure && customMeasure.metric && 
						customMeasure.metric.name && 
							customMeasure.metric.name == metricName) {
						measureId = customMeasure.id
						break
					}
				}
			}
		}
		println "measureId: ${measureId}"
		return measureId
	}
	catch(ex) {
		println "error while retrieving measure id for the custom metric: " + ex.message
		throw new Exception("error while retrieving measure id for the custom metric", ex)
	}
}

/**
 * Create or Update Clear water metrics into Sonar
 * @param configs - config Object
 * @param swaggerJson - Swagger file content as a String
 * @param swaggerId - Swagger Id as a String
 */
 def updateClearWaterMetrics(swaggerJson, swaggerId, service, domain, branch, keyPrefix, username, password) {
	println "SonarModule.groovy:updateClearWaterMetrics"
	def configs = JSON.getValueFromPropertiesFile('configData');
	def enableClearWater = configs.CODE_QUALITY.CLEARWATER.IS_ENABLED
	// println "swaggerJson: $swaggerJson"
	if(enableClearWater) {
		def score = null
		def clearwaterData = null
		if(swaggerJson) {
			clearwaterData = generateClearWaterMetrics(configs, swaggerJson, swaggerId)
			// println "clearwaterdata: $clearwaterData"
			if(clearwaterData) {
				score = getClearWaterMetricsScore(clearwaterData)
			}
		} 
		println "score: $score"
		if(score) {
			createSonarMeasure(configs.CODE_QUALITY.SONAR.HOST_NAME, configs.CODE_QUALITY.CLEARWATER.METRIC_NAME, score, service, domain, branch, keyPrefix, username, password)
		}
	}

 }

/*
 * Generate Clear Water metrics by calling the Clear Water API
 * @param configs - config Object
 * @param swaggerDoc - Swagger file content as a String
 * @param swaggerId - Swagger Id as a String
 */
 def generateClearWaterMetrics(configs, swaggerDoc, swaggerId) {
	println "SonarModule.groovy:generateClearWaterMetrics"
	def data
	def payloadjson = [
		"ntid": configs.CODE_QUALITY.CLEARWATER.USER_ID,
		"swaggerId": swaggerId,
		"swaggerDoc":swaggerDoc
	]
	def payload = JSON.objectToJsonString(payloadjson)
	// println "payload: $payload"
	def _tmpFile = "clearwaterPayload.json"	
	JSON.writeFile(_tmpFile, payload)	

	try {
		def clearWaterData = sh("curl  -X POST  -k -v \
			-H \"Content-Type: application/json\" \
			${configs.CODE_QUALITY.CLEARWATER.API}  \
			-d @clearwaterPayload.json")

		sh("rm -rf ./$_tmpFile")	
		// println "clearWaterData: $clearWaterData"
		if(clearWaterData) {
			return clearWaterData
		} else {
			return null
		}
	}
	catch(ex){
        println "failed while getting cw metrics: $ex"
        return null
	}

 }

/*
 * Get Clear Water metrics score 
 * @param rawData - Raw data from API
 */
 def getClearWaterMetricsScore(clearWaterRawData) {
	println "SonarModule.groovy:getClearWaterMetricsScore"
	def rawData =  JSON.parseJson(clearWaterRawData)
	if(rawData &&  rawData.results &&  rawData.results.score &&  rawData.results.score.current) {
		return rawData.results.score.current
	} else {
        println "Cannot find score in clearwater API response"
	}
 }
