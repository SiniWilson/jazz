import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkAcmModuleFileExists() {
        println "TEST: checking AWSAcmModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./AWSAcmModule.groovy")

        then:
            file.exists() == true
    }

}
