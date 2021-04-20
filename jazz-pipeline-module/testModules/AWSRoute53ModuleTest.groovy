import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkAWSRoute53ModuleFileExists() {
        println "TEST: checking AWSRoute53Module.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./AWSRoute53Module.groovy")

        then:
            file.exists() == true
    }

}