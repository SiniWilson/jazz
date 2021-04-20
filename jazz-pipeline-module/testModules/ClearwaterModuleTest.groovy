import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkClearwaterModuleFileExists() {
        println "TEST: checking ClearwaterModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./ClearwaterModule.groovy")

        then:
            file.exists() == true
    }

    def testGenerateEmailBody() {
        println "TEST: testing generateEmailBody"
        given:
            def emailBody
            def serviceMeta = ["pRLink": "abcd", "service": "testService", "domain": "jazztest"]
            def owner = ["fromAddress": "xyz", "emailAddress": "sample@abcd.com", "displayname": "john doe"]
            def commitResult = ["status": true]
            def config = ["AWS":["REGION":"us-west-2"]]
            JSON.setValueToPropertiesFile('config', config)

            def shell = new GroovyShell()
            def file = shell.parse(new File('./ClearwaterModule.groovy'))
        
        when:
            emailBody = JSON.parseJson(file.generateEmailBody(serviceMeta, owner, commitResult))

        then:

            emailBody.subject == "PR created for publishing assets to Clearwater"
            emailBody.to[0].name.first == "john doe"
            emailBody.templateDirUrl == "https://s3-us-west-2.amazonaws.com/asgc-email-templates/clearwaterv1/"

    }

    def testIsFileExists() {
        println "TEST: testing isFileExists"
        given:
            def filePath

        when:
            filePath = JSON.isFileExists("testModules/ClearwaterModuleTest.groovy")

        then:
            filePath == true
    }

}