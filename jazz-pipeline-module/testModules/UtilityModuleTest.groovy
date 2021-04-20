import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkModuleFileExists() {
        println "TEST: checking Utility.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./UtilityModule.groovy")

        then:
            file.exists() == true
    }

    def testGenerateBucketNameForService() {
        println "TEST: Bucket name is not empty"
        def bucketName
        given:
            def domain
            def service
            def shell = new GroovyShell()
            def file = shell.parse(new File('./UtilityModule.groovy'))
        when:
            bucketName = file.generateBucketNameForService('jazztest', 'testService')
        then:
            bucketName != ""
    }

}
