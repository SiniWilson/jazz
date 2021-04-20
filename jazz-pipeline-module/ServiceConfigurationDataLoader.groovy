#!groovy?
import groovy.json.*
import common.util.Json as JSON
import common.util.Props as PROPS
import common.util.File as FILE
import common.util.Yaml as YAML
import static common.util.Shell.sh as sh
import java.lang.*

println "Service configuration module loaded successfully"

static main( args ) {
    if( args ) {
        "${args.head()}"( *args.tail() )
    }
}

def loadServiceConfigurationData() {
	println "loadServiceConfigurationData is called.."
	def configData = JSON.getValueFromPropertiesFile('configData')
	def serviceConfig = JSON.getValueFromPropertiesFile('serviceConfig')
	def env = System.getenv()
	def AWS_KEY = env['AWS_302890901340_ACCESS_KEY']
	def AWS_SECRET = env['AWS_302890901340_SECRET_KEY']
	def AWS_REGION = env['us-west-2']
	def domain = serviceConfig['domain']
	def service = serviceConfig['service']
	def utilModule = new UtilityModule()
	try {
		if ((domain == "jazz") && (service == "webapp")){
			/* Getting the accounts and regions */
			def accountMap = [:];
			def eachAccountMap = [];
			def regions = [];
			for (item in configData.AWS.ACCOUNTS) {
				regions = [];
				for(data in item.REGIONS){
					regions.push(data.REGION)
				}
				accountMap = [:]
				if(item.PRIMARY){
					accountMap.put('primary', true)
				} else {
					accountMap.put('primary', false)
				}
				accountMap.put('account', item.ACCOUNTID);
				accountMap.put('regions', regions);
				accountMap.put('accountName', item.ACCOUNTNAME);
				eachAccountMap.push(accountMap);
			}
			def uiAccountMap = JSON.objectToJsonString(eachAccountMap)
			sh("sed -i -- 's/{accountMap}/${uiAccountMap}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			
			// Getting the regionList and populating the list
			def regionList = [];
			for (item in configData.JAZZ.REGION_LIST) {
				regionList.push(item)
			}
			def regionValue = JSON.objectToJsonString(regionList)
			sh("sed -i -- 's/{region_list}/${regionValue}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			//Getting the admin group
			sh("sed -i -- 's/{adminGroup}/${configData.JAZZ.ADMIN_GROUP}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			
			// Getting the number of days value after which test application services should be deleted
			sh("sed -i 's/{pocAppLifeInDays}/${configData.JAZZ.POC_APP_LIFE_IN_DAYS}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			// Getting the pocApplicationName for test/poc services
			sh("sed -i -- 's/{pocApplicationName}/${configData.JAZZ.DEFAULT_POC_APP_NAME}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			// Getting the pocApplicationTag for test/poc services
			sh("sed -i -- 's/{pocApplicationTag}/${configData.JAZZ.DEFAULT_POC_APP_TAG}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			// Getting the default Jazz akmId value
			sh("sed -i -- 's/{akmId}/${configData.QCP.DEFAULTS.AKMID}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			def uiConfig = JSON.objectToJsonString(configData.UI_CONFIG)
			sh("sed -i 's/{INSTALLER_VARS}/${uiConfig}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			sh("sed -i 's/{base_url}/${configData.AWS.API.HOST_NAMES.PROD}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			sh("sed -i 's/{api_doc_name}/${configData.JAZZ.API_DOC.S3_BUCKET}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			sh("sed -i 's/{multi_env}/${configData.UI_CONFIG.feature.multi_env}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			sh("sed -i s!{swagger_editor}!${configData.JAZZ.SWAGGER.EDITOR_URL}!g ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			sh("sed -i s!{feedback_url}!${configData.JAZZ.FEEDBACK_URL}!g ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			sh("sed -i  -- 's/{google-tag-manager-id}/${configData.ANALYTICS.GOOGLE_TAG_MANAGER_ID}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			sh("sed -i  -- 's|{matomo-endpoint}|${configData.ANALYTICS.MATOMO_ENDPOINT}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")
			sh("sed -i 's|{matomoTrackingId}|${configData.ANALYTICS.MATOMO_TRACKING_ID}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")

			// Getting Grafana dashboard URL for Metrics
			sh("sed -i -- 's#{conf-grafana-url}#${configData.GRAFANA.URL}#g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts")


			if(configData.UI_CONFIG.service_tabs.overview)   sh("sed -i 's/{overview}/overview/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.service_tabs.access_control)   sh("sed -i 's/{access control}/access control/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.service_tabs.metrics)   sh("sed -i 's/{metrics}/metrics/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.service_tabs.cost)   sh("sed -i 's/{cost}/cost/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.service_tabs.logs)       sh("sed -i 's/{logs}/logs/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.environment_tabs.overview) sh("sed -i 's/{env_overview}/overview/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.environment_tabs.deployments)  sh("sed -i 's/{deployments}/deployments/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.environment_tabs.code_quality == true )  sh("sed -i 's/{code quality}/code quality/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.environment_tabs.assets)  sh("sed -i 's/{assets}/assets/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.environment_tabs.logs)  sh("sed -i 's/{env_logs}/logs/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");
			if(configData.UI_CONFIG.environment_tabs.dns)  sh("sed -i 's/{dns}/dns/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts");

			def envFile = new File("${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/app/src/environments/environment.prod.ts").getText('UTF-8')
			println "displaying env file : ${envFile}"
		}
		
		if ((domain == "jazz") && (service == "documentation")){
			sh("sed -i -- 's/{swagger_bucket}/${configData.JAZZ.API_DOC.S3_BUCKET}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/build.website");
		}

		if ((domain == 'jazz' ) && (service == "is-service-available")){
			updateConfigValue("{inst_stack_prefix}", configData.INSTANCE_PREFIX)
			sh("sed -i -- 's/{inst_stack_prefix}/${configData.INSTANCE_PREFIX}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")
		}

		if ((domain == 'jazz' ) && (service == "services")){
			updateConfigValue("{inst_stack_prefix}", configData.INSTANCE_PREFIX)

			def allowedRuntimes = ""
			for (String item: configData.JAZZ.ALLOWED_RUNTIMES) {
				allowedRuntimes += '"' + item + '",'
			}
			allowedRuntimes = allowedRuntimes.substring(0, allowedRuntimes.length()-1)
			sh("sed -i -- 's/\"{conf_allowed_runtimes}\"/$allowedRuntimes/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")

		}


		if ((domain == 'jazz' ) && (service == "acl")) {
			updateConfigValue("{casbin_user}", env.CASBIN_USER)
			updateConfigValue("{casbin_password}", env.CASBIN_PASSWORD)
			updateConfigValue("{casbin_host}", configData.CASBIN.HOST)
			updateConfigValue("{casbin_port}", configData.CASBIN.PORT)
			updateConfigValue("{casbin_database}", configData.CASBIN.DATABASE)
			updateConfigValue("{casbin_type}", configData.CASBIN.TYPE)
			updateConfigValue("{casbin_timeout}", configData.CASBIN.TIMEOUT)
			updateCoreAPI()
			updateConfigValue("{inst_stack_prefix}", configData.INSTANCE_PREFIX)
			updateConfigValue("{conf-region}", configData.AWS.DEFAULTS.REGION)

			sh("sed -i -- 's/{scm_type}/${configData.SCM.TYPE}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")
			sh("sed -i -- 's#{scm_base_url}#https://${configData.REPOSITORY.BASE_URL}#g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")
			sh("sed -i -- 's#{add_user_api}#${configData.JAZZ.USER_MGMT.ADD_USER_API}#g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")
			sh("sed -i -- 's/{slack_token}/${configData.JAZZ.USER_MGMT.SLACK_AUTH_TOKEN}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")
			sh("sed -i -- 's#{conf-deployers-group-id}#${configData.GITLAB.DEPLOYERS_GROUP_ID}#g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")
			sh("sed -i -- 's#{conf-cas-gitlab-token-location}#${configData.JAZZ.CAS_GITLAB_TOKEN_LOCATION}#g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/global-config.json")
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
			updateConfigValue("{cas-support-group-id}", configData.JAZZ.CAS_SUPPORT_GROUP_ID)
		}

		if((domain == 'jazz' ) && (service == "dns")){
			updateConfigValue("{conf-host-name}", configData.AWS.API.HOST_NAMES.PROD)
			updateConfigValue("{conf-private-zone}", configData.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES['JAZZ.T-MOBILE.COM'].PRIVATEZONE)
			updateConfigValue("{conf-public-zone}", configData.JAZZ.DNS.AWS.ROUTE53.HOSTED_ZONES['JAZZ.T-MOBILE.COM'].PUBLICZONE)
			updateConfigValue("{conf-caps-role-arn}", configData.JAZZ.DNS.AWS.IAM.ROLE) 
			updateConfigValue("{dns-pipeline-build-url}", configData.GITLAB.DNS_PIPELINE_URL)
			updateConfigValue("{dns-pipeline-build-token}", configData.GITLAB.DNS_PIPELINE_TOKEN)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) && (service == "admin")){
			updateConfigValue("{conf-region}", configData.AWS.DEFAULTS.REGION)
			updateConfigValue("{service-config-table}", "service-config")
		}

		if ((domain == 'jazz') && (service == 'costs')) {
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
			updateConfigValue("{conf-cost-api-base-url}", configData.JAZZ.COST_BASE_URL)
		}

		if ((domain == 'jazz') && (service == 'users')) {
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) &&  (service == "delete-serverless-service" || service == "create-serverless-service" || service == "cleanjazztestservices")) {
			
			updateConfigValue("{conf-host-name}", configData.AWS.API.HOST_NAMES.PROD)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
			updateCoreAPI()
			
			if((domain == 'jazz' ) && (service == "delete-serverless-service")) {	
				updateConfigValue("{conf-cas-gitlab-token-location}", configData.GITLAB.GITLAB_SECRET_ID)
				updateConfigValue("{build-url}", configData.GITLAB.DELETE_PIPELINE_URL)
			}

			if ((domain == 'jazz' ) && (service == "create-serverless-service")) {
				sh("sed -i -- 's/{conf-host-name}/${configData.AWS.API.HOST_NAMES.PROD}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{conf-provider-memorysize}", configData.JAZZ.DEFAULT_PROVIDER_MEMORY_SIZE)
				sh("sed -i -- 's/{conf-provider-memorysize}/${configData.JAZZ.DEFAULT_PROVIDER_MEMORY_SIZE}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{conf-provider-timeout}", configData.JAZZ.DEFAULT_PROVIDER_TIMEOUT)
				sh("sed -i -- 's/{conf-provider-timeout}/${configData.JAZZ.DEFAULT_PROVIDER_TIMEOUT}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{conf-region}", configData.AWS.DEFAULTS.REGION)
				sh("sed -i -- 's/{conf-region}/${configData.AWS.DEFAULTS.REGION}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{conf-account}", configData.AWS.DEFAULTS.ACCOUNTID)
				sh("sed -i -- 's/{conf-account}/${configData.AWS.DEFAULTS.ACCOUNTID}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{conf-provider}", configData.JAZZ.DEFAULTS.PROVIDER)
				sh("sed -i -- 's/{conf-provider}/${configData.JAZZ.DEFAULTS.PROVIDER}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{default-production-approval-timeout-min}", configData.JAZZ.PRODUCTION_APPROVAL_TIMEOUT_MINS)
				sh("sed -i -- 's/{default-production-approval-timeout-min}/${configData.JAZZ.PRODUCTION_APPROVAL_TIMEOUT_MINS}/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{create-service-pipeline-build-url}", configData.GITLAB.CREATE_SERVICE_PIPELINE_URL)
				sh("sed -i -- 's|{create-service-pipeline-build-url}|${configData.GITLAB.CREATE_SERVICE_PIPELINE_URL}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				updateConfigValue("{create-service-pipeline-build-token}", configData.GITLAB.CREATE_SERVICE_PIPELINE_TOKEN)
				sh("sed -i -- 's|{create-service-pipeline-build-token}|${configData.GITLAB.CREATE_SERVICE_PIPELINE_TOKEN}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")
				
				def allowedRuntimes = ""
				for (String item: configData.JAZZ.ALLOWED_RUNTIMES) {
					allowedRuntimes += '"' + item + '",'
				}
				allowedRuntimes = allowedRuntimes.substring(0, allowedRuntimes.length()-1)

				sh("sed -i -- 's/\"{conf_allowed_runtimes}\"/$allowedRuntimes/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
				sh("sed -i -- 's/\"{conf_allowed_runtimes}\"/$allowedRuntimes/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
				sh("sed -i -- 's/\"{conf_allowed_runtimes}\"/$allowedRuntimes/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
				sh("sed -i -- 's/\"{conf_allowed_runtimes}\"/$allowedRuntimes/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				def allowedWebsiteFrameworks = ""
				for (String item: configData.JAZZ.ALLOWED_WEBSITE_FRAMEWORKS) {
					allowedWebsiteFrameworks += '"' + item + '",'
				}
				allowedWebsiteFrameworks = allowedWebsiteFrameworks.substring(0, allowedWebsiteFrameworks.length()-1)
				sh("sed -i -- 's/\"{conf_allowed_ws_frameworks}\"/$allowedWebsiteFrameworks/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
				sh("sed -i -- 's/\"{conf_allowed_ws_frameworks}\"/$allowedWebsiteFrameworks/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
				sh("sed -i -- 's/\"{conf_allowed_ws_frameworks}\"/$allowedWebsiteFrameworks/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
				sh("sed -i -- 's/\"{conf_allowed_ws_frameworks}\"/$allowedWebsiteFrameworks/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				def apiOptions=""
				def functionOptions=""
				def websiteOptions=""
				def slsOptions=""

				for (String item: configData.JAZZ.DEPLOYMENT_TARGETS.API) {
					apiOptions += '"' + item + '",'
				}
				apiOptions = apiOptions.substring(0, apiOptions.length()-1)

				for (String item: configData.JAZZ.DEPLOYMENT_TARGETS.FUNCTION) {
					functionOptions += '"' + item + '",'
				}
				functionOptions = functionOptions.substring(0, functionOptions.length()-1)

				for (String item: configData.JAZZ.DEPLOYMENT_TARGETS.WEBSITE) {
					websiteOptions += '"' + item + '",'
				}
				websiteOptions = websiteOptions.substring(0, websiteOptions.length()-1)

				for (String item: configData.JAZZ.DEPLOYMENT_TARGETS['SLS-APP']) {
					slsOptions += '"' + item + '",'
				}
				slsOptions = slsOptions.substring(0, slsOptions.length()-1)

				sh("sed -i -- 's/\"{conf_deployment_targets_api}\"/$apiOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_api}\"/$apiOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_api}\"/$apiOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_api}\"/$apiOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				sh("sed -i -- 's/\"{conf_deployment_targets_function}\"/$functionOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_function}\"/$functionOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_function}\"/$functionOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_function}\"/$functionOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				sh("sed -i -- 's/\"{conf_deployment_targets_website}\"/$websiteOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_website}\"/$websiteOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_website}\"/$websiteOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_website}\"/$websiteOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

				sh("sed -i -- 's/\"{conf_deployment_targets_sls-app}\"/$slsOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_sls-app}\"/$slsOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_sls-app}\"/$slsOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
				sh("sed -i -- 's/\"{conf_deployment_targets_sls-app}\"/$slsOptions/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/test-config.json")

			}

		}
		
		if ((domain == 'jazz' ) && (service == 'ipauth' || service == 'ipauthidxfw')) {
			def ipList = ""
			for (String ip: configData.JAZZ.INTERNAL_IP_ADDRESSES) {
				ipList += '"' + ip + '",'
			}
			ipList = ipList.substring(0, ipList.length()-1)
			sh("sed -i -- 's|\"{tmobile_exit_ip_range}\"|$ipList|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/config.json")
		}

		if ((domain == 'jazz' ) && (service == "asset-event-handler")) {
			updateCoreAPI()
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)

		}

		if ((domain == 'jazz' ) && (service == "acl-authorizer")) {
			updateConfigValue("{casbin_user}", env.CASBIN_USER)
			updateConfigValue("{casbin_password}", env.CASBIN_PASSWORD)
			updateConfigValue("{casbin_host}", configData.CASBIN.HOST)
			updateConfigValue("{casbin_port}", configData.CASBIN.PORT)
			updateConfigValue("{casbin_database}", configData.CASBIN.DATABASE)
			updateConfigValue("{casbin_type}", configData.CASBIN.TYPE)
			updateConfigValue("{casbin_timeout}", configData.CASBIN.TIMEOUT)
			updateCoreAPI()
			updateConfigValue("{inst_stack_prefix}", configData.INSTANCE_PREFIX)
			updateConfigValue("{conf-region}", configData.AWS.DEFAULTS.REGION)
		}


		if ((domain == 'jazz' ) && (service == 'acl-authorizer' || service == 'custom-ad-admin-authorizer' || service == 'custom-ad-authorizer')){
			def ipList = ""
			for (String ip: configData.JAZZ.INTERNAL_IP_ADDRESSES) {
				ipList += '"' + ip + '",'
			}
			ipList = ipList.substring(0, ipList.length()-1)
			sh("sed -i -- 's|\"{tmobile_exit_ip_range}\"|$ipList|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
			sh("sed -i -- 's|\"{tmobile_exit_ip_range}\"|$ipList|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
			sh("sed -i -- 's|\"{tmobile_exit_ip_range}\"|$ipList|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
			
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-support-user-group}", configData.JAZZ.CAS_SUPPORT_GROUP) 
			updateConfigValue("{conf-cas-service-principal-id}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_ID)
		}


		if ((domain == 'jazz' ) && (service == "deployments-event-handler")) {
			updateCoreAPI()
			updateConfigValue("{gitlab_function_job_url}", configData.GITLAB.FUNCTION_PIPELINE_URL)
			updateConfigValue("{gitlab_function_token}", configData.GITLAB.FUNCTION_PIPELINE_TOKEN)
			updateConfigValue("{gitlab_api_job_url}", configData.GITLAB.API_PIPELINE_URL)
			updateConfigValue("{gitlab_api_token}", configData.GITLAB.API_PIPELINE_TOKEN)
			updateConfigValue("{gitlab_website_job_url}", configData.GITLAB.WEBSITE_PIPELINE_URL)
			updateConfigValue("{gitlab_website_token}", configData.GITLAB.WEBSITE_PIPELINE_TOKEN)
			updateConfigValue("{gitlab_serverless_job_url}", configData.GITLAB.SERVERLESS_PIPELINE_URL)
			updateConfigValue("{gitlab_serverless_token}", configData.GITLAB.SERVERLESS_PIPELINE_TOKEN)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) && (service == "splunk-kinesis-log-streamer")) {

			updateCoreAPI()
			updateConfigValue("{splunk_endpoint}", configData.SPLUNK.ENDPOINT)
			updateConfigValue("{spunk_hec_token}", configData.SPLUNK.HEC_TOKEN)
			updateConfigValue("{splunk_index}", configData.SPLUNK.INDEX)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) && (service == "environment-event-handler")) {
			updateCoreAPI()
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) && (service == "email-event-handler")) {
			updateCoreAPI()
			updateConfigValue("{conf-ui-host-name}", configData.JAZZ.JAZZ_HOME_BASE_URL)
			updateConfigValue("{conf-notification-email-address}", configData.JAZZ.NOTIFICATIONS.FROM_ADDRESS)
			updateConfigValue("{conf-region}", configData.AWS.DEFAULTS.REGION)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) && (service == "events-slack-handler")) {
			updateCoreAPI()
			updateConfigValue("{conf-ui-host-name}", configData.JAZZ.JAZZ_HOME_BASE_URL)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
			updateConfigValue("{jazz-notifier-slack-token}", configData.SLACK.SLACK_TOKEN)
		}

		if ((domain == 'jazz' ) && (service == "services-handler")) {
			updateCoreAPI()
			updateConfigValue("{conf-region}", configData.AWS.DEFAULTS.REGION)
			updateConfigValue("{conf-account}", configData.AWS.DEFAULTS.ACCOUNTID)
			updateConfigValue("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValue("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}

		if ((domain == 'jazz' ) && (service == "environments")) {
			updateCoreAPI()
		}

		if ((domain == 'jazz' ) && (service == "slack-utils")) {
			updateConfigValue("{slackAuthToken}", configData.JAZZ.USER_MGMT.SLACK_AUTH_TOKEN)
			updateConfigValue("{tenantId}", configData.JAZZ.USER_MGMT.AZURE_AD.TENANT_ID)
			updateConfigValue("{user_group_id}", configData.JAZZ.USER_MGMT.GROUP_ID)
		}

		if ((domain == 'jazz' ) && (service == "cleanjazztestservices")) {
			updateConfigValue("{jazztest-life-days}", configData.JAZZ.JAZZTEST_LIFE_IN_DAYS)
			updateConfigValue("{jazz-services-cleanup-days}", configData.JAZZ.POC_APP_LIFE_IN_DAYS)

			def poc_app_names = ""
			for (String item: configData.JAZZ.POC_APP_NAMES) {
				poc_app_names += '"' + item + '",'
			}
			poc_app_names = poc_app_names.substring(0, poc_app_names.length()-1)
			sh("sed -i -- 's/\"{jazz-services-poc-app-names}\"/$poc_app_names/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
			sh("sed -i -- 's/\"{jazz-services-poc-app-names}\"/$poc_app_names/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
			sh("sed -i -- 's/\"{jazz-services-poc-app-names}\"/$poc_app_names/g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
		}

		if ((domain == 'jazz' ) && (service == "codeq")) {
			updateCoreAPI()
			updateConfigValue("{conf-cas-cdp-secret}", configData.CODE_QUALITY.SONAR.SONAR_SECRET)
			updateConfigValue("{conf-cas-gitlab-token-location}", configData.JAZZ.CAS_GITLAB_TOKEN_LOCATION)
		}

		if ((domain == 'jazz' ) && (service == "approvals")) {
			updateConfigValue("{conf-cas-gitlab-token-location}", configData.JAZZ.CAS_GITLAB_TOKEN_LOCATION)
		}

		if ((domain == 'jazz' ) && (service == "deployments")) {
			updateConfigValue("{gitlab_function_job_url}", configData.GITLAB.FUNCTION_PIPELINE_URL)
			updateConfigValue("{gitlab_function_token}", configData.GITLAB.FUNCTION_PIPELINE_TOKEN)
			updateConfigValue("{gitlab_api_job_url}", configData.GITLAB.API_PIPELINE_URL)
			updateConfigValue("{gitlab_api_token}", configData.GITLAB.API_PIPELINE_TOKEN)
			updateConfigValue("{gitlab_website_job_url}", configData.GITLAB.WEBSITE_PIPELINE_URL)
			updateConfigValue("{gitlab_website_token}", configData.GITLAB.WEBSITE_PIPELINE_TOKEN)
			updateConfigValue("{gitlab_serverless_job_url}", configData.GITLAB.SERVERLESS_PIPELINE_URL)
			updateConfigValue("{gitlab_serverless_token}", configData.GITLAB.SERVERLESS_PIPELINE_TOKEN)
		}

		if ((domain == 'jazz' ) && (service == "depevthand" || service == "envevthand")) {
			updateConfigValueCustom("{gitlab_function_job_url}", configData.GITLAB.FUNCTION_PIPELINE_URL)
			updateConfigValueCustom("{gitlab_function_token}", configData.GITLAB.FUNCTION_PIPELINE_TOKEN)
			updateConfigValueCustom("{gitlab_api_job_url}", configData.GITLAB.API_PIPELINE_URL)
			updateConfigValueCustom("{gitlab_api_token}", configData.GITLAB.API_PIPELINE_TOKEN)
			updateConfigValueCustom("{gitlab_website_job_url}", configData.GITLAB.WEBSITE_PIPELINE_URL)
			updateConfigValueCustom("{gitlab_website_token}", configData.GITLAB.WEBSITE_PIPELINE_TOKEN)
			updateConfigValueCustom("{gitlab_serverless_job_url}", configData.GITLAB.SERVERLESS_PIPELINE_URL)
			updateConfigValueCustom("{gitlab_serverless_token}", configData.GITLAB.SERVERLESS_PIPELINE_TOKEN)
			updateConfigValueCustomByEnv("{conf-host-name}", configData.AWS.API.HOST_NAMES.DEV, 'dev')
			updateConfigValueCustomByEnv("{conf-host-name}", configData.AWS.API.HOST_NAMES.STG, 'stg')
			updateConfigValueCustomByEnv("{conf-host-name}", configData.AWS.API.HOST_NAMES.PROD, 'prod')
			updateConfigValueCustom("{conf-cas-service-principal-pwd-location}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_SECRETS_LOCATION)
			updateConfigValueCustom("{conf-cas-service-principal-tenantid}", configData.JAZZ.AAD_SERVICE_PRINCIPAL_TENANT_ID)
		}
		
		if ((domain == 'jazz' ) && (service == "gitlab-hook")) {
			sh("sed -i -- 's|{jazzPlatformEventBridge}|${configData.JAZZ.EVENTS.EVENTBRIDGE.DEV}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/functions/config/dev-config.json")
			sh("sed -i -- 's|{jazzPlatformEventBridge}|${configData.JAZZ.EVENTS.EVENTBRIDGE.STG}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/functions/config/stg-config.json")
			sh("sed -i -- 's|{jazzPlatformEventBridge}|${configData.JAZZ.EVENTS.EVENTBRIDGE.PROD}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/functions/config/prod-config.json")
		}

		if ((domain == 'jazz' ) && (service == "workloads")) {
			updateConfigValue("{workloads_api}", configData.JAZZ.WORKLOADS.API)
			updateConfigValue("{workloads_token}", configData.JAZZ.WORKLOADS.TOKEN)
		}

	}
	catch(ex){
		println "Error occured while loading service configuration: " + ex
		throw new Exception("error occured while loading service configuration: " , ex)
	}
}

def updateConfigValueCustomByEnv(key, val, env) {
	def sysEnv = System.getenv()
	sh("sed -i -- 's|${key}|${val}|g' ${PROPS.WORKING_DIRECTORY}/${sysEnv.REPO_NAME}/functions/handlerEvent/config/${env}-config.json")
}

def updateConfigValueCustom(key, val) {
	updateConfigValueCustomByEnv(key, val, 'dev')
	updateConfigValueCustomByEnv(key, val, 'stg')
	updateConfigValueCustomByEnv(key, val, 'prod')
}

def updateConfigValue(key, val) {
	def env = System.getenv()
	sh("sed -i -- 's|${key}|${val}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
	sh("sed -i -- 's|${key}|${val}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
	sh("sed -i -- 's|${key}|${val}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
}

def updateCoreAPI() {
	def configData = JSON.getValueFromPropertiesFile('configData')
	def env = System.getenv()
	sh("sed -i -- 's|{conf-host-name}|${configData.AWS.API.HOST_NAMES.DEV}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/dev-config.json")
	sh("sed -i -- 's|{conf-host-name}|${configData.AWS.API.HOST_NAMES.STG}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/stg-config.json")
	sh("sed -i -- 's|{conf-host-name}|${configData.AWS.API.HOST_NAMES.PROD}|g' ${PROPS.WORKING_DIRECTORY}/${env.REPO_NAME}/config/prod-config.json")
}

//change config data/variables
def setKinesisStream(props, credsId) {
	def serviceConfig = props['serviceConfig']
	def configData = props['configData']
	def environmentLogicalId = props['environmentLogicalId']

	if ( (serviceConfig['service'] == "services-handler") || (serviceConfig['service'] == "events-handler") || (serviceConfig['service'] == "email-event-handler") ) {
		def eventEnvironment = environmentLogicalId == 'prod' || environmentLogicalId == 'stg' ? environmentLogicalId : 'dev'
		def functionName =  "${serviceConfig['domain']}_${serviceConfig['service']}_${environmentLogicalId}"
		def eventSourceList = sh ("aws lambda list-event-source-mappings --query \"EventSourceMappings[?contains(FunctionArn, '$functionName')]\" --region \"${props['deploymentRegion']}\" --profile ${credsId}" , true)
		println "$eventSourceList"
		if (eventSourceList == "[]"){
			sh("aws lambda  create-event-source-mapping --event-source-arn arn:aws:kinesis:${props['deploymentRegion']}:${props['roleId']}:stream/${configData.INSTANCE_PREFIX}-events-hub" + eventEnvironment + " --function-name arn:aws:lambda:${props['deploymentRegion']}:${props['roleId']}:function:$functionName --starting-position LATEST --region ${props['deploymentRegion']} --profile ${credsId}" )
		}
	}
}
