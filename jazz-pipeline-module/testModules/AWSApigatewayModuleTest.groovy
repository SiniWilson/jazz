import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkApiGatewayModuleFileExists() {
        println "TEST: checking AWSApiGatewayModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./AWSApiGatewayModule.groovy")

        then:
            file.exists() == true
    }

    def testGetApiId() {
        println "TEST: testing GetApiId"
        given:
            def apiId
            def shell = new GroovyShell()
            def file = shell.parse(new File('./AWSApiGatewayModule.groovy'))
            def configData = ["AWS": ["ACCOUNTS": [["ACCOUNTID": "123456", "REGIONS": [[ "REGION":"us-west-2", "API_GATEWAY":[
                           "DEV":[
                              "PUBLIC":[
                                 "cap_*":[
                                    "DNS_HOSTNAME":"dev.cap01.jazz.t-mobile.com",
                                    "ID":"cpgcvkbiv3",
                                    "NAME":"dev.cap01"
                                 ],
                                 "*":[
                                    "DNS_HOSTNAME":null,
                                    "ID":"ed57fv1lyj",
                                    "AD_AUTHORIZER":"9qbw3z",
                                    "NAME":"dev.jazz.public"
                                 ],
                                 "svbot_*":[
                                    "DNS_HOSTNAME":"7z2xnmw5al.execute-api.us-west-2.amazonaws.com",
                                    "ID":"7z2xnmw5al",
                                    "NAME":"dev.svbot"
                                 ]
                              ],
                              "PRIVATE":[
                                 "jazztest_*":[
                                    "DNS_HOSTNAME":"6sjp9hlro6.execute-api.us-west-2.amazonaws.com",
                                    "ID":"6sjp9hlro6",
                                    "NAME":"dev.jazz.internal.jazz"
                                 ],
                                 "metalhead_*":[
                                    "DNS_HOSTNAME":"internal.dev.metalhead.jazz.t-mobile.com",
                                    "ID":"vp4uxwktd5",
                                    "NAME":"internal.dev.metalhead"
                                 ],
                                 "jazz_*":[
                                    "DNS_HOSTNAME":"dev-jazz.ccp.t-mobile.com",
                                    "ID":"6sjp9hlro6",
                                    "AD_AUTHORIZER":"p5m2mb",
                                    "NAME":"dev.jazz.internal.jazz"
                                 ],
                                 "clouddns_*":[
                                    "DNS_HOSTNAME":"internal.dev.clouddns.jazz.t-mobile.com",
                                    "ID":"n5ck2wzseg",
                                    "NAME":"internal.dev.clouddns"
                                 ],
                                 "*":[
                                    "DNS_HOSTNAME":"dev-cloud-api.corporate.t-mobile.com",
                                    "ID":"6zfek2hkof",
                                    "AD_AUTHORIZER":"9qbw3z",
                                    "NAME":"dev.jazz.internal"
                                 ],
                                 "jazz_apilinter":[
                                    "DNS_HOSTNAME":"internal.dev.apilinter.jazz.t-mobile.com",
                                    "ID":"gdrya3kk06",
                                    "NAME":"internal.dev.apilinter"
                                 ],
                                 "cap_*":[
                                    "DNS_HOSTNAME":"dev.cap01.jazz.t-mobile.com",
                                    "ID":"cpgcvkbiv3",
                                    "NAME":"dev.cap01"
                                 ],
                                 "svbot_*":[
                                    "DNS_HOSTNAME":"7z2xnmw5al.execute-api.us-west-2.amazonaws.com",
                                    "ID":"7z2xnmw5al",
                                    "NAME":"dev.svbot"
                                ]
                            ]
                        ]
            ]]]]]]]
            def serviceConfig = ['service': 'test-service', 'domain': 'jazztest', 'is_public_endpoint': false, 'account':'123456', 'region':'us-west-2']
            JSON.setValueToPropertiesFile('serviceConfig', serviceConfig);
            JSON.setValueToPropertiesFile('configData', configData);
		    def configs = configData.AWS.ACCOUNTS[0].REGIONS[0].API_GATEWAY

        when:
            apiId = file.GetApiId('dev', configs);
        
        then:
            apiId == "6sjp9hlro6"
           
    }

    def testGetApiGatewayInfo() {
        println "TEST: testing getApiGatewayInfo"
        given:
            def apiInfo
            def shell = new GroovyShell()
            def file = shell.parse(new File('./AWSApiGatewayModule.groovy'))
            
        when:
            apiInfo = file.getApiGatewayInfo('dev', 'DNS_HOSTNAME');
        
        then:
            apiInfo == "6sjp9hlro6.execute-api.us-west-2.amazonaws.com"
           
    }

}
