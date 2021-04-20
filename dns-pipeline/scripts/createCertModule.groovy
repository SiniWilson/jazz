#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder

/*
* createCertModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Function to create certificate
*/
def createCertificate() {
    println "createCertModule.groovy: createCertificate"

    def env = System.getenv()
    
    def utilModule = new UtilityModule()
    utilModule.showDnsEnvParams()

    def route53Module = new AWSRoute53Module()
    def eventsModule = new EventsModule()
    def acmModule = new AWSAcmModule()
    def loginModule = new Login()
    def awsAccountCreds = null
    def eventName
    def context_map
    def props
    def endpointConfType
    def Configs
    def envApiId
    long MINUTES_TO_MILLISECONDS

    try {
        //aws configure
        def AWS_KEY= env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET= env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION= env['AWS_DEFAULT_REGION']
        context_map = JSON.getValueFromPropertiesFile("context_map")
        Configs = JSON.getValueFromPropertiesFile("configData")
        Number timeOutData = Configs.JAZZ.DNS.APPROVAL_TIMEOUT_IN_MINS
        timeOutData = timeOutData * 1000

        props = JSON.getAllProperties()
        MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        /*
        * Checking if the approval time is still valid or expired
        */
        // update approval status
        def triggerPipeline = false;
        utilModule.approvalStatusUpdate(triggerPipeline);
        Number approvalTime = props["approvalTime"]
        Number currentTimeValue = System.currentTimeMillis();
        Number diffTime = (currentTimeValue - approvalTime)/timeOutData
        println "diffTime: $diffTime"
        if(diffTime > 60) {
            context_map.put('timedOut',true)
            throw new Exception("Approval timeout expired, please raise a request again")
        }

        loginModule.getAuthToken()

        def accountDetails = props["accountDetails"]
        def regionValue = props["deploymentRegion"]
        awsAccountCreds = generateCreds(accountDetails, regionValue, AWS_KEY, AWS_SECRET, AWS_REGION)
        JSON.setValueToPropertiesFile("awsAccountCreds", awsAccountCreds)
        println "awsAccountCreds: $awsAccountCreds"
    } catch(ex) {
        println "Something went wrong while configuring aws account " + ex
        eventsModule.sendStartedEvent('CREATE_CERTIFICATE', 'certificate creation started ', context_map)
        eventsModule.sendFailureEvent('CREATE_CERTIFICATE', 'Failed while creating certificate ', context_map)
        throw new Exception("Something went wrong while configuring aws account ", ex)
    }
        
    try {
        def isCertificateAvailable = false
        def currentCertArn = ""
        def isCertIssued = false
        def certificateDetails
        def dnsName = ""
        def certRegion = 'us-east-1'

        eventName = "VALIDATE_DNS_INPUT"
        def serviceConfig = JSON.getValueFromPropertiesFile("serviceConfig")

        // TODO: need to remove the started event later, added only for testing
        eventsModule.sendStartedEvent('APPROVE_DNS_REQ', 'Your DNS request sent for approval ', context_map)
        eventsModule.sendCompletedEvent('APPROVE_DNS_REQ', 'Your DNS request got approved by ', context_map)
        envApiId = props["apiId"]
        println "envApiId: $envApiId"
        if (props['endpointType'] == 'api') {
            endpointConfType = getEndpointConfigType(props["apiId"], props["deploymentRegion"], awsAccountCreds)
            println "endpointConfType: $endpointConfType"
            if (endpointConfType == 'REGIONAL') {
                certRegion = props["deploymentRegion"]
                println "certRegion: $certRegion"
                JSON.setValueToPropertiesFile("certRegion", props["deploymentRegion"])
                
                def zoneId = route53Module.getHostedZoneId(props["deploymentRegion"], endpointConfType, 'api')
                println "zoneId: $zoneId"
                JSON.setValueToPropertiesFile("zoneId", zoneId);
            }
            def customDomainDetails = getDomainDetails(props["domainName"], props["deploymentRegion"], endpointConfType, awsAccountCreds)
            println "customDomainDetails: $customDomainDetails"
            isCertificateAvailable = customDomainDetails.isAvailable
            currentCertArn = customDomainDetails.certificateArn

            JSON.setValueToPropertiesFile("isCertificateAvailable", isCertificateAvailable)
            JSON.setValueToPropertiesFile("currentCertArn", currentCertArn)
        } else {
            def serviceName = "${serviceConfig['domain']}_${serviceConfig['service']}"
            def checkDistConfig = checkCertAndAliasAttached(props["endpointAsset"], awsAccountCreds)
            println "checkDistConfig: $checkDistConfig"
            currentCertArn = checkDistConfig.certificateArn
        }
        JSON.setValueToPropertiesFile("endpointConfType", endpointConfType)
        
        /*
        * If certificate is already available
        */
        if (isCertificateAvailable) {
            def status
            def statusValue = true;
            def count = 0
            Number noOfRetries
            Number timeout
            if (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName]) {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].TIMEOUT_IN_MINS).toLong()
            } else {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS).toLong()
            }

            /*
            * if status is anything apart from string "ISSUED", it will do retry
            */
            while(statusValue) {
                if (count > noOfRetries) {
                    statusValue = false
                    context_map['timedOut'] = true
                    throw new Exception("Maximum number of retries completed, error - failed while validating certificate!")
                } else if(count > 0) {
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    sleep(sleepTime)
                }
                count++
                status = acmModule.getCertificateStatus(currentCertArn, certRegion, awsAccountCreds)
                if (status == "ISSUED")  {
                    statusValue = false
                }
            }

            if (status == "ISSUED" && props['endpointType'] == 'api') {
                isCertIssued = true
            } else if (status != "PENDING VALIDATION" && status != "ISSUED") {
                acmModule.deleteCertificate(currentCertArn, certRegion, awsAccountCreds)
                isCertificateAvailable = false
            }

            eventValue = "CREATE_CERTIFICATE"
            eventsModule.sendStartedEvent(eventValue, 'certificate creation started', context_map)
            eventsModule.sendCompletedEvent(eventValue, 'certificate creation completed', context_map)
            eventsModule.sendCompletedEvent("CREATE_ASSET", 'asset is created', context_map)

            eventValue = "DNS_VALIDATION"
            eventsModule.sendStartedEvent(eventValue, 'certificate validation started', context_map)
            eventsModule.sendCompletedEvent('DNS_VALIDATION', 'certificate validation completed', context_map)

            eventValue = "ATTACH_CERTIFICATE"
            eventsModule.sendStartedEvent(eventValue, 'started to apply certificate to provided endpoint', context_map)
            eventsModule.sendCompletedEvent(eventValue, 'Certificate got created successfully', context_map)

            certificateDetails = currentCertArn
            JSON.setValueToPropertiesFile("certificateDetails", certificateDetails)

            def resourceRecordValue = acmModule.getCertRecordDetails(certificateDetails, certRegion, awsAccountCreds)
            dnsName = resourceRecordValue.name
            JSON.setValueToPropertiesFile("dnsName", dnsName)
        } 
        /*
        * If certificate is not available
        */
        def tagsObj
        if (!isCertificateAvailable) {
            eventName = "CREATE_CERTIFICATE"
            eventsModule.sendStartedEvent(eventName, 'certificate creation started', context_map)

            tagsObj = [
                'Application': serviceConfig['appTag'],
                'EnvironmentId': props['environmentLogicalId'],
                'Environment': getEnvironmentTag(props['environmentLogicalId']),
                'Platform': Configs['INSTANCE_PREFIX'],
                'ApplicationId': serviceConfig['appId'],
                'Service': serviceConfig['service'],
                'Domain': serviceConfig['domain'],
                'Owner': serviceConfig['owner']
            ]
            println "tagsObj: $tagsObj"

            def count = 0
            if (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName]) {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].TIMEOUT_IN_MINS).toLong()
            } else {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS).toLong()
            }
            println "noOfRetries: $noOfRetries"
            println "timeout: $timeout"

            /*
            * if certificateDetails is null, it will do retry
            */
            while(certificateDetails == null) {
                if(count > noOfRetries) {
                    context_map['timedOut'] = true
                    throw new Exception("Maximum number of retries completed, error - failed while creating certificate!")
                } else if (count > 0) {
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    sleep(sleepTime)
                }
                count ++
                certificateDetails = createCertificateAndAddTags(props["domainName"], certRegion, tagsObj, awsAccountCreds)
                println "certificateDetails: $certificateDetails"
            }
            eventsModule.sendCompletedEvent(eventName, 'certificate creation completed', context_map)

            /*
            * Create an entry for certificate in assets catalog
            */
            println "Creating a certificate asset"
            eventsModule.sendCompletedEvent("CREATE_ASSET", null, generateAssetMap(serviceConfig['type'], 'aws', certificateDetails.CertificateArn, 'certificate', serviceConfig['owner'], props["domainName"], props["endpointAsset"], null, props["certId"], null, null, null, true, null))
        }
        /*
        * If certificate is not available and not issued as well
        */
        if (!isCertIssued && !isCertificateAvailable) {
            eventName = "DNS_VALIDATION"
            eventsModule.sendStartedEvent(eventName, 'certificate validation started', context_map)

            dnsValidation(certificateDetails.CertificateArn, certRegion, serviceConfig, eventName, noOfRetries, timeout, context_map, Configs, awsAccountCreds)

            eventName = "APPROVE_DNS_REQ"

            def certStatusPending = true
            def count = 0

            if (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName]) {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].TIMEOUT_IN_MINS).toLong()
            } else {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS).toLong()
            }

            /*
            * if certStatus is anything apart from string "ISSUED", it will do retry
            */
            while(certStatusPending) {
                if (count > noOfRetries) {
                    certStatusPending = false
                    context_map['timedOut'] = true
                    throw new Exception("Maximum number of retries completed, error - failed while validating certificate!")
                } else if(count > 0) {
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    sleep(sleepTime)
                }
                count++
                def certStatus = acmModule.getCertificateStatus(certificateDetails.CertificateArn, certRegion, awsAccountCreds)
                println "Certificate Status: $certStatus"
                if (certStatus == "ISSUED")  {
                    certStatusPending = false
                }
            }
            eventsModule.sendCompletedEvent('DNS_VALIDATION', 'certificate validation completed', context_map)

            eventName = "ATTACH_CERTIFICATE"
            eventsModule.sendStartedEvent(eventName, 'started to apply certificate to provided endpoint', context_map)
            count = 0
            if (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName]) {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].TIMEOUT_IN_MINS).toLong()
            } else {
                noOfRetries = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES).toLong()
                timeout = (Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS).toLong()
            }

            // if dnsName is anything apart from non-empty string, it will do retry
            while(dnsName == null || dnsName == '' ) {
                if (count > noOfRetries) {
                    context_map['timedOut'] = true
                    throw new Exception("Maximum number of retries completed, error - failed while attaching certificate to the endpoint!")
                } else if (count > 0) {
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    sleep(sleepTime)
                }
                count++
                dnsName = attachCertificateToEndpoint(props["domainName"], certificateDetails.CertificateArn, props["endpointType"], endpointConfType, props["deploymentRegion"], props["endpointAsset"], envApiId, serviceConfig, props["environmentLogicalId"], serviceConfig, awsAccountCreds, tagsObj)
            }
            eventsModule.sendCompletedEvent(eventName, 'Certificate got created successfully ', context_map)
        }
        JSON.setValueToPropertiesFile("isCertificateAvailable", isCertificateAvailable)
        JSON.setValueToPropertiesFile("certificateDetails", certificateDetails)
        JSON.setValueToPropertiesFile("dnsName", dnsName)

    } catch(ex) {
        eventsModule.sendFailureEvent(eventName, 'Failed while creating certificate ', context_map)
        println "Something went wrong while creating certificate: " + ex
        throw new Exception("Something went wrong while creating certificate: ", ex)
    } finally {
        utilModule.resetAWSProfile(awsAccountCreds)
    }
}

/**
* Get the API Id of the gateway specific to an environment. The value will be retrieved from environments table and if not available will try to retrieve it from config
* @param  environment
* @param  filtered apigateway config data
*/
def getAPIId(_envId, apigatewayArr) {
    println "createCertModule.groovy: getAPIId"

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
* Function to attach certificate to endpoint
*/
def attachCertificateToEndpoint(domainName, certArn, endpointType, endpointConfType, region, endpoint, apiId, config, environment, serviceConfig, awsAccountCreds, tags) {
    println "createCertModule.groovy: attachCertificateToEndpoint"

    def eventsModule = new EventsModule()
    try {
        def props = JSON.getAllProperties()
        def dnsName = ""
        /*
        * Apply certificate
        * CF update for website or/and custom domain creation for API
        */
        if (endpointType == 'api') {
            // Create custom domain name for API services and apply certificate
            def customDomain
            def endpointConfMapping
            def map = [
                "regional": [
                    "cert_name": "regional-certificate-name",
                    "cert_arn": "regional-certificate-arn",
                    "target_domain_name": "regionalDomainName"
                ],
                "edge-optimized": [
                    "cert_name": "certificate-name",
                    "cert_arn": "certificate-arn",
                    "target_domain_name": "distributionDomainName"
                ]
            ]
            if (endpointConfType == 'REGIONAL') {
                endpointConfMapping = map["regional"]
            } else {
                endpointConfMapping = map["edge-optimized"]
            }
            def tagsString = tags.inject([]) { result, entry ->
                result << "${entry.key}=${entry.value.toString()}"
            }.join(',')
            println "tagsString: $tagsString"

            customDomain = sh("aws apigateway create-domain-name --domain-name ${domainName} --tags ${tagsString} --${endpointConfMapping.cert_name} ${domainName} --${endpointConfMapping.cert_arn} ${certArn} --endpoint-configuration types=${endpointConfType} --region ${region} --profile ${awsAccountCreds}")

            def domainDetails = JSON.parseJson(customDomain)
            println "Custom domain details: $domainDetails"

            dnsName = domainDetails[endpointConfMapping.target_domain_name]
            println "DNSHost name: $dnsName"

            /*
            * Create an entry for custom_domain in assets catalog
            */
            println "Creating a custom domain asset"
            eventsModule.sendCompletedEvent("CREATE_ASSET", null, generateAssetMap(serviceConfig['type'], 'aws', domainName, 'custom_domain', serviceConfig['owner'], domainName, props["endpointAsset"], null, null, null, null, null, true, null))

            /*
            * get basepath-mapping details
            */
            def getBasepathMappingDetails = sh("aws apigateway get-base-path-mappings --domain-name ${domainName} --region ${region} --profile ${awsAccountCreds}")
            println "Basepath mapping details: $getBasepathMappingDetails"

            if (getBasepathMappingDetails == null || getBasepathMappingDetails == "" || JSON.parseJson(getBasepathMappingDetails).items.size() == 0) {
                /*
                * create basepath-mapping if does not exist
                */
                sh("aws apigateway create-base-path-mapping --domain-name ${domainName} --rest-api-id ${apiId} --base-path ${environment} --stage ${environment} --region ${region} --profile ${awsAccountCreds}")
            } else {
                /*
                * update apigateway basepath mapping
                */
                sh("aws apigateway update-base-path-mapping --domain-name ${domainName} --base-path ${basepathMappings.items[0].basePath} --patch-operations op='replace',path='/basePath',value='${environment}' --region ${region} --profile ${awsAccountCreds}")
            }

        } else if (endpointType == 'website') {
            /*
            * Apply certificate to cloudfront
            */
            def serviceName = "${serviceConfig['domain']}_${serviceConfig['service']}"
            def updatedDistributionID = updateDistributionConfig(props["endpointAsset"], false, certArn, domainName, awsAccountCreds)

            def distStatus = checkDistributionStatus(updatedDistributionID, awsAccountCreds)
            dnsName = endpoint.replace('https://', '')
            println "DNSHost name: $dnsName"
        }

        return dnsName
    } catch(ex) {
        println "Something went wrong while attaching certificate to endpoint: " + ex
        throw new Exception("Something went wrong while attaching certificate to endpoint: ", ex)
    }
}

/*
* Function to update Distribution Config
*/
def updateDistributionConfig(endpoint, isReset, certArn, domainName, awsAccountCreds) {
    println "createCertModule.groovy: updateDistributionConfig"

    try {
        def distributionID = getDistributionId(endpoint, awsAccountCreds)
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
                newDistConfig.Aliases['Quantity'] = 1
                newDistConfig.Aliases['Items'] = [domainName]

                newDistConfig['ViewerCertificate'] = [
                    "SSLSupportMethod": "sni-only", 
                    "ACMCertificateArn": "${certArn}",  // cert ARN
                    "MinimumProtocolVersion": "TLSv1.1_2016", 
                    "Certificate": "${certArn}",  // cert ARN
                    "CertificateSource": "acm"
                ]
            }
            println "Edited Distribution config file: $newDistConfig"

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
        println "Error while updating distribution config file: " + ex
        return ""
    }
}

/*
* Function to validate DNS
*/
def dnsValidation(certArn, certRegion, config, eventName, noOfRetries, timeout, context_map, Configs, awsAccountCreds) {
    println "createCertModule.groovy: dnsValidation"

    /*
    * Get resourceRecord details from ACM for DNS validation
    */
    def eventsModule = new EventsModule()
    def acmModule = new AWSAcmModule()
    def route53Module = new AWSRoute53Module()
    def utilModule = new UtilityModule()
    def credsId = null
    try {
        def props = JSON.getAllProperties()
        long MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        /*
        * Assuming temporary role to access route53 services
        */
        def env = System.getenv()
        def AWS_KEY= env['AWS_302890901340_ACCESS_KEY']
        def AWS_SECRET= env['AWS_302890901340_SECRET_KEY']
        def AWS_REGION= env['AWS_DEFAULT_REGION']
        
        credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
        credsId = route53Module.assumeTempRole(credsId, AWS_REGION)
        println "credsId: $credsId"

        def resourceCount = 0
        def resourceRecord = acmModule.getCertRecordDetails(certArn, certRegion, awsAccountCreds)
        while(resourceRecord == null){
            if (resourceCount > noOfRetries) {
                context_map['timedOut'] = true
                throw new Exception("Maximum number of retries completed, error - failed while getting certificate details!")
            } else if (resourceCount > 0) {
                def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                sleep(sleepTime)
            }
            resourceCount++
            try{
                resourceRecord = acmModule.getCertRecordDetails(certArn, certRegion, awsAccountCreds)
            } catch(ex){
                println "Error while getting record details: " + ex
                resourceRecord = null
            }  
        }

        println "resourceRecord is ${resourceRecord}"
        /*
        * Add acm resource Records to Route53 using Route53 Module
        */
        def isAcmRecordAdded = null
        def count = 0
        def retryCount = 0

        noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES
        timeout = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS

        while(isAcmRecordAdded == null) {
            if (count > noOfRetries) {
                context_map['timedOut'] = true
                throw new Exception("Maximum number of retries completed, error - failed while adding record to public hosted zone!")
            } else if (count > 0) {
                def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                sleep(sleepTime)
            }
            count++
            try{
                isAcmRecordAdded = addToPublicHostedZone(resourceRecord.name, resourceRecord.value, 'public', config, noOfRetries, timeout, context_map, credsId)
                println "isAcmRecordAdded: $isAcmRecordAdded"
            } catch(ex){
                println "Error in createCertificateAndAddTags: " + ex
                isAcmRecordAdded = null
            }    
        }

        if (Configs.JAZZ.DNS.RETRY_SETTINGS[eventName]) {
            noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].NO_OF_RETRIES
            timeout = Configs.JAZZ.DNS.RETRY_SETTINGS[eventName].TIMEOUT_IN_MINS
        } else {
            noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES
            timeout = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS
        }

        def cnameStatusValue = null
        def denied = false
        def zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE

        if(isAcmRecordAdded.status == 'ALREADY_EXIST'){
            println "duplicate record, skipping to next step"

        } else if (isAcmRecordAdded.status != 'NOT_APPROVED' && isAcmRecordAdded.status != 'PENDING') {
            context_map['timedOut'] = true
            eventsModule.sendFailureEvent(eventName, 'Certificate Approval Request is timed out', context_map)
            throw new Exception("Certificate Approval Request is timed out")

        } else if(isAcmRecordAdded.status == 'PENDING'){
            retryCount = 0
            while(cnameStatusValue == null) {
                if (retryCount > noOfRetries) {
                    context_map['timedOut'] = true
                    eventsModule.sendFailureEvent(eventName, 'Certificate Approval Request is timed out', context_map)
                    throw new Exception("Maximum number of retries completed, error - certificate approval timed out!")
                } else if (retryCount > 0) {
                    def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                    println "sleepTime: $sleepTime"
                    sleep(sleepTime)
                }
                retryCount++
                println "retryCount is now : $retryCount"
                try{
                    def cnameStatusData = route53Module.getRequestStatus(isAcmRecordAdded.data, credsId)
                    println "cnameStatusData: $cnameStatusData"
                    if(cnameStatusData.ChangeInfo.Status == 'INSYNC'){
                        cnameStatusValue = "APPROVED"
                    }
                } catch(ex){
                    println "Error in getting request status: " + ex
                    cnameStatusValue = null
                    throw new Exception("Error in getting request status: ", ex)
                }
            }
        }

    } catch(ex) {
        println "Something went wrong while validating DNS: " + ex
        throw new Exception("Something went wrong while validating DNS: ", ex)
    } finally {
        utilModule.resetAWSProfile(credsId)
    }
}

/*
* Function to add resource records to public hosted zone
*/
def addToPublicHostedZone(recordName, recordValue, dnsEndpointType, config, noOfRetries, timeout, context_map, credsId) {
    println "createCertModule.groovy: addToPublicHostedZone"

    try {
        def utilModule = new UtilityModule()

        def Configs = JSON.getValueFromPropertiesFile("configData")
        def zoneId
        if(dnsEndpointType == 'public'){
            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PUBLICZONE
        } else {
            zoneId = Configs.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES["JAZZ.T-MOBILE.COM"].PRIVATEZONE
        }
        def responseJSON = ''
        def cnameStatus = false
        def count = 0
        def returnObj = [:]

        try{
            responseJSON = createRecordForCname(recordName, recordValue, zoneId, credsId);
            println "CNAME DNS record details: ${responseJSON}"
            if(responseJSON && responseJSON.ChangeInfo && responseJSON.ChangeInfo.Status == 'PENDING'){
                cnameStatus = false
            } else if(responseJSON && responseJSON.ChangeInfo && responseJSON.ChangeInfo.Status != 'PENDING'){
                cnameStatus = true
            } else if(responseJSON && responseJSON.duplicate){
                println "duplicate record"
                returnObj.put("status", "ALREADY_EXIST")
                return returnObj
            }

        } catch(ex){
            println "exception occured while creating DNS: " + ex.message
            cnameStatus = false
            throw new Exception("exception occured while creating DNS: ", ex)
        }
        
        println "cnameStatus: $cnameStatus"
        
        if(cnameStatus){
            returnObj.put("status", "APPROVED")
            returnObj.put("data", responseJSON.ChangeInfo.Id)
        } else {
            returnObj.put("status", "PENDING")
            returnObj.put("data", responseJSON.ChangeInfo.Id)
        }
        println "returnObj: $returnObj"
        return returnObj

    } catch(ex){
        println "Error in createCertificateAndAddTags: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }
}

/*
* Function to create the CNAME record using route53 change-resource-record-sets
*/
def createRecordForCname(recordName, recordValue, zoneId, credsId) {
    println "createCertModule.groovy: createRecordForCname"

    try {
        def Configs = JSON.getValueFromPropertiesFile("configData")
        def props = JSON.getAllProperties()
        long MINUTES_TO_MILLISECONDS = props["MINUTES_TO_MILLISECONDS"]
        def responseJSON = '';
        def route53Module = new AWSRoute53Module();

        def retryCount = 0
        def noOfRetries = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.NO_OF_RETRIES
        def timeout = Configs.JAZZ.DNS.RETRY_SETTINGS.DEFAULT.TIMEOUT_IN_MINS
        
        while(responseJSON == '') {
            if (retryCount > noOfRetries) {
                throw new Exception("Error while creating CNAME Record")
            } else if (retryCount > 0) {
                def sleepTime = ((timeout * MINUTES_TO_MILLISECONDS)/noOfRetries).toLong()
                println "sleepTime: $sleepTime"
                sleep(sleepTime)
            }
            retryCount++
            println "retryCount is now : $retryCount"
            responseJSON = route53Module.createCnameRecord(recordName, recordValue, zoneId, credsId)
            println "responseJSON in parent: ${responseJSON}"
            if(responseJSON === 'Throttling error') {
                responseJSON = ''
            }
        }
        return responseJSON;
    } catch(ex) {
        println "Error in createRecordForCname: " + ex.message
        throw new Exception("Error occurs: ", ex)
    }

}

/*
* Function to create certificate and add tags to it
*/
def createCertificateAndAddTags(domainName, certRegion, tagsObj, awsAccountCreds) {
    println "createCertModule.groovy: createCertificateAndAddTags"

    /*
    * certificate creation and adding tags
    */
    def acmModule = new AWSAcmModule()
    try {
        def requestCertificate = acmModule.createCertificate(domainName, certRegion, tagsObj, awsAccountCreds)
        println "Certificate details: ${requestCertificate}"

        if(requestCertificate.isCertCreated) {
            return requestCertificate.result
        } else {
            return null
        }
    } catch (ex) {
        println "Error in createCertificateAndAddTags: " + ex
        return null
    }
}

/**
*   Generate tag for a specific environment based on the id
*
*   Ref: https://ccoe.docs.t-mobile.com/aws/reference/hostname_tagging_guidelines/#environments
*/

def getEnvironmentTag(env){
    println "createCertModule.groovy: getEnvironmentTag"
    
    def envTag = "Non-production"
    if (env == "prod"){
      envTag = "Production"
    }
    return envTag
}

/*
* Function to get accountInfo
*/
def getAccountInfo() {
    println "createCertModule.groovy: getAccountInfo"

    def envModule = new EnvironmentDeploymentMetadataLoader()
    def utilModule = new UtilityModule()

    def environmentInfo =  envModule.getEnvironmentInfo()
    environmentInfo = environmentInfo['deployment_accounts']

    def config = JSON.getValueFromPropertiesFile('serviceConfig')
    config['account'] = environmentInfo[0].account
    config['region'] = environmentInfo[0].region
    JSON.setValueToPropertiesFile('serviceConfig', config)

    def accountDetails = utilModule.getAccountInfo();
    JSON.setValueToPropertiesFile('accountDetails', accountDetails)
}

/*
* Function to get endpoint Config Type
* @param apiId
* @param region
*/
def getEndpointConfigType(apiId, region, awsAccountCreds) {
    println "createCertModule.groovy: getEndpointConfigType"

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
* Function to get domain details
* @param fqdn
* @param region
* @param endpointConfig
*/
def getDomainDetails(fqdn, region, endpointConfig, awsAccountCreds) {
    println "createCertModule.groovy: getDomainDetails"

    def customDomainDetails = [
        "isAvailable": false,
        "certificateArn": ""
    ]
    try {
        def domainDetails = sh("aws apigateway get-domain-name --domain-name ${fqdn} --region ${region} --profile ${awsAccountCreds}")
        domainDetails = JSON.parseJson(domainDetails)

        if (endpointConfig == 'REGIONAL') {
            customDomainDetails.certificateArn = domainDetails.regionalCertificateArn
        } else {
            customDomainDetails.certificateArn = domainDetails.certificateArn
        }
        customDomainDetails.isAvailable = true
        return customDomainDetails
    } catch (ex) {
        println "Error while fetching custom Domain details: " + ex
        return customDomainDetails
    }
}

/*
* Function to check if certificate and alias name is attached to a cloudfront distribution
* @param endpoint cloudfront endpoint (ex: foobar.cloudfront.net)
* @param awsAccountCreds aws credentials
*/
def checkCertAndAliasAttached(endpoint, awsAccountCreds) {
    println "createCertModule.groovy: checkCertAndAliasAttached"

    def configDetails = [
        "isAvailable": false,
        "certificateArn": "",
        "distributionID": ""
    ]
    try {
        configDetails.distributionID = getDistributionId(endpoint, awsAccountCreds)
        println "Distribution ID: ${configDetails.distributionID}"

        if(configDetails.distributionID) {
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
            
            if(configDetails.certificateArn != null){
                configDetails.isAvailable = true
            }
            def distributionStatus = checkDistributionStatus(configDetails.distributionID, awsAccountCreds)
            if (distributionStatus != "Deployed") {
                println "Distribution status is not in 'Deployed' state. Kindly retry after sometime."
                throw new Exception("Distribution status is not in 'Deployed' state. Kindly retry after sometime.Current status: ${distributionStatus}")
            }
            return configDetails
        } else {
            println "Failing build since Distribution ID is not available"
            throw new Exception("Failing build since Distribution ID is not available")
        }

    } catch (ex) {
        println "Error while checking the distribution config file: " + ex
        return configDetails
    }
}

/**
* Get CloudFront distribution id for a specific domain name
* 
* @param domainName CloudFront distribution domain name (ex: foobar.cloudfront.net)
* @param awsProfile AWS profile with credentials
*/
def getDistributionId(domainName, awsProfile) {
    println "createCertModule.groovy: getDistributionId"
    
    def distId
    try {
        def outputStr = sh("aws cloudfront list-distributions --output json --profile ${awsProfile} --query \"DistributionList.Items[?DomainName=='$domainName'].{Id:Id}\"")
        outputObj = JSON.jsonParse(outputStr)

        if(outputObj) {
            println "### OutputStr for getting Distribution Id: $outputObj"
            if(outputObj && outputObj.size > 0 && outputObj[0].Id) {
                distId = outputObj[0].Id
            }
        }
        return distId
    } catch (ex) {
        println "Error while fetching cloudfront distribution id for domain name: $domainName"
        return distId
    }
}

/*
* check destribution status
* @param distributionId
*/
def checkDistributionStatus(distributionId, awsAccountCreds) {
    println "createCertModule.groovy: checkDistributionStatus"

    try {
        def getDistribution = sh("aws cloudfront get-distribution --id ${distributionId} --profile ${awsAccountCreds}")
        def distributionDetails = JSON.parseJson(getDistribution)

        println "Ditribution details: ${distributionDetails}"
        return distributionDetails.Distribution.Status
    } catch(ex) {
        println "Something went wrong while checling distribution status: " + ex
        throw new Exception("Something went wrong while checling distribution status: ", ex)
    }
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

    credsId = utilModule.configureAWSProfile(AWS_KEY, AWS_SECRET, AWS_REGION)
    /*
    * Getting temporary credentials for cross account role if not primary account
    */
    deploymentAccountCreds = utilModule.assumeCrossAccountRole(credsId, AWS_REGION);
    return deploymentAccountCreds
}

/*
* Function to generate Asset Map
*/
def generateAssetMap(service_type, provider, providerId, type, created_by, fqdn, fqdn_value, zone, cert_id, record_type, privateRecordId, publicRecordId, isPublicEndpoint, hostedZoneId) {
	def serviceCtxMap = [
		service_type: service_type,
		provider: provider,
		provider_id: providerId,
		type: type,
		created_by: created_by,
        metadata: [
            fqdn: fqdn,
            endpoint: fqdn_value,
            zone: zone,
            cert_id: cert_id,
            dns_record_type: record_type,
            privateRecordId: privateRecordId,
            publicRecordId: publicRecordId,
            isPublicEndpoint: isPublicEndpoint,
            hostedZoneId: hostedZoneId
        ]
    ]
    return serviceCtxMap;
}
