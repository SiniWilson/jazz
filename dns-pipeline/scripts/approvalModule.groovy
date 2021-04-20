#!groovy?
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.Yaml as YAML
import common.util.File as FILE
import static common.util.Shell.sh as sh
import java.lang.*
import java.net.URLEncoder


/*
* approvalModule.groovy
* @author: Saurav Dutta
* @version: 1.0
*/

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

/*
* Function to initiate the approval workflow
*/
def approvalWorkflow() {
    println "approvalModule.groovy: approvalWorkflow"

    def utilModule = new UtilityModule()
    utilModule.showDnsEnvParams()

    def eventsModule = new EventsModule()
    def props = JSON.getAllProperties()
    def configData = props['configData']
    def serviceConfig = props['serviceConfig']
    def context_map = JSON.getValueFromPropertiesFile("context_map")
    def id

    try {
        eventsModule.sendStartedEvent('APPROVE_DNS_REQ', 'Your DNS request sent for approval ', context_map)
        /*
        * sending email notification to approver
        */
        def approvalUrl = "http://${configData.JAZZ.JAZZ_HOME_BASE_URL}/services"
        def approvalTimeoutMins = 60
        id = utilModule.getDeploymentId(approvalTimeoutMins, serviceConfig, "create-certificate")
        JSON.setValueToPropertiesFile("approvalDeploymentId", id);
        notifyApprover(id, approvalUrl, approvalTimeoutMins, serviceConfig, configData)
        def approvalTime = System.currentTimeMillis();
        println "approvalTime: $approvalTime"
        JSON.setValueToPropertiesFile("approvalTime", approvalTime)

    } catch(ex) {
        println "Something went wrong in the approval workflow: " + ex
        throw new Exception("Something went wrong in the approval workflow: ", ex)
    }
}

/*
* Function to notify the approver
* @param uiLink
* @param timeOutMins
* @param serviceConfig
* @param configData
*/
def notifyApprover(id, uiLink , timeOutMins, serviceConfig, configData){
    println "approvalModule.groovy: approvalWorkflow"

    try {
        def env = System.getenv()
        def domainName = "${env.FQDN}.jazz.t-mobile.com"
        def expTime = sh("TZ=America/Los_Angeles date --date=\"$timeOutMins minutes\"", true)

        def svcAdmin = [
            'first_name': configData.JAZZ.DNS.OWNER_FIRST_NAME,
            'last_name': configData.JAZZ.DNS.OWNER_LAST_NAME,
            'email': configData.JAZZ.DNS.OWNER
        ]

        def body = JSON.objectToJsonString([
            from: configData.JAZZ.NOTIFICATIONS.FROM_ADDRESS,
            to: [[
                emailID: svcAdmin.email,
                name: [
                        first: svcAdmin.first_name,
                        last: svcAdmin.last_name
                        ],
                heading: "DNS Approval Action Needed",
                message: "The following DNS Request is pending your approval <br/> Service: <b>${serviceConfig['service']}</b> <br/>Domain: <b>${serviceConfig['domain']}</b> <br/>FQDN: <b>${domainName}</b> <br/>Service Endpoint: <b>${env.ENDPOINT}</b>",
                details: "",
                linkexpire: "Your link expires at " + expTime,
                link: [
                        [
                            text: "Approve",
                            url: uiLink  + "?action=proceed&id=${id}&serviceId=${serviceConfig['id']}&serviceName=${serviceConfig['service']}&domain=${serviceConfig['domain']}&fqdn=${domainName}&endpoint=${env.ENDPOINT}"
                        ],
                        [
                            text: "Reject",
                            url: uiLink  + "?action=reject&id=${id}&serviceId=${serviceConfig['id']}&serviceName=${serviceConfig['service']}&domain=${serviceConfig['domain']}&fqdn=${domainName}&endpoint=${env.ENDPOINT}"
                        ]
                    ]
                ]],
            subject: "Approve DNS workflow for creating certificate for ${serviceConfig['service']}" ,
            templateDirUrl: "https://s3-${configData.AWS.REGION}.amazonaws.com/asgc-email-templates/approvalv1/",
            id: id
        ])

        def sendMail = sh("set +x; curl -k -v -H 'Content-type: application/json' -d '$body' https://${configData.AWS.API.HOST_NAMES.PROD}/api/platform/send-email; set -x", true )
        def responseJSON = JSON.parseJson(sendMail)

        if(responseJSON.data){
            println "successfully sent e-mail to $svcAdmin.first_name $svcAdmin.last_name at $svcAdmin.email"
        } else {
            println "exception occured while sending e-mail: $responseJSON"
        }

    } catch (ex){
        println "Exception occured: " + ex
        throw new Exception("Exception occured while notifying approver ", ex)
    }
}
