import spock.lang.*
import common.util.Json as JSON
import static common.util.Shell.sh as sh

class FirstSpecification extends Specification {

    def checkModuleFileExists() {
        println "TEST: checking ApigeeModule.groovy exists"
        given:
            def file;
        
        when:
            file = new File("./ApigeeModule.groovy")

        then:
            file.exists() == true
    }

    def testSetDefaultValues() {
        println "TEST: testing setDefaultValues"
        given:
            def shell = new GroovyShell()
            def file = shell.parse(new File('./ApigeeModule.groovy'))
            def configLoader = ["APIGEE":[
                "MAVEN": "apache-maven-3.3.9",
                "BUILD_VERSION": "1.0",
                "APIGEE_CRED_ID": "ApigeeforJazz",
                "ENABLED": true,
                "API_ENDPOINTS": [
                    "DEV": [
                        "MGMT_ORG": "t-test",
                        "SERVICE_HOSTNAME": "devtest.test.api.t-test.com",
                        "MGMT_ENV": "dev01",
                        "MGMT_HOST": "apitest.enterise.apigee.com"
                    ]
                ],
                "USE_SECURE": false
            ]]
        
            file.setDefaultValues(configLoader)

        when:
            def apigeeConfig = JSON.getValueFromPropertiesFile('apigeeConfig')
            def apiversion = JSON.getValueFromPropertiesFile('apiversion')
            def apigeeModuleRoot = JSON.getValueFromPropertiesFile('apigeeModuleRoot')
        then:
            apigeeConfig == configLoader.APIGEE
            apiversion == "1.0.1"
            apigeeModuleRoot == "ApigeeRepo"
    }

    def testDeploy() {
        println "TEST: testing deploy"
        given:
            def serviceConfig = [
                "enable_api_security": false,
                "deployment_targets": [
                    "api": "aws_apigateway"
                ],
                "providerRuntime": "nodejs10.x",
                "slack_channel": null,
                "policies": [
                    [
                        "permission": "write",
                        "category": "code"
                    ],
                    [
                        "permission": "admin",
                        "category": "manage"
                    ],
                    [
                        "permission": "write",
                        "category": "deploy"
                    ]
                ],
                "approvers": [
                    "RChetti1"
                ],
                "description": null,
                "is_public_endpoint": false,
                "repository": "[Archived]",
                "type": "api",
                "createdByEmail": "test@test.com",
                "scmManaged": true,
                "provider": "aws",
                "appId": "test",
                "id": "9431377c-39ba-ba74-3af4-0d2496a25065",
                "appTag": "test",
                "timestamp": "2020-05-17T17:22:12:022",
                "owner": "rchetti1",
                "providerMemorySize": "256",
                "appName": "test",
                "runtime": "nodejs10.x",
                "approvalTimeOutInMins": "5",
                "created_by": "rchetti1",
                "providerTimeout": "160",
                "require_internal_access": false,
                "create_cloudfront_url": false,
                "scmType": "gitlab",
                "service": "testgitlab",
                "akmId": "102827",
                "domain": "jazztest",
                "region": [
                    "us-west-2"
                ],
                "deployment_accounts": [
                    "dev": [
                        "L": [
                            [
                                "M": [
                                    "region": [
                                        "S": "us-west-2"
                                    ],
                                    "account": [
                                        "S": "302890901340"
                                    ]
                                ]
                            ]
                        ]
                    ]
                ],
                "status": "deletion_completed"
            ]
            JSON.setValueToPropertiesFile('serviceConfig', serviceConfig)
            def swaggerFile = "swagger.json"
            def arn = ["functionName": "abcdef"]
            def envKey = "DEV"
            def envLogicalId = "dev-123"
            def apiPath = "path://"

            def shell = new GroovyShell()
            def file = shell.parse(new File('./ApigeeModule.groovy'))

        when:
            def deployStatus = file.deploy(swaggerFile, arn, envKey, envLogicalId, apiPath)
        then:
            deployStatus != ""
    }
}
