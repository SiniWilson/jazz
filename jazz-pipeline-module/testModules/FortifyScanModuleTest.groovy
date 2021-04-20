import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkFortifyScanModuleFileExists() {
        println "TEST: checking FortifyScanModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./FortifyScanModule.groovy")

        then:
            file.exists() == true
    }

    def testInitiateScan() {
        println "TEST: testing Initiating Fortify Scan"
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./FortifyScanModule.groovy'))
            def scanStatusUrl
            def repo = 'ssh://git@gitlab.com/tmobile/jazz/core/jazz_deployments-event-handler.git'
            def commitNumber = '6eab94e0ccf120978c3fc9a9f8a68e6b0e4b77777'
            def configData = ["CODE_QUALITY": ["FORTIFY_SCAN":[
               "STACK":"CORPORATE_EIT",
               "METRIC_NAME_HIGH":"fortify_high_priority_issues",
               "METRIC_NAME_LOW":"fortify_low_priority_issues",
               "SCANNER_JOB":"Cloud API Pipeline Jobs/FortifyScanner",
               "RETRY_COUNT":"10",
               "IS_ENABLED":true,
               "AKMID":"102827",
               "API":"https://scan.appsec.corporate.t-mobile.com/requestScan",
               "GITLAB_API":"https://gitlab.com/api/v4/projects/19181318/trigger/pipeline",
               "GITLAB_BRANCH":"impl-fortify-scan",
               "GITLAB_TOKEN":"e152450eeddef8fa217ba500e04c72",
               "METRIC_NAME_MEDIUM":"fortify_medium_priority_issues",
               "SLEEP_TIME_IN_SECONDS":"30",
               "EVENT":"SCAN_REQUEST"
            ]]]
            def serviceConfig = ['serviceId': '123434', 'domain': 'jazztest', 'runtime': 'nodejs10.x', 'branch': 'master', 'akmid': '13234']
            JSON.setValueToPropertiesFile('serviceConfig', serviceConfig);
            JSON.setValueToPropertiesFile('repoName', 'master');
            JSON.setValueToPropertiesFile('configData', configData);
            JSON.setValueToPropertiesFile('REQUEST_ID', '123456');
            def scanData = {"STACK":"CORPORATE_EIT","METRIC_NAME_HIGH":"fortify_high_priority_issues","METRIC_NAME_LOW":"fortify_low_priority_issues","RETRY_COUNT":"10","IS_ENABLED":true,"AKMID":"102827","API":"https://scan.appsec.corporate.t-mobile.com/requestScan","METRIC_NAME_MEDIUM":"fortify_medium_priority_issues","SLEEP_TIME_IN_SECONDS":"30","EVENT":"SCAN_REQUEST"}

        when:
            scanStatusUrl = file.initiateScan(serviceConfig['serviceId'], serviceConfig['domain'], serviceConfig['akmid'], serviceConfig['branch'], '123456788', repo, commitHash, scanData);
            println "scanStatusUrl: ${scanStatusUrl}"
           
        then:
            scanStatusUrl == 'https://scan.appsec.corporate.t-mobile.com/scanStatus/JAZZ_Serverless_platform-41f2a6954fb93b0988c0~master/123456'
           
    }

    def testDoScan() {
        println "TEST: testing Fortify Scan"
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./FortifyScanModule.groovy'))
            def commitHash = '1242344'
            JSON.setValueToPropertiesFile('commitSha', commitHash);

        when:
            file.doScan("jazz");
            
        then:
            commitHash == '1242344'
           
    }

}
