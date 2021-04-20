import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkAclModuleFileExists() {
        println "TEST: checking AclModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./AclModule.groovy")

        then:
            file.exists() == true
    }

    def testGlobalData() {
        println "TEST: testing if all the required fields are retrieved properly"
        given:
            def serviceId
            def aclUrl
            def userList
            def categoryList = ['manage', 'code', 'deploy']
            
            JSON.setValueToPropertiesFile('categoryList', categoryList)
            JSON.setValueToPropertiesFile('SERVICE_ID', '123456')
            JSON.setValueToPropertiesFile('API_BASE_URL', 'https://jazz.test.com')
            JSON.setValueToPropertiesFile('serviceConfig', 'sample data')
        
        when:
            def retrievedList = JSON.getValueFromPropertiesFile('categoryList')
            def retrievedId = JSON.getValueFromPropertiesFile('SERVICE_ID')
            def retrievedUrl = JSON.getValueFromPropertiesFile('API_BASE_URL')
            def retrievedData = JSON.getValueFromPropertiesFile('serviceConfig')

        then:
            retrievedList == ['manage', 'code', 'deploy']
            retrievedId == '123456'
            retrievedUrl == 'https://jazz.test.com'
            retrievedData == 'sample data'

    }

}