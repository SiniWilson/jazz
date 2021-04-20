#!groovy?
import common.util.Shell as ShellUtil
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.Yaml as YAML
import common.util.File as FILE
import static common.util.Shell.sh as sh
import java.lang.*
import groovy.transform.Field



/*
* Clearwater.groovy
* @author: Dimple
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}


def downloadArtifact(swaggerUrl, pumlUrl, sequenceDiagramUrl) {
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def serviceRepoSlug = serviceConfig['domain'] + "_" + serviceConfig['service']
    JSON.setValueToPropertiesFile("serviceRepoSlug", serviceRepoSlug)
    def fileList = sh("ls -al", true)
	println "fileList: $fileList"
    println "swaggerUrl: $swaggerUrl"
    println "pumlUrl: $pumlUrl"
    println "sequenceDiagramUrl: $sequenceDiagramUrl"
    sh("rm -rf ./ClearWaterArtifacts")
    
    def checkdir = new File("/ClearWaterArtifacts")
    println "checkdir: " + checkdir.exists()
    if (JSON.isFileExists("/ClearWaterArtifacts")) {
        sh("cd ClearWaterArtifacts")
        if(swaggerUrl) {
            sh("wget --no-check-certificate -q $swaggerUrl")
            if (JSON.isFileExists("/swagger.json")) {
                sh("cp ./swagger.json ./${serviceRepoSlug}.json")
            }	
        }
        if(pumlUrl) {
            sh("wget --no-check-certificate -q $pumlUrl")
        }
        if(sequenceDiagramUrl) {
            sh("wget --no-check-certificate -q $sequenceDiagramUrl")
            if (JSON.isFileExists("/flow.png")) {
                sh("cp ./flow.png ./${serviceRepoSlug}.png")
            }
        }
        sh("cd ..")
    }
   
}


def createMetadataObj(pRLink) {
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def serviceMeta = [:]
    serviceMeta.domain = serviceConfig['domain']
    serviceMeta.service = serviceConfig['service']
    serviceMeta.users = serviceConfig['created_by']
    if(pRLink) {
        serviceMeta.pRLink = pRLink
    }
    return serviceMeta
}
