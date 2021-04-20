import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkEnvModuleFileExists() {
        println "TEST: checking EnvModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./EnvironmentDeploymentMetadataLoader.groovy")

        then:
            file.exists() == true
    }

    def testSetEnvGlobalParams() {
        println "TEST: testing setEnvGlobalParams if it sets the variables correctly"
        given:
            def service
            def domain 
            def branch 
            def buildUrlLink 
            def buildId 
            def requestId
            def shell = new GroovyShell()
            def file = shell.parse(new File('./EnvironmentDeploymentMetadataLoader.groovy'))
            file.setEnvGlobalParams('testService', 'jazztest', 'dev', 'https://bulidLink/43', '43', '12345')
        
        when:
            service = JSON.getValueFromPropertiesFile('serviceName')
            domain = JSON.getValueFromPropertiesFile('serviceDomain')
            branch = JSON.getValueFromPropertiesFile('serviceBranch')
            buildUrlLink = JSON.getValueFromPropertiesFile('buildUrl')
            buildId = JSON.getValueFromPropertiesFile('providerBuildId')
            requestId = JSON.getValueFromPropertiesFile('requestId')

        then:
            service == 'testService'
            domain == 'jazztest'
            branch == 'dev'
            buildUrlLink == 'https://bulidLink/43'
            buildId == '43'
            requestId == '12345'
    }

    def testGetRepoURL() {
        println "TEST: testing getRepoURL"
        given:
            JSON.setValueToPropertiesFile('serviceDomain', 'jazztest')
            JSON.setValueToPropertiesFile('serviceName', 'testService')
            JSON.setValueToPropertiesFile('gitRepoBaseUrl', 'https://abcd/')
            def gitCommitUrl
            def shell = new GroovyShell()
            def file = shell.parse(new File('./EnvironmentDeploymentMetadataLoader.groovy'))
        
        when:
            gitCommitUrl = file.getRepoURL()

        then:
            gitCommitUrl == 'https://abcd/jazztest_testService.git'

    }

    def testGenerateDeleteDeploymentMap() {
        println "TEST: generateDeleteDeploymentMap"
        given:
            def mapEntry;
            def sampleMap = [
                domain: 'jazztest',
                provider_build_url:  'https://bulidLink/43',
                provider_build_id: '43'
            ]
            def shell = new GroovyShell()
            def file = shell.parse(new File('./EnvironmentDeploymentMetadataLoader.groovy'))
            file.setEnvGlobalParams('testService', 'jazztest', 'dev', 'https://bulidLink/43', '43', '12345')

        when:
            mapEntry = file.generateDeleteDeploymentMap()

        then:
            sampleMap == mapEntry
    }

    def testGenerateDeploymentMap() {
        println "TEST: generateDeploymentMap"
        given:
            def mapEntry;
            def sampleMap = [
                environment_logical_id: "sample-dev",
                status: "creation_completed",
                domain: "jazztest",
                provider_build_url:  'https://bulidLink/43',
                provider_build_id: '43',
                scm_branch: "dev",
                request_id: "12345",
                scm_commit_hash: "sample-hash",
                scm_url: "https://sample.com"
            ]
            def shell = new GroovyShell()
            def file = shell.parse(new File('./EnvironmentDeploymentMetadataLoader.groovy'))
            file.setEnvGlobalParams('testService', 'jazztest', 'dev', 'https://bulidLink/43', '43', '12345')

        when:
            mapEntry = file.generateDeploymentMap("creation_completed", "sample-dev", "https://sample.com", "sample-hash")

        then:
            sampleMap == mapEntry
    }

    def testGenerateEnvironmentMap() {
        println "TEST: generateEnvironmentMap"
        given:
            def mapEntry;
            def sampleMap = [
                status: "creation_completed",
                domain: "jazztest",
                branch: "dev",
                logical_id: "sample-dev",
                endpoint: "https://sample.com",
                deployment_descriptor: "sample-deployment-descriptor",
                metadata: "sample-data"
            ]
            def shell = new GroovyShell()
            def file = shell.parse(new File('./EnvironmentDeploymentMetadataLoader.groovy'))
            file.setEnvGlobalParams('testService', 'jazztest', 'dev', 'https://bulidLink/43', '43', '12345')

        when:
            mapEntry = file.generateEnvironmentMap("creation_completed", "sample-dev", "sample-data", "sample-deployment-descriptor", "https://sample.com")

        then:
            sampleMap == mapEntry
    }

}