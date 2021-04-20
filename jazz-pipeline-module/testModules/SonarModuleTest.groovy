import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkSonarModuleFileExists() {
        println "TEST: checking SonarModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./SonarModule.groovy")

        then:
            file.exists() == true
    }

    def testSonarConfigurations() {
        println "TEST: testing Sonar report"
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./SonarModule.groovy'))
            
            def configData = ["CODE_QUALITY": ["SONAR": [
               "DEPENDENCY_CHECK_URL":"http://dl.bintray.com/jeremy-long/owasp/dependency-check-2.1.1-release.zip",
               "CHECKSTYLE_LIB":"http://artifactory.corporate.t-mobile.com/artifactory/cld-release-local/com/puppycrawl/tools/checkstyle/7.6/checkstyle-7.6-all.jar",
               "JAZZ_PROFILE":"JazzProfile",
               "HOST_NAME":"sonar.corporate.t-mobile.com",
               "IS_ENABLED":true,
               "DEPENDENCY_CHECK_ELAPSED_HOURS_BEFORE_UPDATES":"24",
               "DEPENDENCY_CHECK_NIST_MIRROR_UTILITY":"https://github.com/stevespringett/nist-data-mirror/releases/download/1.1.0/nist-data-mirror.jar",
               "ENABLE_VULNERABILITY_SCAN":"false",
               "DEPENDENCY_CHECK_NIST_FILES_LOCATION":"/var/log/depcheck_test2/nistfiles/"
            ]]]
            def serviceConfig = ['service': 'test', 'domain': 'jazztest', 'runtime': 'nodejs10.x']
            JSON.setValueToPropertiesFile('serviceConfig', serviceConfig);
            JSON.setValueToPropertiesFile('configData', configData);
            file.configureForProject('master', 'jazz');

        when:
            def projectKey = JSON.getValueFromPropertiesFile('projectKey')
           
        then:
            projectKey == "jazz_jazztest_test_master"
           
    }

    def testSonarPropertiesFile() {
        println "TEST: testing Sonar properties file"
        given:
            def sonarFile
            def shell = new GroovyShell()
            def file = shell.parse(new File('./SonarModule.groovy'))
            def configLoaderValue = JSON.getValueFromPropertiesFile('configData');
            def host = configLoaderValue.CODE_QUALITY.SONAR.HOST_NAME
            file.configureScanner(host, 'user@123', 'pass@123')

        when:
            sonarFile = new File("sonar-project.properties")
           
        then:
            sonarFile.exists() == true
           
    }

}
