#!groovy
import common.util.Json as JSON
import static common.util.Shell.sh as sh
import java.lang.*

/** ACM module loaded successfully */

static main( args ) {
	if( args ) {
		"${args.head()}"( *args.tail() )
	}
}

/** 
* Create certificate with provided fqdn in provided region.
* @params fqdn: Certificate will be created with provided fqdn(or domain) name.
* @params region: Region in which certificate should be created.
* @params tags: Tags to attach/add to Certificate.
*/
def createCertificate(fqdn, region, tags, awsProfile) {
    println "In AWSAcmModule.groovy:createCertificate"

    def data = [
        'isCertCreated': false,
        'result' : null
    ]
    try {
        def requestedCertificate = sh("aws acm request-certificate --domain-name ${fqdn}  --validation-method DNS --region ${region} --profile ${awsProfile}")
        
        def certDetails = JSON.parseJson(requestedCertificate)
        println "Certificate details: ${certDetails}"
        
        addTagsToCertificate(certDetails.CertificateArn, tags, region, awsProfile)

        data.isCertCreated = true
        data.result = certDetails
        return data
    } catch(ex) {
        println "error occourred while creating certificate: " + ex
        return data
    }
}

/**
* Add tags to certificate
* @params certificateARN: ARN of the Certificate to which tags should be added.
* @params tags: Tags to attach/add to Certificate.
* @params region: Region in which certificate is created.
*/
def addTagsToCertificate(certificateARN, tags, region, awsProfile) {
    println "In AWSAcmModule.groovy:addTagsToCertificate"
    try {
    def tagsString = ''
    for (keyname in tags.keySet()) {
        tagsString = "${tagsString}'Key=${keyname},Value=${tags[keyname]}' "
    }

    sh("aws acm add-tags-to-certificate --certificate-arn ${certificateARN} --tags ${tagsString.trim()} --region ${region} --profile ${awsProfile}")
    println "tags added to certificate"
    } catch (ex) {
        println "error occourred while adding tags to certificate: " + ex
        throw new Exception("error occourred while adding tags to certificate", ex)
    }
}

/**
* Delete certificate with provided certificate ARN in provided region
* @params certificateARN: ARN of the Certificate that need to be deleted.
* @params region: Region in which certificate is created/available.
*/
def deleteCertificate(certificateARN, region, awsProfile) {
    println "In AWSAcmModule.groovy:deleteCertificate"
    try {
        sh("aws acm delete-certificate --certificate-arn ${certificateARN} --region ${region} --profile ${awsProfile}")
        println "Certificate deleted"
        return "success"
    } catch (ex) {
        println "error occurred while deleting certificate: " + ex
        return "failure"
    }
}

/**
* Get certificate details of provided certificate ARN, if exist return the status
* @params certificateARN: ARN of Certificate.
* @params region: Region in which certificate is created/available.
*/
def getCertificateStatus(certificateARN, region, awsProfile) {
    println "In AWSAcmModule.groovy:getCertificateStatus"
    
    try {
        def certDetails = getCertificateDetails(certificateARN, region, awsProfile)
        if (certDetails && certDetails.Certificate && certDetails.Certificate.Status) {
            return certDetails.Certificate.Status
        }
        return null
    } catch (ex) {
        println "error occurred while getting certificate's status: " + ex
        return null
    }
    
}

/**
* Get certificate details of provided certificate ARN, if exist return the record details
* @params certificateARN: ARN of Certificate.
* @params region: Region in which certificate is created/available.
*/
def getCertRecordDetails(certificateARN, region, awsProfile) {
    println "In AWSAcmModule.groovy:getCertRecordDetails"

    try {
        def certDetails = getCertificateDetails(certificateARN, region, awsProfile)
        def resourceRecord
        println "Certificate details: ${certDetails}"
        if(certDetails.Certificate.Status == 'FAILED'){
            println "Certificate status is Failed! Something wrong during certificate creation/validation, check with AWS ACM service!"
            throw new Exception("Error while getting certificate details, certificate status - FAILED")
        }
        if(certDetails.Certificate.DomainValidationOptions.size() != 0){
            resourceRecord = [:]
            for (item in certDetails.Certificate.DomainValidationOptions) {
                if(item.ResourceRecord){
                    resourceRecord['name'] = item.ResourceRecord.Name
                    resourceRecord['value'] = item.ResourceRecord.Value
                }
            }
            if(resourceRecord.size() == 0){
                return null
            } else {
                return resourceRecord
            }
        } else {
            return null
        }
    } catch (ex) {
        println "error occurred while fetching certificate record details: " + ex
        return null
    }
}

/**
* Get certificate details of provided certificate ARN
* @params certificateARN: ARN of Certificate.
* @params region: Region in which certificate is created/available.
*/
def getCertificateDetails(certificateARN, region, awsProfile) {
    println "In AWSAcmModule.groovy:getCertificateDetails"
    try {
        def getCertificate = sh("aws acm describe-certificate --certificate-arn ${certificateARN} --region ${region} --profile ${awsProfile}")
        def certificateDetails = JSON.parseJson(getCertificate)
        return certificateDetails
    } catch (ex) {
        println "error occurred while getting certificate's detail: " + ex
        throw new Exception("error occourred while getting certificate's detail", ex)
    }
}
