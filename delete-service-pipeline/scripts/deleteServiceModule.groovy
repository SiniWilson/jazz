#!groovy?
import custom.sls.*
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.Yaml as YAML
import common.util.File as FILE
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder


/*
* deleteServiceModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Function to undeploy services of type API, LAMBDA and SLSAPP
*/
def undeployService() {
    println "deleteServiceModule.groovy: undeployService"

    def utilModule = new UtilityModule()
    utilModule.showDeleteEnvParams()

    def serviceType = JSON.getValueFromPropertiesFile('flowType')
    def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
    def Configs = JSON.getValueFromPropertiesFile('configData')

    def eventS3 = serviceConfig['event_source_s3']
    def repoName = JSON.getValueFromPropertiesFile('scmRepoName')
    def environmentIdValues = JSON.getValueFromPropertiesFile('environmentArray')
    println "environmentIdValues: $environmentIdValues"
    def envModule = new EnvironmentDeploymentMetadataLoader();
    def eventsModule = new EventsModule();
    def slackModule = new SlackModule()
    def env = System.getenv()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def resourcePath = JSON.getValueFromPropertiesFile('resourcePath')
    
    try {
        def AWS_KEY = env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET = env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION = env['AWS_DEFAULT_REGION']
        
        switch (serviceType) {
            case 'API':
                println "API service deletion"

                def path = getResourcePath()
                if(path == null && resourcePath != null){
                    path = resourcePath
                }
                if (path != null) {
                    def apiPlatform
                    if (serviceConfig.deployment_targets) {
                        /*
                        * if service metadata is retrieved using service metadata loader
                        */
                        apiPlatform = serviceConfig.deployment_targets.api;
                    } else {
                        /*
                        * Supporting older services with no service_id in their deployment-env.yml. Service metadata loader is not used in this case.
                        */
                        apiPlatform = 'aws_apigateway';
                    }

                    for (_envId in environmentIdValues) {
                        try {
                            JSON.setValueToPropertiesFile("environmentId", _envId)

                            def environmentLogicalId = envModule.getEnvironmentLogicalData(_envId)

                            envModule.setEnvLogicalId(_envId)
                            def branchName = envModule.getEnvBranchNameByGuid(_envId)
                            
                            if (branchName && branchName != 'NA') {
                                JSON.setValueToPropertiesFile('repoBranch', branchName)
                                jobContext['Branch'] = branchName
                                branchName = 'NA'
                            }
                            eventsModule.sendStartedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' started')
                            if (!checkIfDeploymentAccountExists(_envId)) {
                                // assumption is there are no assets, hence just need to archive the environment
                                eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                                continue;
                            }

                            _envId = environmentLogicalId

                            println "Undeploying environment: ${_envId}"
                            def regionValue = JSON.getValueFromPropertiesFile('region')
                            JSON.setValueToPropertiesFile('deploymentRegion', regionValue)
                            sh("sed -i -- 's/{region}/${regionValue}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")

                            def accountInfo = utilModule.getAccountInfo();

                            switch (apiPlatform) {
                                case 'aws_apigateway':
                                    def apigatewayArr = utilModule.getAPIGatewaybyRegion(regionValue)
                                    def api_id = getAPIId(_envId, apigatewayArr)
                                    JSON.setValueToPropertiesFile("apigatewayArr", apigatewayArr)
                                    JSON.setValueToPropertiesFile("apiId", api_id)
                                    // '/env_id/foo/bar' would be the path for dev environment, we need to delete everything under /env_id
                                    if (_envId != 'prod' && _envId != 'stg') {
                                        path = "/" + _envId
                                    }
                                    cleanUpApiGatewayResources(_envId, path, api_id, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                                    break
                                case 'gcp_apigee':
                                    def env_key = (_envId != 'prod' && _envId != 'stg') ? "DEV" : _envId.toUpperCase()
                                    def apigeeModule = new ApigeeModule()
                                    // need to check how to pass username and password
                                    apigeeModule.delete("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json", env_key, _envId, env.APIGEE_DEPLOY_USER, env.APIGEE_DEPLOY_PASSWORD)
                                    break
                                default:
                                    throw new Exception('Deployment platform not recognized.')
                            }

                            cleanUpApiDocs(_envId, AWS_KEY, AWS_SECRET, AWS_REGION)
                            unDeployServiceData(serviceConfig, _envId, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                            
                            // Check and Delete DNS and Cert
                            deleteCertAndDNSAssets('API', _envId, accountInfo, regionValue)
                            archiveAssetDetails('api', _envId)
                            eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                            if (env.ENVIRONMENT_ID != null) {
                                slackModule.sendSlackNotification('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed', 'COMPLETED', jobContext)
                            }
                        } catch(ex) {
                            /*
                            * Sending DELETE FAILED event only in case of service deletion
                            */
                            if(env.ENVIRONMENT_ID == null) {
                                JSON.setValueToPropertiesFile("environmentId", "NA")
                            }
                            eventsModule.sendFailureEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' failed! ' + ex.message)
                            println "Environment Deletion failed for envId: $_envId . Error Details: " + ex.message
                            ex.printStackTrace()
                            jobContext['EVENT'] = 'DELETE_ENVIRONMENT'
                            jobContext['Error Message'] = ex.message
                            slackModule.sendSlackNotification('DELETE_ENVIRONMENT', "Environment Deletion failed for envId: $_envId", 'FAILED', jobContext)
                            throw new Exception("Environment Deletion failed for envId: $_envId ", ex)
                        }
                    }
                }
                break

            case 'LAMBDA':
                println "LAMBDA service deletion"

                for (_envId in environmentIdValues) {
                    try {
                        JSON.setValueToPropertiesFile("environmentId", _envId)
                        def branchName = envModule.getEnvBranchNameByGuid(_envId)

                        def environmentLogicalId = envModule.getEnvironmentLogicalData(_envId)

                        println "branchName: $branchName"
                        if (branchName && branchName != 'NA') {
                            JSON.setValueToPropertiesFile('repoBranch', branchName)
                            jobContext['Branch'] = branchName
                            branchName = 'NA'
                        }
                        eventsModule.sendStartedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' started')
                        if (!checkIfDeploymentAccountExists(_envId)) {
                            // assumption is there are no assets, hence just need to archive the environment
                            eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                            continue;
                        }

                        _envId = environmentLogicalId
                        
                        println "Undeploying environment: ${_envId}"
                        def regionValue = JSON.getValueFromPropertiesFile('region')
                        JSON.setValueToPropertiesFile('deploymentRegion', regionValue)
                        sh("sed -i -- 's/{region}/${regionValue}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")

                        def accountInfo = utilModule.getAccountInfo();

                        if (serviceConfig['event_source_s3']) { // check service for S3 Bucket and make empty if not. //
                            checkBucket(_envId, serviceConfig['event_source_s3'], accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                        }
                        unDeployServiceData(serviceConfig, _envId, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                        
                        archiveAssetDetails('function', _envId)
                        eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                        if (env.ENVIRONMENT_ID != null) {
                            slackModule.sendSlackNotification('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed', 'COMPLETED', jobContext)
                        }
                    } catch (ex) {
                        /*
                        * Sending DELETE FAILED event only in case of service deletion
                        */
                        if(env.ENVIRONMENT_ID == null) {
                            JSON.setValueToPropertiesFile("environmentId", "NA")
                        }
                        eventsModule.sendFailureEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' failed! ' + ex.message)
                        println "Environment Deletion failed for envId: $_envId . Error Details:" + ex.message
                        ex.printStackTrace()
                        jobContext['EVENT'] = 'DELETE_ENVIRONMENT'
                        jobContext['Error Message'] = ex.message
                        slackModule.sendSlackNotification('DELETE_ENVIRONMENT', "Environment Deletion failed for envId: $_envId", 'FAILED', jobContext)
                        throw new Exception("Environment Deletion failed for envId: $_envId ", ex)
                    }
                }
                break
            case 'WEBSITE':
                println "WEBSITE service deletion"

                for (_envId in environmentIdValues) {
                    try {
                        JSON.setValueToPropertiesFile("environmentId", _envId)
                        def branchName = envModule.getEnvBranchNameByGuid(_envId)

                        def environmentLogicalId = envModule.getEnvironmentLogicalData(_envId)

                        if (branchName && branchName != 'NA') {
                            JSON.setValueToPropertiesFile('repoBranch', branchName)
                            jobContext['Branch'] = branchName
                            branchName = 'NA'
                        }
                        eventsModule.sendStartedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' started')
                        if (!checkIfDeploymentAccountExists(_envId)) {
                            // assumption is there are no assets, hence just need to archive the environment
                            eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                            continue;
                        }
                        
                        _envId = environmentLogicalId

                        println "Undeploying environment: ${_envId}"
                        def regionValue = JSON.getValueFromPropertiesFile('region')
                        JSON.setValueToPropertiesFile('deploymentRegion', regionValue)

                        def accountInfo = utilModule.getAccountInfo();

                        cleanupCloudFrontDistribution(_envId, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

                        unDeployWebsite(_envId, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

                        // Check and Delete DNS and Cert
                        deleteCertAndDNSAssets('WEBSITE', _envId, accountInfo, regionValue)
                        archiveAssetDetails('website', _envId)
                        eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                        if (env.ENVIRONMENT_ID != null) {
                            slackModule.sendSlackNotification('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed', 'COMPLETED', jobContext)
                        }
                    } catch (ex) {
                        /*
                        * Sending DELETE FAILED event only in case of service deletion
                        */
                        if(env.ENVIRONMENT_ID == null) {
                            JSON.setValueToPropertiesFile("environmentId", "NA")
                        }
                        eventsModule.sendFailureEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' failed! ' + ex.message)
                        println "Environment Deletion failed for envId: $_envId . Error Details:" + ex.message
                        ex.printStackTrace()
                        jobContext['EVENT'] = 'DELETE_ENVIRONMENT'
                        jobContext['Error Message'] = ex.message
                        slackModule.sendSlackNotification('DELETE_ENVIRONMENT', "Environment Deletion failed for envId: $_envId", 'FAILED', jobContext)
                        throw new Exception("Environment Deletion failed for envId: $_envId ", ex)
                    }
                }
                break
            case 'SLSAPP':
                println "SLSAPP service deletion"

                for (_envId in environmentIdValues) {
                    try {
                        JSON.setValueToPropertiesFile("environmentId", _envId)
                        def branchName = envModule.getEnvBranchNameByGuid(_envId)

                        def environmentLogicalId = envModule.getEnvironmentLogicalData(_envId)

                        println "branchName: $branchName"
                        if (branchName && branchName != 'NA') {
                            JSON.setValueToPropertiesFile('repoBranch', branchName)
                            jobContext['Branch'] = branchName
                            branchName = 'NA'
                        }
                        eventsModule.sendStartedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' started')
                        if (!checkIfDeploymentAccountExists(_envId)) {
                            // assumption is there are no assets, hence just need to archive the environment
                            eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                            continue;
                        }

                        _envId = environmentLogicalId
                        
                        println "Undeploying environment: ${_envId}"
                        def hasServerlessFile = loadServerlessYml(serviceConfig, _envId, repoName)

                        println "Undeploying environment: ${_envId}"

                        def regionValue = JSON.getValueFromPropertiesFile('region')
                        JSON.setValueToPropertiesFile('deploymentRegion', regionValue)

                        def accountInfo = utilModule.getAccountInfo();
                        
                        if (hasServerlessFile) {
                            sh("sed -i -- 's/{region}/${regionValue}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
                            installServerlessPlugins(repoName)
                            unDeployServiceData(serviceConfig, _envId, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                        } else {
                            deleteCloudformationStack(serviceConfig, _envId, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                        }


                        archiveAssetDetails('custom', _envId)
                        eventsModule.sendCompletedEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed')
                        if (env.ENVIRONMENT_ID != null) {
                            slackModule.sendSlackNotification('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' completed', 'COMPLETED', jobContext)
                        }
                    } catch (ex) {
                        /*
                        * Sending DELETE FAILED event only in case of service deletion
                        */
                        if(env.ENVIRONMENT_ID == null) {
                            JSON.setValueToPropertiesFile("environmentId", "NA")
                        }
                        eventsModule.sendFailureEvent('DELETE_ENVIRONMENT', 'Environment cleanup for ' + _envId + ' failed! ' + ex.message)
                        println "Environment Deletion failed for envId: $_envId . Error Details:" + ex.message
                        ex.printStackTrace()
                        jobContext['EVENT'] = 'DELETE_ENVIRONMENT'
                        jobContext['Error Message'] = ex.message
                        slackModule.sendSlackNotification('DELETE_ENVIRONMENT', "Environment Deletion failed for envId: $_envId", 'FAILED', jobContext)
                        throw new Exception("Environment Deletion failed for envId: $_envId ", ex)
                    }
                }
                break
        }
    } catch (ex) {
        /*
        * Sending DELETE FAILED event only in case of service deletion
        */
        if(env.ENVIRONMENT_ID == null) {
            JSON.setValueToPropertiesFile("environmentId", "NA")
        }
        println "Something went wrong while undeploying service " + ex.message
        throw new Exception ("Something went wrong while undeploying service ", ex)
    }
}

/*
* Clean up the API gateway resource configurations specific to the service
* @param environment
* @param path the resource path
* @param api_id
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
 */
def cleanUpApiGatewayResources(environment, path, api_id, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "In deleteServiceModule.groovy: cleanUpApiGatewayResources"

    def utilModule = new UtilityModule()
    def eventsModule = new EventsModule();
    def apiGatewayModule = new AWSApiGatewayModule()
    def slackModule = new SlackModule()
    def credsId = null
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    
    eventsModule.sendStartedEvent('DELETE_API_RESOURCE', null)
    try {
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        def resourceId = apiGatewayModule.findResourceId(api_id, path, credsId)
        if (resourceId != null && resourceId != '') {
            apiGatewayModule.deleteApiGatewayResources(api_id, resourceId, getStage(environment), credsId)
            eventsModule.sendCompletedEvent('DELETE_API_RESOURCE', null)
        } else {
            println 'Resource Id does not exists in API gateway.'
            eventsModule.sendCompletedEvent('DELETE_API_RESOURCE', 'No Resource to be deleted')
        }

    } catch (ex) {
        eventsModule.sendFailureEvent('DELETE_API_RESOURCE', ex.message)
        jobContext['EVENT'] = 'DELETE_API_RESOURCE'
        jobContext['Error Message'] = 'cleanUpApiGatewayResources failed! ' + ex.message
        slackModule.sendSlackNotification('DELETE_API_RESOURCE', 'cleanUpApiGatewayResources failed! ', 'FAILED', jobContext)
        throw new Exception("Error in cleanUpApiGatewayResources ", ex);
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Function to return stage based on environment
* @param environment
*/
def getStage(environment) {
    if (!environment.equals('stg') && !environment.equals('prod')) {
        return 'dev'
    } else {
        return environment
    }
}

/**
* Function to install serverless plugin
* @param repoName
*/
def installServerlessPlugins(repoName) {
    println "In deleteServiceModule.groovy: installServerlessPlugins"

    try {
        def whiteListModule = new WhiteListValidatorModule()
        def serverlessyml = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        println "serverlessyml- $serverlessyml - ${serverlessyml.getClass().name}"
        def outstandingPlugins = whiteListModule.validatePlugins(serverlessyml)
        println "outstandingPlugins- $outstandingPlugins"

        if(outstandingPlugins.isEmpty()) {
            def plugins = whiteListModule.getPluginsfromYaml(serverlessyml)
            println "plugins: $plugins"
            if( plugins ) {
                for (plugin in plugins){
                    sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};npm install ${plugin}")
                }
            } else {
                println "No plugins listed..skipping"
            }
        } else {
            throw new Exception( "The following plugins are not allowed: ${outstandingPlugins}")
        }
    } catch (ex) {
        println "Plugin Installation Failed: ${ex.message}"
        throw new Exception( "Plugin Installation Failed ", ex)
    }
}

/**
* Function to load serverlessYml file
* @param config
* @param environment
* @param repoName
*/
def loadServerlessYml(config, env, repoName) {
    println "In deleteServiceModule.groovy: loadServerlessYml"

    JSON.setValueToPropertiesFile('REPO_NAME', repoName);
    // copy serverless.yml from codebase to application.yml if it exists before it is overwritten and always returns true
    sh("cp ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml ${PROPS.WORKING_DIRECTORY}/${repoName}/application.yml || true")

    try {
        println "build rules..."
        def slsBuildRules = new sbr()
        def deploymentDescriptor = slsBuildRules.prepareServerlessYml() // Generating the deployment descriptor
        println "prepareServerlessYml => ${deploymentDescriptor}"
        sh("rm -rf ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        YAML.writeYaml("${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml", deploymentDescriptor)
        println sh("cat ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
        return true
    } catch (ex) {
        println "Failed to load the serverless.yml for ${env} environment."
        ex.printStackTrace()
        return false
    }
}


/**
* Archive the assets of a service
* @param serviceType
* @param environment
*/
def archiveAssetDetails(serviceType, env) {
    println "In deleteServiceModule.groovy: archiveAssetDetails"

    def eventsModule = new EventsModule()
    def serviceConfig = JSON.getValueFromPropertiesFile("serviceConfig")
    
    def assetList = getAssets(env)
    for (asset in assetList.data.assets) {
        eventsModule.sendCompletedEvent('UPDATE_ASSET', "Environment cleanup for ${env} completed", generateAssetMap(serviceType, asset.provider, asset.provider_id , asset.type,  serviceConfig['owner']))
    }
}

/*
* Generate asset map
* @param serviceType
* @param provider
* @param providerId
* @param type
* @param created_by
*/
def generateAssetMap(serviceType, provider, providerId, type, created_by) {
    println "In deleteServiceModule.groovy: generateAssetMap"

    def serviceCtxMap = [
        status: 'archived',
        service_type: serviceType,
        provider: provider,
        provider_id: providerId,
        type: type,
        created_by: created_by
    ]
    return serviceCtxMap
}

/**
* Undeploy the service
* @param config
* @param environment
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def unDeployServiceData(config, environment, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy: unDeployServiceData"
    // aws configure
    def repoName = JSON.getValueFromPropertiesFile('scmRepoName')
    def eventsModule = new EventsModule()
    def utilModule = new UtilityModule()
    def slackModule = new SlackModule()
    def Configs = JSON.getValueFromPropertiesFile('configData')
    eventsModule.sendStartedEvent('UNDEPLOY_LAMBDA', null)
    def credsId = null
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def envs = System.getenv()

    try {
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        def s3DeploymentBuckets = utilModule.getAccountBucketName()
        println "s3DeploymentBuckets: $s3DeploymentBuckets"
        println "environment: $environment"
        def deploymentBucket
        if (s3DeploymentBuckets) {
            if (environment == 'prod') {
                deploymentBucket = s3DeploymentBuckets['PROD']
            } else if (environment == 'stg') {
                deploymentBucket = s3DeploymentBuckets['STG']
            } else {
                deploymentBucket = s3DeploymentBuckets['DEV']
            }
            
            if (config.type?.equals('sls-app') ) {
                setDeploymentBucketNameForSlsapp(deploymentBucket, repoName)
            } else {
                setDeploymentBucketName(deploymentBucket, repoName)
            }
            try {
                sh("cd ${PROPS.WORKING_DIRECTORY}/${repoName};serverless remove --stage ${environment} --aws-profile ${credsId} >  ../../${PROPS.LOGS_DIRECTORY}/${envs.CI_JOB_NAME}_serverless-remove_${environment}.log 2>&1" )
            } catch (ex) {
                println "serverless remove failed"
                // GO FOR CF DELETE
                def cfStackName = "${config['service']}--${config['domain']}-${environment}"
                if (config.type?.equals('sls-app') ) {
                    cfStackName = "${Configs.INSTANCE_PREFIX}-${config['domain']}--${config['service']}-${environment}"
                }
                utilModule.deletecfstack(credsId, cfStackName, regionValue)
            }
            println 'Service undeployed'
        } else {
            eventsModule.sendFailureEvent('UNDEPLOY_LAMBDA', 'Unable to find Deployment Buckets')
            jobContext['EVENT'] = 'UNDEPLOY_LAMBDA'
            jobContext['Error Message'] = 'Unable to find Deployment Buckets'
            slackModule.sendSlackNotification('UNDEPLOY_LAMBDA', 'Unable to find Deployment Buckets', 'FAILED', jobContext)
            throw new Exception('Unable to find Deployment Buckets')
        }
        eventsModule.sendCompletedEvent('UNDEPLOY_LAMBDA', null)
    } catch(ex) {
        println "error: " + ex.message
        eventsModule.sendFailureEvent('UNDEPLOY_LAMBDA', ex.message)
        jobContext['EVENT'] = 'UNDEPLOY_LAMBDA'
        jobContext['Error Message'] = ex.message
        slackModule.sendSlackNotification('UNDEPLOY_LAMBDA', ex.message, 'FAILED', jobContext)
        throw new Exception("Error in undeployServiceData ", ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

def setDeploymentBucketName(deploymentBucket, repoName) {
    println "deleteServiceModule.groovy: setDeploymentBucketName"

    sh("sed -i -- 's/{s3bucketValue}/${deploymentBucket}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}

def setDeploymentBucketNameForSlsapp(deploymentBucket, repoName) {
    println "deleteServiceModule.groovy: setDeploymentBucketNameForSlsapp"

    sh("sed -i -- 's/\${opt:bucket}/${deploymentBucket}/g' ${PROPS.WORKING_DIRECTORY}/${repoName}/serverless.yml")
}


/**
* Get the API Id of the gateway specific to an environment. The value will be retrieved from environments table and if not available will try to retrieve it from config
* @param  environment
* @param  filtered apigateway config data
*/
def getAPIId(_envId, apigatewayArr) {
    println "deleteServiceModule.groovy: getAPIId"

    def envModule = new EnvironmentDeploymentMetadataLoader()
    def apiGatewayModule = new AWSApiGatewayModule()

    def envInfo = envModule.getEnvironmentInfo()
    def envMetadata = [:]

    if (envInfo && envInfo['metadata']) {
        envMetadata = envInfo['metadata']
    }
    if (envMetadata['AWS_API_ID'] != null) {
        return envMetadata['AWS_API_ID']
    }
    envMetadata = apiGatewayModule.getApiId(_envId, apigatewayArr)
    return envMetadata
}

/*
* Gets target deployment_account information for the environment.
*/
def getEnvironmentDeploymentAccount() {
    println "deleteServiceModule.groovy: getEnvironmentDeploymentAccount"

    def envModule = new EnvironmentDeploymentMetadataLoader()
    def environmentInfo =  envModule.getEnvironmentInfo(true)
    return environmentInfo['deployment_accounts']
}

/*
* Checks if valid deployment_account information exists for the environment.
* Also sets this information to global variables for further use.
* @param env
*/
def checkIfDeploymentAccountExists(env) {
    println "deleteServiceModule.groovy: checkIfDeploymentAccountExists"

    def utilModule = new UtilityModule()
    def deploymentAccounts = getEnvironmentDeploymentAccount()
    def retVal = false
    try {
        if (deploymentAccounts == null) {
            println "Missing deployment account configuration for environment: ${env} or account configurations not found in Jazz"
        } else {
            if (deploymentAccounts.isEmpty()) {
                println "Missing deployment account configuration for environment: ${env} or account configurations not found in Jazz"
            } else {
                if (deploymentAccounts[0].containsKey('region') && deploymentAccounts[0].containsKey('account')) {
                    
                    def regionData = deploymentAccounts[0].region
                    JSON.setValueToPropertiesFile('region', regionData)
                    def config = JSON.getValueFromPropertiesFile('serviceConfig')
                    config['account'] = deploymentAccounts[0].account
                    config['region'] = regionData
                    JSON.setValueToPropertiesFile('serviceConfig', config)

                    def accountDetails = utilModule.getAccountInfo();
                
                    if (accountDetails) {
                        JSON.setValueToPropertiesFile('accountDetails', accountDetails)
                        credentialId = accountDetails.CREDENTIAL_ID
                        retVal = true
                        println "Found account configuration, credentialId: ${credentialId} & target region: ${regionData}"
                    }
                    
                } else {
                    println "Missing deployment account configuration for environment: ${env} or account configurations not found in Jazz"
                }
            }
        }
    } catch (ex) {
        println "Missing deployment account configuration for environment: ${env} or account configurations not found in Jazz: " + ex.message
        ex.printStackTrace()
    }
    return retVal
}

/*
* Get the resource Path from domain and service name.
* @return  formed resource path string
*/
def getResourcePath() {
    println "deleteServiceModule.groovy: getResourcePath"

    def repoName = JSON.getValueFromPropertiesFile('scmRepoName')
    def basePath
    def pathInfo
    def resourcepath = null
    try {
        if (JSON.isFileExists("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")) {
            def swaggerStr = JSON.readFile("${PROPS.WORKING_DIRECTORY}/${repoName}/swagger/swagger.json")
            def swaggerJsonObj = swaggerStr
            basePath = swaggerJsonObj.basePath
            def keys = swaggerJsonObj.paths.keySet()
            for (_p in keys) {
                pathInfo = _p
                break
            }
            resourcepath = (basePath + '/' + pathInfo).replaceAll('//','/')
        }
        return resourcepath
    } catch (ex) {
        println 'getResourcePath Failed. ' + ex.message
        ex.printStackTrace()
        return null
    }
}

/**
* Clean up the API documentation folder from S3 corresponding to the environment
* @param  environment
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def cleanUpApiDocs(environment, AWS_KEY, AWS_SECRET, AWS_REGION){
    println "deleteServiceModule.groovy:cleanUpApiDocs"

    def utilModule = new UtilityModule()
    def slackModule = new SlackModule()
    def serviceName = JSON.getValueFromPropertiesFile('serviceName')
    def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
    def credsId = null    
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')

    try {
        /*
        * configuring primary aws account
        */
        credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)

        def apiRootFolder = getApiDocsFolder(environment)
        def servicePath = serviceName
        if (serviceDomain != null && serviceDomain != '') {
            servicePath = serviceDomain + '_' + serviceName
        }
        sh("aws s3 rm s3://$apiRootFolder/$servicePath --recursive --profile ${credsId}")
    } catch(ex) {
        jobContext['EVENT'] = 'DELETE_API_DOC'
        jobContext['Error Message'] = 'cleanUpApiDocs failed! ' + ex.message
        slackModule.sendSlackNotification('DELETE_API_DOC', ex.message, 'FAILED', jobContext)
        println 'cleanUpApiDocs Failed. ' + ex.message
        ex.printStackTrace()
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Get the API docs folder for environment
* @param environment
*/
def getApiDocsFolder(environment) {
    println "deleteServiceModule.groovy:getApiDocsFolder"

    if (environment && environment.equals('stg')) {
        return 'stg-cloud-api-doc.corporate.t-mobile.com'
    } else if (environment && environment.equals('prod')) {
        return 'cloud-api-doc.corporate.t-mobile.com'
    } else {
        return 'dev-cloud-api-doc.corporate.t-mobile.com'
    }
}

/**
* Check S3 bucket
* @param environment
* @param bucketName
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def checkBucket(environment, bucketName, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy:checkBucket"

    // aws configure
    def utilModule = new UtilityModule()
    def credsId = null
    try {
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        def targetBucket = bucketName
        if (environment != 'prod') {
            targetBucket = bucketName + '-' + environment
        }
        def s3Exists = true
        try {
            sh("aws s3api head-bucket --bucket ${targetBucket} --output json --profile ${credsId}")
        } catch (ex) {
            println 'Bucket does not exist'
            ex.printStackTrace()
            s3Exists = false
        }
        if (s3Exists && (checkIfBucketHasObjects(targetBucket, credsId))) {
            sh("aws s3 rm s3://${targetBucket}/ --recursive --exclude '/' --profile ${credsId}")
            println 'Removing items from bucket'
        }
    } catch (ex) {
        throw new Exception("Something went wrong while checking S3 bucket", ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Function to check if s3 bucket has objects
* @param bucketName
* @param credsId
*/
def checkIfBucketHasObjects(bucketName, credsId) {
    println "deleteServiceModule.groovy:checkIfBucketHasObjects"

    def status = true;
    try {
        sh("aws s3api list-objects --bucket $bucketName --profile ${credsId} --output json --query '[length(Contents[])]'")
    } catch (ex) {
        println "Bucket $bucketName is empty"
        ex.printStackTrace()
        status = false
    }
    return status
}

/**
* Delete the the cloud Front policies related to the service folder
* @param environment
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def cleanupCloudFrontDistribution(environment, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
     println "deleteServiceModule.groovy:cleanupCloudFrontDistribution"

    // aws configure
    def utilModule = new UtilityModule()
    def eventsModule = new EventsModule()
    def slackModule = new SlackModule()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def credsId = null

    try {
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        def service = JSON.getValueFromPropertiesFile('serviceName')
        def domain = JSON.getValueFromPropertiesFile('serviceDomain')

        def distributionID
        def _Etag
        service = domain + '_' + service
        distributionID = getDistributionId(environment, service, credsId)
        def distributionConfig = getDistributionConfig(distributionID, credsId)
        _Etag = generateDistributionConfigForDisable(distributionConfig)
        _Etag = disableCloudFrontDistribution(distributionID, _Etag, 'disable-cf-distribution-config.json', environment, credsId)
    } catch (ex) {
        if ((ex.message).indexOf('getDistributionId Failed') > -1) {
            println "Could not find a CloudFront distribution Id"
            eventsModule.sendCompletedEvent('DELETE_CLOUDFRONT', 'CF resource not available')
        } else {
            jobContext['EVENT'] = 'DELETE_CLOUDFRONT'
            jobContext['Error Message'] = 'cleanupCloudFrontDistribution failed! ' + ex.message
            slackModule.sendSlackNotification('DELETE_CLOUDFRONT', 'cleanupCloudFrontDistribution failed! ', 'FAILED', jobContext)
            throw new Exception('cleanupCloudFrontDistribution Failed. ', ex)
        }
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/**
 * Get the CloudFront distribution Id corresponding to the service
 * @param stage
 * @param service
 * @param credsId
 */
def getDistributionId(stage, service, credsId) {
    println "deleteServiceModule.groovy:getDistributionId"

    def slackModule = new SlackModule()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def distributionID
    def outputStr
    try {
        outputStr = sh("aws cloudfront list-distributions --profile ${credsId} --output json --query \"DistributionList.Items[?Origins.Items[?Id=='$stage-static-website-origin-$service']].{Distribution:DomainName, Id:Id}\"")
        outputObj = JSON.jsonParse(outputStr)
        
        if (outputObj.size() > 0) {
            println "List Distribution, parsed output: $outputObj"
            distributionID = outputObj[0].Id
        }

        if (distributionID == null || distributionID == '') {
            throw new Exception("getDistributionId Failed. 'distributionID' is null")
        }
        return distributionID
    }catch (ex) {
        jobContext['Error Message'] = 'getDistributionId Failed. ' + ex.message
        slackModule.sendSlackNotification('GET_DISTRIBUTION', ex.message, 'FAILED', jobContext)
        println 'getDistributionId Failed. ' + ex.message
        throw new Exception('getDistributionId Failed. ', ex)
    }
}

/*
* Get CloudFront distribution Config corresponding to the service
* @param distributionID
* @param credsId
*/
def getDistributionConfig(distributionID, credsId) {
    println "deleteServiceModule.groovy:getDistributionConfig"

    def slackModule = new SlackModule()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def distributionConfig
    try {
        distributionConfig = sh("aws cloudfront get-distribution-config --output json --id ${distributionID} --profile ${credsId}")
        distributionConfig = JSON.parseJson(distributionConfig)
        return distributionConfig
    }catch (ex) {
        jobContext['Error Message'] = 'getDistributionConfig Failed. ' + ex.message
        slackModule.sendSlackNotification('GET_DISTRIBUTION_CONFIG', ex.message, 'FAILED', jobContext)
        println 'getDistributionConfig Failed.' + ex.message
        throw new Exception('getDistributionConfig Failed.', ex)
    }

}

/*
* Generate Disable Distribution configuration
* @param distributionConfig
*/
def generateDistributionConfigForDisable(distributionConfig) {
    println "deleteServiceModule.groovy:generateDistributionConfigForDisable"

    def slackModule = new SlackModule()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    def distributionConfigObj
    def eTag
    try {
        eTag = distributionConfig.ETag
        distributionConfig.DistributionConfig.Enabled = false
        def updatedCfg = JSON.objectToJsonString(distributionConfig.DistributionConfig)
        println "updatedCfg... $updatedCfg"
        try {
            sh("echo \'$updatedCfg\' > disable-cf-distribution-config.json")
        } catch (ex) {
            throw new Exception('Error occurerd in generateDistributionConfigForDisable while creating config file ', ex)
        }

        return eTag
    } catch (ex) {
        jobContext['Error Message'] = 'generateDistributionConfigForDisable Failed. ' + ex.message
        slackModule.sendSlackNotification('GENERATE_DISTRIBUTION_CONFIG', ex.message, 'FAILED', jobContext)
        println 'Error occurerd in generateDistributionConfigForDisable ' + ex.message
        throw new Exception('generateDistributionConfigForDisable Failed.', ex)
    }
}

/**
* Disable Distribution configuration
* @param distributionID
* @param _Etag
* @param configFile
* @param environment
* @param credsId
*/
def disableCloudFrontDistribution(distributionID, _Etag, configFile, environment, credsId) {
    println "deleteServiceModule.groovy:disableCloudFrontDistribution"

    def eventsModule = new EventsModule()
    def slackModule = new SlackModule()
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')
    
    eventsModule.sendStartedEvent('DISABLE_CLOUDFRONT', 'distributionID: ' + distributionID)
    def disableOutput
    def eTag
    try {
        disableOutput = sh("aws cloudfront update-distribution --output json --id $distributionID --distribution-config file://"+configFile+" --if-match $_Etag --profile ${credsId}")
        println "disableOutput... $disableOutput"
        
        if (disableOutput) {
            def disableConfigObj = JSON.parseJson(disableOutput)
            eTag = disableConfigObj.ETag
        }
        println "disable eTag...$eTag"
        eventsModule.sendCompletedEvent('DISABLE_CLOUDFRONT', 'distributionID: ' + distributionID)
        return eTag
    }catch (ex) {
        jobContext['EVENT'] = 'DISABLE_CLOUDFRONT'
        jobContext['Error Message'] = 'disableCloudFrontDistribution failed! ' + ex.message
        slackModule.sendSlackNotification('DISABLE_CLOUDFRONT_DISTRIBUTION', ex.message, 'FAILED', jobContext)
        eventsModule.sendFailureEvent('DISABLE_CLOUDFRONT', 'disableCloudFrontDistribution failed for distributionID: ' + distributionID)
        println 'disableCloudFrontDistribution. ' + ex.message
        throw new Exception('disableCloudFrontDistribution. ', ex)
    }
}

/**
* Undeploy the website. Delete the web folder from S3 bucket
* @param environment
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def unDeployWebsite(environment, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy:unDeployWebsite"

    // aws configure
    def eventsModule = new EventsModule()
    def utilModule = new UtilityModule()
    def serviceMetadataModule = new ServiceMetadataLoader()
    def slackModule = new SlackModule()
    def credsId = null
    def jobContext = JSON.getValueFromPropertiesFile('jobContext')

    eventsModule.sendStartedEvent('UNDEPLOY_WEBSITE', null)
    try{
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        def bucketName = serviceMetadataModule.getS3BucketNameForService(credsId)
        println "bucketName: $bucketName"
         if (bucketName) {
            if (bucketName.endsWith('serverless-static-website')) {
                /*
                * For backward compatibility, skip the above old buckets.
                */
            } else {
                if (environment) {
                    cleanupS3BucketPolicy(environment, bucketName, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
                    def env = bucketName + '/' + environment
                    println "Cleaning the environment folder within the S3 bucket $env"
                    sh("aws s3 rm s3://$env --recursive --profile ${credsId}")
                }

                def wsExists = checkIfWebsiteExists(bucketName, credsId)
                println "wsExists: $wsExists"
                if (wsExists && !checkIfBucketHasObjects(bucketName, credsId)) {
                    println "Cleaning up bucket: $bucketName"
                    sh("aws s3 rb s3://$bucketName --force --profile ${credsId}")
                }
            }
        }
        eventsModule.sendCompletedEvent('UNDEPLOY_WEBSITE', null)
    } catch(ex) {
        eventsModule.sendFailureEvent('UNDEPLOY_WEBSITE', ex.message)
        println 'unDeployWebsite failed! ' + ex.message
        jobContext['EVENT'] = 'UNDEPLOY_WEBSITE'
        jobContext['Error Message'] = 'unDeployWebsite failed! ' + ex.message
        slackModule.sendSlackNotification('UNDEPLOY_WEBSITE', ex.message, 'FAILED', jobContext)
        throw new Exception('unDeployWebsite failed! ', ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Delete the the bucket policies related to the service folder
* @param environment
* @param bucketName
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def cleanupS3BucketPolicy(environment, bucketName, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy:cleanupS3BucketPolicy"

    def utilModule = new UtilityModule()
    def credsId = null

    try {
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        def bucketPolicy = sh("aws s3api get-bucket-policy --bucket $bucketName --output json --profile ${credsId}")
        
        def policyObject = JSON.parseJson(JSON.parseJson(bucketPolicy).Policy)
        def policyObjectUpdated = [:]
        policyObjectUpdated.Version = policyObject.Version
        policyObjectUpdated.Id = policyObject.Id
        def statements = []
        for (items in policyObject.Statement) {
            if (items.Resource != "arn:aws:s3:::$bucketName/$environment/*") {
                def copy = [:]
                copy.putAll(items)
                statements.add(copy)
            }
        }

        println "Updated policy: $statements"
        if (statements.size() > 0) {
            policyObjectUpdated.Statement = statements
            def policyJson = JSON.objectToJsonString(policyObjectUpdated)
            updateBucketPolicy(policyJson, bucketName, credsId)
        }
    } catch (ex) {
        println 'Bucket policy update failed. ' + ex.message
        throw new Exception('Bucket policy update failed. ', ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Function to update s3 bucket policy
* @param policyJson
* @param bucketName
* @param credsId
*/
def updateBucketPolicy(policyJson, bucketName, credsId) {
    println "deleteServiceModule.groovy:updateBucketPolicy"

    try {
        sh("aws s3api put-bucket-policy --profile ${credsId} --output json --bucket ${bucketName} --policy \'${policyJson}\'")
    } catch (ex) {
        throw new Exception('Exception occurred while updating bucket policy. Error: ', ex)
    }
}

/*
* Check if the website folder existing in the S3 buckets for each environments
* @param bucketName
* @param credsId
*/
def checkIfWebsiteExists(bucketName, credsId) {
    println "deleteServiceModule.groovy:checkIfWebsiteExists"

    def status = true;
    try {
        sh("aws s3 ls s3://$bucketName --profile ${credsId}")
    } catch (ex) {
        println 'Bucket does not exists'
        ex.printStackTrace()
        status = false
    }
    return status
}

/*
* Function to delete cloudformationStack
* @param config
* @param env
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def deleteCloudformationStack(config, env, accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy:deleteCloudformationStack"
    println 'Manualy removing the stack by using aws cli.'

    // aws configure
    def utilModule = new UtilityModule()
    def Configs = JSON.getValueFromPropertiesFile('configData')
    def credsId = null

    def cfStackName = "${Configs.INSTANCE_PREFIX}-${config['domain']}--${config['service']}-${env}"
    try {
        credsId = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
        def res = sh("aws cloudformation delete-stack --stack-name ${cfStackName} --region ${regionValue} --profile ${credsId}")
        println "Delete stack resp: ${res}"
    } catch (ex) {
        println "Error occured while deleting cloudformation stack ${cfStackName}" : ex.message
        throw new Exception("Error occured while deleting cloudformation stack ${cfStackName}", ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Function to get assets with serviceName and domain
* @param env
*/
def getAssets(env) {
    println "deleteServiceModule.groovy:getAssets"

    def authToken = JSON.getValueFromPropertiesFile('authToken')
    def serviceName = JSON.getValueFromPropertiesFile('serviceName')
    def serviceDomain = JSON.getValueFromPropertiesFile('serviceDomain')
    def serviceId = JSON.getValueFromPropertiesFile('serviceId')
    def Configs = JSON.getValueFromPropertiesFile('configData')
    def assetsUrl = "https://${Configs.AWS.API.HOST_NAMES.PROD}/api/jazz/assets"
    def assets
    try {
        assets = sh("curl GET -H 'Content-Type: application/json' -H 'Jazz-Service-ID: ${serviceId}' -H 'Authorization: $authToken' '${assetsUrl}?domain=${serviceDomain}&service=${serviceName}&environment=${env}'")
        assets = JSON.parseJson(assets)
        println "Asset details for the service: ${serviceName} and domain: ${serviceDomain} : \n $assets"
    } catch (ex) {
        println "Exception occured while getting the assets. ${ex.message}"
        ex.printStackTrace()
    }
    return assets
}

/*
* Function to generate Creds based on account
* @param accountInfo
* @param regionValue
* @param AWS_KEY
* @param AWS_SECRET
* @param AWS_REGION
*/
def generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION) {
    println "deleteServiceModule.groovy:generateCreds"

    def utilModule = new UtilityModule();
    def credsId = null;
    def deploymentAccountCreds = null;

    credsId = utilModule.generateCreds(AWS_KEY, AWS_SECRET, AWS_REGION);
    /*
    * Getting temporary credentials for cross account role if not primary account
    */
    deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, AWS_REGION);
    return deploymentAccountCreds
}

/**
 * Check if a service has dns_record and certificate as assets and delete it
 * @param endpoint_type
 * @param environmentIds
 * @param accountInfo
 * @param regionValue
 */

def deleteCertAndDNSAssets(endpoint_type, environmentIds, accountInfo, regionValue) {
    println "deleteServiceModule.groovy:deleteCertAndDNSAssets"

    def credsId = null
    def awsAccountCreds = null
    def serviceMetadataModule = new ServiceMetadataLoader()
    def acmModule = new AWSAcmModule()
    def route53Module = new AWSRoute53Module()
    def apiGatewayModule = new AWSApiGatewayModule()
    def utilModule = new UtilityModule()
    try {
        def env = System.getenv()
        def AWS_KEY = env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET = env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION = env['AWS_DEFAULT_REGION']

        def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
        def Configs = JSON.getValueFromPropertiesFile("configData")
        def props = JSON.getAllProperties()

        def getDNSAssetList = serviceMetadataModule.loadAssetInfo('dns_record', environmentIds)
        def getCertAssetList = serviceMetadataModule.loadAssetInfo('certificate', environmentIds)
        def currentFqdnValue = ''
        def endpoint
        def endpointConfType
        awsAccountCreds = generateCreds(accountInfo, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)

        try {
            /*
            * Assuming temporary role to access route53 services
            */
            credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
            credsId = route53Module.assumeTempRole(credsId, AWS_REGION)
            if(endpoint_type == 'API'){
                if(environmentIds != 'stg' && environmentIds != 'prod') {
                    endpointConfType = getEndpointConfigType(props["apiId"], regionValue, awsAccountCreds)
                } else {
                    def envApiId = getAPIId(environmentIds, props["apigatewayArr"])
                    println "envApiId: $envApiId"
                    endpointConfType = getEndpointConfigType(envApiId, regionValue, awsAccountCreds)
                }
            }
            def recordZoneId = route53Module.getHostedZoneId(regionValue, endpointConfType, serviceConfig['type'])
            println "recordZoneId: $recordZoneId"
            def zoneId

            if (getDNSAssetList.size() != 0) {
                for (eachAsset in getDNSAssetList) {
                    if (eachAsset.status != "archived") {
                        if(eachAsset.metadata.isPublicEndpoint == false){
                            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PRIVATEZONE
                        } else {
                            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE
                        }
                        endpoint = eachAsset.metadata.endpoint
                        endpoint = endpoint.replace('https://', '')
                        route53Module.deleteDNSRecord(eachAsset.metadata.fqdn, zoneId, recordZoneId, endpoint, credsId)
                    }
                }
            }

        } catch(ex){
            println "Something went wrong while deleting the record " + ex
            throw new Exception("Something went wrong while deleting the record ", ex)
        } finally {
            utilModule.resetAWSProfile(credsId)
        }

        if (getCertAssetList.size() != 0) {
            for (eachAsset in getCertAssetList) {
                if (eachAsset.status != "archived") {
                    currentFqdnValue = eachAsset.metadata.fqdn
                    try {
                        def currentCertArn
                        def cert_region = 'us-east-1'

                        // detach the certificate
                        if (endpoint_type == 'API') {
                            def endpoint_config = apiGatewayModule.getEndpointConfigType(props["apiId"], awsAccountCreds)
                            if (endpoint_config == 'REGIONAL') {
                                cert_region = regionValue
                            }
                            def customDomainDetails = getDomainDetails(currentFqdnValue, regionValue, endpoint_config, awsAccountCreds)
                            currentCertArn = customDomainDetails.certificateArn
                            //delete custom domain name
                            sh("aws apigateway delete-domain-name --domain-name ${currentFqdnValue} --region ${regionValue} --profile ${awsAccountCreds}")

                        } else if (endpoint_type == 'WEBSITE') {
                            def serviceName = "${serviceConfig['domain']}_${serviceConfig['service']}"
                            def currentConfigDetails = checkCertAndAliasAttached(environmentIds, serviceName, currentFqdnValue, awsAccountCreds)
                            currentCertArn = currentConfigDetails.certificateArn
                            def updatedDistributionID = updateDistributionConfig(environmentIds, serviceName, true, null, null, awsAccountCreds)
                            checkDistributionStatus(updatedDistributionID, awsAccountCreds)
                        }

                        def certRecordDetails = acmModule.getCertRecordDetails(currentCertArn, cert_region, awsAccountCreds)
                        if (certRecordDetails == null) {
                            //Since it takes time to create the certificate, we are trying to fetch the details again after some time
                            println 'Getting records details again'
                            sleep(30)
                            certRecordDetails = acmModule.getCertRecordDetails(currentCertArn, cert_region, awsAccountCreds)
                        }
                        println "certRecordDetails is ${certRecordDetails}"

                        def zoneId
                        def requestStatus = null
                        if(eachAsset.metadata.isPublicEndpoint == true){
                            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE
                        } else {
                            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PRIVATEZONE
                        }
                        try {
                            /*
                            * Assuming temporary role to access route53 services
                            */
                            credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
                            credsId = route53Module.assumeTempRole(credsId, AWS_REGION)
                            /*
                            * delete certificate record
                            */
                            def recordResponse = route53Module.deleteCertificateRecord(certRecordDetails.name, certRecordDetails.value, zoneId, credsId)
                            println "recordResponse: $recordResponse"
                            
                            def count = 0;
                            while(requestStatus == null) {
                                if(count > 4) {
                                    throw new Exception("Error while deleting certificate record")
                                } else if (count > 0) {
                                    sleep(60000)
                                }
                                count++
                                def requestDetails = route53Module.getRequestStatus(recordResponse.ChangeInfo.Id, credsId)
                                println "requestDetails: $requestDetails"
                                if(requestDetails.ChangeInfo.Status == 'INSYNC'){
                                    requestStatus = "APPROVED"
                                }
                            }
                        } catch(ex) {
                            println "Something went wrong deleting certificate record " + ex
                            throw new Exception("Something went wrong deleting certificate record ", ex)
                        } finally {
                            utilModule.resetAWSProfile(credsId)
                        }

                        Number noOfRetries
                        Number timeout
                        noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.CERT_SETTINGS.NO_OF_RETRIES).toLong()
                        timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.CERT_SETTINGS.TIMEOUT_IN_MINS).toLong()


                        /*
                        * Delete certificate
                        */
                        def certDel = null
                        count = 0
                        while(certDel == null) {
                            if(count > noOfRetries) {
                                println "error occourred while deleting certificate"
                                throw new Exception("error occourred while deleting certificate")
                            } else if (count > 0){
                                def sleepTime = ((timeout * 60000)/noOfRetries).toLong()
                                sleep(sleepTime)

                            }
                            count++
                            def deletionResult = acmModule.deleteCertificate(currentCertArn, cert_region, awsAccountCreds)
                            println "deletionResult: $deletionResult"
                            if(deletionResult == "success") {
                                certDel = "success"
                            }
                        }

                    } catch (ex) {
                        println "error:" + ex.message
                        throw new Exception("Error occurred while deleting dns/cert asset: ${eachAsset}", ex)
                    }
                }
            }
        }
    } catch(ex) {
        println "deleteCertAndDNSAssets failed: " + ex.message
        throw new Exception("deleteCertAndDNSAssets failed", ex )
    } finally {
        utilModule.resetAWSProfile(awsAccountCreds)
    }
}

/*
* Function to get endpoint Config Type
* @param apiId
* @param region
*/
def getEndpointConfigType(apiId, region, awsAccountCreds) {
    println "deleteServiceModule.groovy: getEndpointConfigType"

    try {
        def getApiDetails = sh("aws apigateway get-rest-api --rest-api-id ${apiId} --region ${region} --profile ${awsAccountCreds}")
        def apiDetails = JSON.parseJson(getApiDetails)

        println "APIGateway details: $apiDetails"
        return apiDetails.endpointConfiguration.types[0]
    } catch(ex) {
        println "Something went wrong while getting endpoint Config Type: " + ex
        throw new Exception("Something went wrong while getting endpoint Config Type: ", ex)
    }
}

/*
* To update the distribution config file with the new values
*/
def updateDistributionConfig(environment, serviceName, isReset, certArn, domain_name, awsAccountCreds) {
    println "deleteServiceModule.groovy: updateDistributionConfig"

    try {
        def distributionID = getDistributionId(environment, serviceName, awsAccountCreds)
        println "Distribution ID: $distributionID"
        
        if(distributionID) {
            def distributionConfig = sh("aws cloudfront get-distribution-config --profile ${awsAccountCreds} --id ${distributionID} --output json")
            def cfConfig = JSON.parseJson(distributionConfig)

            if (cfConfig == null) { 
                println "Could not parse distribution configuration"
                throw new Exception("Could not parse distribution configuration")
            }
            def newDistConfig = [: ]
            def eTag = cfConfig.ETag

            for(key in cfConfig.DistributionConfig.keySet()) {
                newDistConfig[key] = cfConfig.DistributionConfig[key]
            }
            if (isReset) {
                /*
                * Reset the distribution config file
                */
                newDistConfig.Aliases['Quantity'] = 0
                newDistConfig.Aliases.remove('Items')
                newDistConfig['ViewerCertificate'] = [
                    "CloudFrontDefaultCertificate": true,
                    "MinimumProtocolVersion": "TLSv1",
                    "CertificateSource": "cloudfront"
                ]
            } else {
                if (newDistConfig.Aliases['Quantity'] == 0) {
                    newDistConfig.Aliases['Quantity'] = 1
                    newDistConfig.Aliases['Items'] = [domain_name]
                } else if (newDistConfig.Aliases['Quantity'] > 0 && !newDistConfig.Aliases['Items'].contains(domain_name)) {
                    def currentAliases = newDistConfig.Aliases['Items'] //It will be array of strings
                    // update provided cert ARN and alias name in the distribution config file if it does not exist
                    newDistConfig.Aliases['Quantity'] = newDistConfig.Aliases['Quantity'] + 1
                    newDistConfig.Aliases['Items'] = currentAliases.add(domain_name)
                } else {
                    println 'Provided fqdn is already updated in the distribution config'
                }

                newDistConfig['ViewerCertificate'] = [
                    "SSLSupportMethod": "sni-only", 
                    "ACMCertificateArn": "${certArn}",  // cert ARN
                    "MinimumProtocolVersion": "TLSv1.1_2016", 
                    "Certificate": "${certArn}",  // cert ARN
                    "CertificateSource": "acm"
                ]
            }
            println "newDistConfig: $newDistConfig"

            /*
            * Delete distribution-config.json if exist
            */
            if (JSON.isFileExists('distribution-config.json')) {
                sh("rm -rf distribution-config.json")
            }
            def cfConfigJson = JSON.objectToJsonPrettyString(newDistConfig)
            sh("echo '$cfConfigJson' >> distribution-config.json")

            def updateDistribution = sh("aws cloudfront update-distribution --id ${distributionID} --distribution-config file://distribution-config.json --if-match ${eTag} --profile ${awsAccountCreds}")
            println "Updated Distribution config file: ${JSON.parseJson(updateDistribution)}"

            return distributionID

        } else {
            println "Failing build since Distribution ID is not available"
            throw new Exception("Failing build since Distribution ID is not available")
        }
       
    } catch (ex) {
        println "Error ehile updating distribution config file: " + ex.message
        ex.printStackTrace()
        return ''
    }
}

/*
* check destribution status
* @param distributionId
*/
def checkDistributionStatus(distributionId, awsAccountCreds) {
    println "deleteServiceModule.groovy: checkDistributionStatus"

    try {
        def getDistribution = sh("aws cloudfront get-distribution --id ${distributionId} --profile ${awsAccountCreds}")
        def distributionDetails = JSON.parseJson(getDistribution)

        println "Ditribution details: ${distributionDetails}"
        return distributionDetails.Distribution.Status
    } catch(ex) {
        println "Something went wrong while checling distribution status: " + ex.message
        throw new Exception("Something went wrong while checling distribution status: ", ex)
    }
}

/*
* To get the Cert Details in case of WEBSITE
*/
def checkCertAndAliasAttached(environment, serviceName, domain_name, awsAccountCreds) {
    println "deleteServiceModule.groovy:checkCertAndAliasAttached"

    def configDetails = [
        'isAvailable': false,
        'certificateArn': '',
        'distributionID': '',
        'aliasList': [],
        'isAliasAvailable': false
    ]
    try {
        configDetails.distributionID = getDistributionId(environment, serviceName, awsAccountCreds)
        println "distributionID: ${configDetails.distributionID}"

        if(configDetails.distributionID){
            def distributionConfig = sh("aws cloudfront get-distribution-config --output json --id ${configDetails.distributionID} --profile ${awsAccountCreds}")
            def cfConfig = JSON.parseJson(distributionConfig)

            if (cfConfig == null) {
                println "Could not parse distribution configuration"
                throw new Exception("Could not parse distribution configuration")
            }
            def newDistConfig = [: ]

            for(key in cfConfig.DistributionConfig.keySet()) {
                newDistConfig[key] = cfConfig.DistributionConfig[key]
            }
            
            configDetails.certificateArn = newDistConfig.ViewerCertificate.ACMCertificateArn
            if (newDistConfig.Aliases['Quantity'] > 0 ) {
                configDetails.aliasList = newDistConfig.Aliases['Items'] //It will be array of strings
            }

            if (configDetails.aliasList.contains(domain_name)) {
                configDetails.isAliasAvailable = true
            }
            if (configDetails.certificateArn != null) {
                configDetails.isAvailable = true
            }
            return configDetails
        } else {
            println "Failing build since Distribution ID is not available"
            throw new Exception("Failing build since Distribution ID is not available")
        }

    } catch (ex) {
        println "Error while checking the distribution config file: " + ex.message
        ex.printStackTrace()
        return configDetails
    }
}

/*
* To get the Cert Details in case of API
*/
def getDomainDetails(fqdn, region, endpoint_config, credsId) {
    println "deleteServiceModule.groovy:getDomainDetails"

    def customDomainDetails = [
        'isAvailable': false,
        'certificateArn': ''
    ]
    try {
        def domainDetails = sh("aws apigateway get-domain-name --domain-name ${fqdn} --region ${region} --profile ${credsId}")
        println "domainDetails: $domainDetails"

        if (endpoint_config == 'REGIONAL') {
            customDomainDetails.certificateArn = JSON.parseJson(domainDetails).regionalCertificateArn
        } else {
            customDomainDetails.certificateArn = JSON.parseJson(domainDetails).certificateArn
        }
        customDomainDetails.isAvailable = true
        return customDomainDetails
    } catch (ex) {
        println "Error while fetching custom Domain details: " + ex.message
        ex.printStackTrace()
        return customDomainDetails
    }
}
