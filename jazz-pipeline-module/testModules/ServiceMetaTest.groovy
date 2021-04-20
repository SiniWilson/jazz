import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkModuleFileExists() {
        println "TEST: checking ServiceMetadataLoader.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./ServiceMetadataLoader.groovy")

        then:
            file.exists() == true
    }

    def testGetServiceDetails() {
        println "TEST: Get service details using service id"
        def bucketName
        given:
            def apiUrl = "https://cloud-api.corporate.t-mobile.com/api/jazz"
            def serviceId = "72fca2eb-739d-0ebe-fe87-d277dd601c26"
            def token = "dummytoken"
            JSON.setValueToPropertiesFile('authToken', token)
            def shell = new GroovyShell()
            def file = shell.parse(new File('./ServiceMetadataLoader.groovy'))
        when:
            file.getServiceDetails(apiUrl, serviceId)
            def serviceDetails = JSON.getValueFromPropertiesFile('serviceConfig')
        then:
            serviceDetails != ""
    }

    def testLoadServiceMetaData() {
        println "TEST: Get service details using domain and service"
        def bucketName
        given:
            def apiUrl = "https://cloud-api.corporate.t-mobile.com/api/jazz"
            def service = "depevthand"
            def domain = "jazz"
            def shell = new GroovyShell()
            def file = shell.parse(new File('./ServiceMetadataLoader.groovy'))
            file.setService(service)
            file.setDomain(domain)
        when:
            file.loadServiceMetaData(apiUrl)
            def serviceDetails = JSON.getValueFromPropertiesFile('serviceConfig')
        then:
            serviceDetails != ""
    }

    def testLoadAssetInfo() {
        println "TEST: loadAssetInfo"
        def bucketName
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./ServiceMetadataLoader.groovy'))
            def configLoader = ["AWS":[
                "API": [
                 "HOST_NAMES": [
                    "PROD": "cloud-api.corporate.t-mobile.com",
                    "DEV": "dev-cloud-api.corporate.t-mobile.com",
                    "STG": "stg-cloud-api.corporate.t-mobile.com"
                 ]
                ]
            ]]
            JSON.setValueToPropertiesFile('configData', configLoader)
        when:
            file.loadAssetInfo("apigateway", "stg")
            def assetInfo = JSON.getValueFromPropertiesFile('serviceAssetConfig')
        then:
            assetInfo != ""
    }

    def testGetScmType() {
        println "TEST: getScmType"
        def bucketName
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./ServiceMetadataLoader.groovy'))
        when:
            
            def scmType = file.getScmType()
        then:
            scmType == "gitlab"
    }

}
