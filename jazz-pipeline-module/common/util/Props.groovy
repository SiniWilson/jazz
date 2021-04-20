package common.util

class Props {
    public static final int CLONE_DEPTH = 1;
    public static def WORKING_DIRECTORY = "WorkingDirectory"
    public static def WORKING_MODULE_DIRECTORY = "$WORKING_DIRECTORY/jazz-pipeline-module"
    public static def WORKING_TEMPLATE_DIRECTORY = "$WORKING_DIRECTORY/Templates"
    public static def PROPERTIES_FILE = "$WORKING_DIRECTORY/properties.json"  
    public static def SERVERLESS_CONFIG_DIRECTORY = "$WORKING_DIRECTORY/serverlessConfigRepo"
    public static def JAVA_CONFIG_DIRECTORY = "$WORKING_DIRECTORY/JavaConfigRepo"
    public static def AWS_APIGATEWAY_LAMBDA_INTEGRATION_DIRECTORY = "$WORKING_DIRECTORY/aws-apigateway-lambda-integration-spec"
    public static def LOGS_DIRECTORY = "$WORKING_DIRECTORY/PipelineLogs"
    public static def JAZZ_CONFIG_FILE = "JazzConfigDirectory/jazz-config.json"
}