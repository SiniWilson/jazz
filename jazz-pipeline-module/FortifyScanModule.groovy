#!groovy
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*


/**
 * The Fortify scan module to run code security scan
 * @author: Dimple
 * @date: June 01, 2020
*/

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/*
 * Start Fortify Scan  
 */
def doScan(keyPrefix) {
	println "FortifyScanModule.groovy:doScan"
	def props = JSON.getAllProperties() 
	def configData = props['configData']
	def env = System.getenv()

	if(configData.CODE_QUALITY.FORTIFY_SCAN.IS_ENABLED) {
		def serviceConfig = props['serviceConfig']
		def requestId = props['REQUEST_ID'].trim()
		def commitSha = props['commitSha'].trim()
		
		def request = "curl --request POST --header \"PRIVATE-TOKEN:${env.GITLAB_SVC_ACCT_PASSWORD}\" \
		--header \"Content-Type: application/json\" \
		--data '{ \"ref\": \"master\", \"variables\": [ { \"key\": \"serviceId\", \"value\": \"${serviceConfig['id']}\" }, { \"key\": \"branch\", \"value\": \"${env['REPO_BRANCH']}\" }, { \"key\": \"requestId\", \"value\": \"${requestId}\" }, { \"key\": \"commitHash\", \"value\": \"${commitSha}\" }, { \"key\": \"keyPrefix\", \"value\": \"${keyPrefix}\" } ] }' \
		\"${configData.CODE_QUALITY.FORTIFY_SCAN.GITLAB_API}\" "
		println "fortify scan request: $request" 
		sh("${request}")
	}
}


/*
 * Initiate scan
 * @params service - service name
 * @params domain - domain name
 * @params akmid - AKM ID
 * @params branch - Branch
 * @params build - Build ID
 * @params repo - the repo URL for checking out source
 * @params commitHash - commit hash on the commit which needs to be scanned
 */
def initiateScan(service, domain, serviceAkmid, branch, build, repo, commitHash) {
	try {
		println "FortifyScanModule.groovy:initiateScan"
		def appname = domain + "_" + service
		def module = ""
		def configData = JSON.getValueFromPropertiesFile('configData')
		// Use platform's default AKMID for the scans
		def akmid = configData.CODE_QUALITY.FORTIFY_SCAN.AKMID
		if (serviceAkmid) {
			println "Using application's AKMID: " + serviceAkmid
			akmid = serviceAkmid
		}
		def scanResults = fortifyScan(repo, akmid, commitHash, build, branch, module)
		def scanStatusUrl
		if(scanResults && scanResults.success && scanResults.message) {
			/* 
			Currently the API response is in the below format , a mix of texts and URL which requires extra parsing
			"message": "Requested scan is already initiated!. Please check the status of your scan via {scanStatusUrl} "
			*/
			def patternStr = null
			if(scanResults.message.startsWith("Requested scan is already initiated")) {
				patternStr = "Requested scan is already initiated!. Please check the status of your scan via "
			} else {
				patternStr = "Scan has been initiated successfully. Please check the status of your scan via "
			}
			scanStatusUrl = scanResults.message.replaceAll(patternStr,"").trim()		
		} else {
			println "Fortify scan initiation failed. "+ scanResults
			throw new Exception("Fortify scan initiation failed.", scanResults)
		}
		JSON.setValueToPropertiesFile("scanStatusUrl", scanStatusUrl)
		println scanStatusUrl
		return scanStatusUrl

	} catch (ex) {
		println "Fortify initiate scan failed." + ex.message
		throw new Exception("Fortify initiate scan failed", ex)
	}
}

/*
 * Do the fortify scan. Refer https://docs.appsec.corporate.t-mobile.com:8000/Source%20Code%20Scanning/requestscan/
 * @params repo - Repo URL for checking out source
 * @params akmid - AKM ID
 * @params commitId - Commit ID on the commit which needs to be scanned
 * @params buildId - Build ID
 * @params branch - Branch
 * @params moduleName - Module Name
 */
def fortifyScan(repo, akmid, commitId, buildId, branch, moduleName){
	try{
		println "FortifyScanModule.groovy:fortifyScan"
		def configData = JSON.getValueFromPropertiesFile('configData')
		def scanConfig = configData.CODE_QUALITY.FORTIFY_SCAN
		def cmd = "curl -k -H \'Content-type: application/json\' -d \'{\"stack\":\"${scanConfig.STACK}\", \"event\":\"${scanConfig.EVENT}\", \"repo\":\""+ repo +"\", \"akmid\":\""+ akmid +"\", \"commitId\":\""+ commitId +"\", \"buildId\":\""+ buildId +"\" ,\"branch\":\""+ branch +"\", \"moduleName\":\""+ moduleName +"\"}\' '${scanConfig.API}'"
		println "Fortify Scan Command is: " + cmd
		def scan = sh("curl -k -H \'Content-type: application/json\' -d \'{\"stack\":\"${scanConfig.STACK}\", \"event\":\"${scanConfig.EVENT}\", \"repo\":\""+ repo +"\", \"akmid\":\""+ akmid +"\", \"commitId\":\""+ commitId +"\", \"buildId\":\""+ buildId +"\" ,\"branch\":\""+ branch +"\", \"moduleName\":\""+ moduleName +"\"}\' '${scanConfig.API}'" , true)
		println "Scan result is: " + scan
		def resultJson = JSON.jsonParse(scan)
		return resultJson
	} catch (ex) {
		println "Fortify scan request failed. "+ ex.message
		return null
	}
}

/**
 * Parse the scan data and get the Issues reports
 * @params scannerStatusUrl - The status URL
 */
def parseScanData(scannerStatusUrl) {
    def data
	try {
		println "FortifyScanModule.groovy:parseScanData"
		def scanResponse = sh("curl  -k -v \
			-H \"Content-Type: application/json\" \
			${scannerStatusUrl}", true)
		println "Response to scan status request: " + scanResponse
		def scanResponseObj = JSON.jsonParse(scanResponse)
		if(scanResponseObj && scanResponseObj.orginal_scan && scanResponseObj.orginal_scan.scan_status && 
			scanResponseObj.orginal_scan.scan_status == "SCAN_COMPLETED") {
			data = scanResponseObj.orginal_scan.scan_issues
			JSON.setValueToPropertiesFile('fortifySonarData', data);
            return data
        } else {
            return null
        }
	}
	catch(ex) {
		println "parseScanData failed" + ex.message
		throw new Exception("parseScanData failed", ex)
	}
}

/*
 * Parse and get the Clone URL
 * @params repoUrl - The repo url of the service
 */
def parseSCMUrl(repoUrl) {
	println "FortifyScanModule.groovy:parseSCMUrl"
	if(repoUrl.contains("http://")) {
		def repo = repoUrl.replace("http://", "ssh://git@")
		repo = repo.replace("/scm", "")
		return repo
	} else if(repoUrl.contains("https://")) {
		def repo = repoUrl.replace("https://", "ssh://git@")
		repo = repo.replace("/scm", "")
		return repo
	} else {
		return repoUrl
	}
	
}
