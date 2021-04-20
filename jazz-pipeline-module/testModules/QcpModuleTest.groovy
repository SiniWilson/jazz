import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkQcpModuleFileExists() {
        println "TEST: checking QcpModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./QcpModule.groovy")

        then:
            file.exists() == true
    }

    def testSetDefaultValues() {
        println "TEST: testing setDefaultValues"
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./QcpModule.groovy'))
            def configData = ["QCP":["DEFAULTS":["AKMID":"9999", "APPLICATION_NAME": "sampleApplication", "VERSION":"2"], "ENDPOINT":"https://qcp.sample.com", "CREDENTIALS":["USERNAME": "jazztest-qcp-test", "PASSWORD": "jkdbcdkjdvidub"]]]
            JSON.setValueToPropertiesFile('configData', configData)
            JSON.setValueToPropertiesFile('serviceConfig', ['service':'sampleService', 'domain': 'jazztest', 'id': '12345', 'akmId': '12345'])
            file.initialize("1")
        when:
            def qcpEndpoint = configData.QCP.ENDPOINT
            def qcpUsername = configData.QCP.CREDENTIALS.USERNAME
            def qcpPassword = configData.QCP.CREDENTIALS.PASSWORD
            def akmId = JSON.getValueFromPropertiesFile('akmId')
            def applicationName = JSON.getValueFromPropertiesFile('applicationName')
            def version = JSON.getValueFromPropertiesFile('version')
        
        then:
            qcpEndpoint == "https://qcp.sample.com"
            qcpUsername == "jazztest-qcp-test"
            qcpPassword == "jkdbcdkjdvidub"
            akmId == "12345"
            applicationName == "sampleApplication.jazztest.sampleService"
            version == "1"
    }

    def testGetAuthentication() {
        println "TEST: testing getAuthentication failure scenario"
        given:
            def value;
            def shell = new GroovyShell()
            def file = shell.parse(new File('./QcpModule.groovy'))
        
        when:
            value = file.getAuthentication(null, null)

        then:
            value == null
    }
}