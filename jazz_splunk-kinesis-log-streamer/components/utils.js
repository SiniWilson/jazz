/**
	Helper functions for Splunk cloudwatch log streamer
	@module: utils.js
	@description: Helper functions for using Regex patterns, etc.
	@author: 
	@version: 1.0
**/
const AWS = require('aws-sdk');
const { v4: UuidV4 } = require('uuid');
const request = require('request');
const logger = require("../components/logger.js");
const global_config = require("../config/global-config.json");
const truncate = require('unicode-byte-truncate');
const jwt = require("jsonwebtoken");
const retry = require('retry');

// Load these once per cold-start
// Create a Secrets Manager client
const secretsMgrClient = new AWS.SecretsManager({
	region: process.env.AWS_REGION
});

// Used for caching secrets in memory to avoid additional service calls
var graphSecret = "";
var graphToken = "";
var graphTokenExpiry;

var serviceCache = new Map();
var environmentCache = new Map();
var logGroupTagCache = new Map();
var adminConfigCache;

// Helper functions
var getInfo = function (messages, patternStr) {
	let pattern = new RegExp(patternStr);
	let result = "";
	if (messages) {
		for (let i = 0, len = messages.length; i < len; i++) {
			let tmp = pattern.exec(messages[i].message);
			if (tmp && tmp[1]) {
				result = tmp[1];
				break;
			}
		}
	}
	return result;
}

var getSubInfo = function (message, patternStr, index) {
	let pattern = new RegExp(patternStr);
	let result = "";
	if (message) {
		let tmp = pattern.exec(message);
		if (tmp && tmp[index]) {
			result = tmp[index];
		}
	}
	return result;
}

var getToken = function (configData) {
	logger.debug("Inside getToken");		
	return new Promise(async (resolve, reject) => {
		try {
			let graphCreds = await getSvcPrincipalCredentials(configData);
			logger.debug("got secret details: " + JSON.stringify(graphCreds))
			
			graphToken = await getGraphToken(configData, graphCreds);
			resolve( graphToken);
		}catch(ex) {
			reject(ex);
		}
	});
}

async function getGraphToken(config, graphCreds) {
	return new Promise(async (resolve, reject) => {
		logger.debug("getGraphToken----")
		// decode graph token and determine expiry
		let expiredToken = true;
		if (graphTokenExpiry) {
			// check with current time
			let currentTime = new Date();
			if (currentTime < graphTokenExpiry) {
				expiredToken = false;
			}
		}
		if (!graphToken || expiredToken) {
			logger.debug("No graph token available.")
			let payload = {
				url: `${config.GRAPH_API_TOKEN_URL}`,
				method: "GET",
				headers: {
					"Content-Type": "application/x-www-form-urlencoded"
				},
				form: {
					grant_type: "client_credentials",
					client_id: graphCreds.username,
					client_secret: graphCreds.password,
					scope: "https://graph.microsoft.com/.default"
				},
				rejectUnauthorized: false
			};

			logger.debug("getGraphToken payload: " + JSON.stringify(payload));
			request(payload, function (error, response, body) {
				logger.debug("getGraphToken response: " + JSON.stringify(response));
				if (error) {
					logger.error("Error in getting graph token: " + JSON.stringify(error));
					return reject(error);
				}
				if (response.statusCode && response.statusCode === 200) {
					graphToken = JSON.parse(response.body).access_token;
					// expiry = current time + 59 minutes
					let expiryMinutes = 59;
					let minToMilliseconds = 60000;
					graphTokenExpiry = new Date(new Date().getTime() + expiryMinutes * minToMilliseconds);;
					logger.debug("got graph token: " + graphToken)
					return resolve(graphToken);
				} else {
					logger.error("Invalid/expired credentials. " + JSON.stringify(response));
					return reject({
						"auth_error": `Invalid/expired credentials - ${JSON.stringify(response)}`
					});
				}
			});
		} else {
			logger.debug(`Doing nothing, graphToken is already available!`);
			return resolve(graphToken);
		}
	});
}

async function getSvcPrincipalCredentials(config) {
	return new Promise(async (resolve, reject) => {
		logger.debug("getSvcPrincipalCredentials")
		if (!graphSecret) {
			logger.debug("No graph secret available.")
			await secretsMgrClient.getSecretValue(
				{
					SecretId: config.SVC_PRINCIPAL_PWD_LOCATION
				},
				function (err, data) {
					logger.debug("getSvcPrincipalCredentials- err: " + JSON.stringify(err))
					logger.debug("getSvcPrincipalCredentials- data: " + JSON.stringify(data))
					if (err) {
						logger.error(err);
						return reject({
							"auth_error": true,
							"message": `secret_error: Error occurred while getting the secret from secret store, error: ${err.code}`
						})
					} else {
						// Decrypts secret using the associated KMS CMK.
						// Depending on whether the secret is a string or binary, one of these fields will be populated.
						if ("SecretString" in data) {
							// Secret is stored as JSON in secrets manager
							graphSecret = JSON.parse(data.SecretString);
							logger.debug("graphSecret-: " + JSON.stringify(graphSecret))
							return resolve(graphSecret);
						} else {
							return reject({
								"auth_error": true,
								"message": `secret_error: Failed while parsing password within the secret`
							})
						}
					}
				}
			);
		} else {
			/**
			 * Do nothing, secret is already available!
			 */
			logger.debug(`Doing nothing, secret (user password) is already available!`);
			return resolve(graphSecret);
		}
	});
}

/**
 * Function to get configDB data
 * @param {object} config 
 * @param {string} token 
 */
var getConfigJson = function (config, token) {
	return new Promise((resolve, reject) => {
		if (!adminConfigCache)
		{
			var config_json_api_options = {
				url: `${config.SERVICE_API_URL}${config.CONFIG_URL}`,
				headers: {
				"Content-Type": "application/json",
				"Authorization": token
				},
				method: "GET",
				rejectUnauthorized: false
			};
			request(config_json_api_options, (error, response, body) => {
				if (error) {
					logger.error("Error while getting admin config from DB: " + JSON.stringify(config_json_api_options) + " with error " + JSON.stringify(error));
					reject(error)
				} else {
					if (response.statusCode && response.statusCode === 200) {
						var responseBody = JSON.parse(body);
						adminConfigCache = responseBody.data;
						logger.info("caching admin config data: " + JSON.stringify(responseBody.data));
						resolve(responseBody.data)
					} else {
						logger.error("Error in retreiving admin config from DB with response: " + JSON.stringify(response));
						reject('Error in retreiving admin config from DB');
					}
				}
			})
		}
		else
		{
			logger.debug("Pulling admin config from cache");
			resolve(adminConfigCache);
		}
	})
}

/**
 * Function to get service ID through service and domain
 * @param {object} config 
 * @param {object} logGroup 
 * @param {object} authToken 
 */
var getServiceID = function (config, logGroup, authToken) {
	var logTags
	var service
	var domain
	var oldService = true;

	/**
	 * Check for new API/Lambda and SLS app
	 */
	logger.debug('logGroup : ' + JSON.stringify(logGroup));
	if((logGroup.split('_').length - 1) >= 2 && logGroup.indexOf('-FN_') == -1){
		logTags = logGroup.split('_');
		domain = logTags[0].split('/');
		domain = domain[domain.length - 1];
		service = logTags[1];
		oldService = false;
	} else {
		oldService = true;
	}

	/**
	 * Call API only for new services
	 * So we can skip unnecessary API calls for old services
	 */

	if(!oldService){
		return new Promise((resolve, reject) => {
			let cacheKey = domain + "_" + service;
			if (!serviceCache[cacheKey])
			{
				var service_api_options = {
					url: `${config.SERVICE_API_URL}${config.SERVICE_URL}?domain=${domain}&service=${service}`,
					headers: {
						"Content-Type": "application/json",
						"Authorization": authToken
					},
					method: "GET",
					rejectUnauthorized: false
				};
				request(service_api_options, (error, response, body) => {
					if (error) {
						logger.error("Error while trying to get service: " + JSON.stringify(service_api_options) + " with error " + JSON.stringify(error));
						reject(error);
					} else {
						if (response.statusCode && response.statusCode === 200) {
							var responseBody = JSON.parse(body);
							logger.debug("Response Body of Service is: " + JSON.stringify(responseBody));
							serviceCache[cacheKey] = responseBody.data.services[0].id;
							resolve(responseBody.data.services[0].id)
						} else {
							logger.error("Service not found for this service and domain: " + JSON.stringify(service_api_options) + " with response " + JSON.stringify(response));
							reject('Service not found for this service and domain');
						}
					}
				})
			} else {
				logger.debug("Pulling service from cache");
				resolve(serviceCache[cacheKey]);
			}
		});
	} else {
		/**
		 * Returning sample Promise as oldService : true
		 * and checking in successive blocks and processing it
		 */
		return new Promise((resolve, reject) => {
			resolve({"oldService": true})
		})
	}
}

/**
 * Function to get environment data using environments API
 * @param {object} config 
 * @param {object} logGroup 
 * @param {string} authToken 
 */
var getEnvData = function (config, logGroup, authToken, serviceID) {
	/**
	 * Returning sample Promise as oldService : true
	 * and checking in successive blocks and processing it
	 */
	if(serviceID.oldService){
		return new Promise ((resolve, reject) => {
			resolve({"oldService": true})
		})
	} else {
		var logTags
		var env
		var service
		var domain

		if((logGroup.split('_').length - 1) >= 2 && logGroup.indexOf('-FN_') == -1){
			logTags = logGroup.split('_');
			env = logTags[logTags.length - 1];
			domain = logTags[0].split('/');
			domain = domain[domain.length - 1];
			service = logTags[1];
		} 

		return new Promise((resolve, reject) => {
			let cacheKey = domain + '_' + service + '_' + env;
			if (!environmentCache[cacheKey]) {
				var env_api_options = {
					url: `${config.SERVICE_API_URL}${config.ENV_URL}/${env}?domain=${domain}&service=${service}`,
					headers: {
						"Content-Type": "application/json",
						"Authorization": authToken,
						"Jazz-Service-ID": serviceID
					},
					method: "GET",
					rejectUnauthorized: false
				};
				request(env_api_options, (error, response, body) => {
					if (error) {
						logger.error("Error while trying to get environments: " + JSON.stringify(env_api_options) + " with error " + JSON.stringify(error));
						reject(error);
					} else {
						if (response.statusCode && response.statusCode === 200) {
							var responseBody = JSON.parse(body);
							logger.debug("Response Body of Environment data is: " + JSON.stringify(responseBody));
							environmentCache[cacheKey] = responseBody.data.environment[0];
							resolve(responseBody.data.environment[0]);
						} else {
							logger.error("Environment not found for: " + JSON.stringify(env_api_options) + " with response " + JSON.stringify(response));
							reject('Environment not found for this service, domain, environment');
						}
					}
				});
			}
			else
			{
				logger.debug("pulling from environment cache");
				resolve(environmentCache[cacheKey]);
			}
		});
	}
}

/**
 * Function to assume role
 * @param {object} configData 
 * @param {object} envData 
 */
var assumeRole = function (configData, envData) {
	var isPrimary, roleArn;
	/**
	 * Returning sample Promise as oldService : true
	 * and checking in successive blocks and processing it
	 */
	if(envData && envData.oldService){
		return new Promise ((resolve, reject) => {
			resolve({"oldService": true});
		})
	} else {
		if(envData){
			isPrimary = checkIsPrimary(envData.deployment_accounts[0].account, configData);
			roleArn = getRolePlatformService(envData.deployment_accounts[0].account, configData);
		}
		var accessparams;
		return new Promise((resolve, reject) => {
			if(envData){
				if (isPrimary) {
					accessparams = {};
					resolve(accessparams);
				} else {
					const sts = new AWS.STS({ region: process.env.REGION });
					const roleSessionName = UuidV4();
					const params = {
						RoleArn: roleArn,
						RoleSessionName: roleSessionName,
						DurationSeconds: 3600,
					};
					sts.assumeRole(params, (err, data) => {
					if (err) {
						logger.error("Error on assume role " + JSON.stringify(params) + " with error: " + JSON.stringify(err));
						reject({
						"result": "serverError",
						"message": "Unknown internal error occurred"
						});
					} else {
						accessparams = {
							accessKeyId: data.Credentials.AccessKeyId,
							secretAccessKey: data.Credentials.SecretAccessKey,
							sessionToken: data.Credentials.SessionToken,
						};
						resolve(accessparams)
					}
					})
				}
			} else {
				// if envData is undefined or null
				logger.error('Env data is undefined or null');
				reject('Env data is undefined or null');
			}
		})
	}
}

function faultTolerantLogGroupTags(params, cloudwatchlogs, cb) {
	// with explicit options ref: http://adilapapaya.com/docs/retry/#tutorial
	var operation = retry.operation({
		retries: 5,
		factor: 2,
		minTimeout: 100,
		maxTimeout: 1000,
		randomize: false
	});	
	operation.attempt(function(currentAttempt) {
		cloudwatchlogs.listTagsLogGroup(params, function(err, data) {
			if (operation.retry(err)) {
				return;
			}
			cb(err ? operation.mainError() : null, data);
		});
	});
}

/**
 * Function to get tags from log group
 * @param {string} logGroupName
 * @param {object} tempCreds
 * @param {object} envData
 */
var getLogsGroupsTags = function (logGroupName, tempCreds, envData) {
	/**
	 * Returning sample Promise as oldService : true
	 * and checking in successive blocks and processing it
	 */
	if(tempCreds.oldService) {
		return new Promise((resolve, reject) => {
			resolve({"oldService": true})
		})
	} else {
		if(envData){
			tempCreds.region = envData.deployment_accounts[0].region;
		}
		var cloudwatchlogs = new AWS.CloudWatchLogs(tempCreds);
		var params = {
			logGroupName: logGroupName /* required */
		};
		return new Promise((resolve, reject) => {
			if (!logGroupTagCache[logGroupName])
			{
				faultTolerantLogGroupTags(params, cloudwatchlogs, function(err, data) {
					if (err) {
						logger.error("Error while getting LogGroupTags: " + JSON.stringify(params) + " with error " + JSON.stringify(err));
						reject(err)
					} else {
						logger.debug(`tags for log group - ${logGroupName}: ` + JSON.stringify(data))
						logGroupTagCache[logGroupName] = data;
						resolve(data)
					} 
				});
			}
			else
			{
				logger.debug("Pulling log group tags from cache");
				resolve(logGroupTagCache[logGroupName]);
			}
		});
	}
}

/**
 * Function to check if account is primary or not
 * @param {string} accountId 
 * @param {object} jsonConfig 
 */
var checkIsPrimary = function (accountId, jsonConfig) {
	var data = jsonConfig.config.AWS.ACCOUNTS;
	var index = data.findIndex(x => x.ACCOUNTID == accountId);
	if (data[index].PRIMARY) {
	  return data[index].PRIMARY;
	} else {
	  return false;
	}
}

/**
 * Function to get roleArn for specific accountId
 * @param {string} accountId 
 * @param {object} jsonConfig 
 */
var getRolePlatformService = function (accountId, jsonConfig) {
	var data = jsonConfig.config.AWS.ACCOUNTS;
	var index = data.findIndex(x => x.ACCOUNTID == accountId);
	return data[index].IAM.PLATFORMSERVICES_ROLEID;
}

var getCommonData = function (payload, config) {
	return new Promise((resolve, reject) => {
		
		/**
		 * get token for calling respective APIs
		 */
		getToken(config)
		.then((creds) => {
			/**
			 * get configDB data for getting roleArn specific to account
			 */
			getConfigJson(config, creds)
			.then((configData) => {
				/**
				 * get serviceID through service and domain
				 */
				getServiceID(config, payload.logGroup, creds)
				.then((serviceID) => {
					/**
					 * get accountId and region through service Data
					 */
					getEnvData(config, payload.logGroup, creds, serviceID)
					.then((envData) => {
						/**
						 * execute sts:assumeRole
						 */
						assumeRole(configData, envData)
						.then((tempCreds) => {
							/**
							 * configure cloudwatch and retrieve tags from logGroup
							 */
							getLogsGroupsTags(payload.logGroup, tempCreds, envData)
							.then((tagsResult) => {
								let data = {};
								data.metadata = {};
								data.request_id = getInfo(payload.logEvents, global_config.PATTERNS.Lambda_request_id);
								data.provider = "aws";
								data.assetType = "lambda";
								let domainAndservice, serviceInfo;
								if (data.request_id) {
									/**
									 * If tags present, then fetching respective information
									 * Otherwise fallback to old logic
									 */
									logger.debug('tagsResult : ' + JSON.stringify(tagsResult));
									data.assetName = payload.logGroup.split(global_config.PATTERNS.assetNameKey)[1];
									if(tagsResult != 'error' && tagsResult.tags && tagsResult.tags.EnvironmentId && tagsResult.tags.Domain && tagsResult.tags.Service){
										data.environment = tagsResult.tags.EnvironmentId;
										data.namespace = tagsResult.tags.Domain;
										data.service = tagsResult.tags.Service;
										if (tagsResult.tags.FunctionName !== undefined && tagsResult.tags.FunctionName !== null && tagsResult.tags.FunctionName !== "")
										{
											data.assetName = tagsResult.tags.FunctionName;
										}
									} else {
										data.environment = getSubInfo(payload.logGroup, global_config.PATTERNS.Lambda_environment, 2);
										if (data.environment === "dev") {
											let dev_environment = getSubInfo(payload.logGroup, global_config.PATTERNS.Lambda_environment_dev, 2);
											serviceInfo = getSubInfo(payload.logGroup, global_config.PATTERNS.Lambda_environment_dev, 1);
											data.environment = dev_environment;
										} else {
											serviceInfo = getSubInfo(payload.logGroup, global_config.PATTERNS.Lambda_domain_service, 1);
										}
	
										domainAndservice = serviceInfo;
										if(serviceInfo.indexOf(global_config.PATTERNS.sls_app_function) !== -1 ) { // if yes then sls-app function
											let serviceInfoArr = serviceInfo.split(global_config.PATTERNS.sls_app_function);
											domainAndservice = serviceInfoArr[0];
										}
	
										logger.debug("domainAndservice: " + domainAndservice)
	
										let namespace = domainAndservice.substring(0, domainAndservice.indexOf("_"));
										if (namespace) {
											data.namespace = namespace;
											data.service = domainAndservice.substring(namespace.length + 1);
										} else {
											data.namespace = "";
											data.service = domainAndservice;
										}
									}
									data.metadata.platform_log_group = payload.logGroup;
									data.metadata.platform_log_stream = payload.logStream;
									resolve(data);
								} else {
									resolve(data);
								}
							})
							.catch(error => {
								logger.error('Error in retreiving tags from logGroup:' + JSON.stringify(error));
								logger.error(error.stack);
								reject(null);
							});
						})
						.catch(error => {
							logger.error('Error in executing sts:assumeRole:' + JSON.stringify(error));
							logger.error(error.stack);
							reject(null);
						});
					})
					.catch(error => {
						logger.error('Error in retrieving service details:' + JSON.stringify(error));
						logger.error(error.stack);
						reject(null);
					});
				})
				.catch(error => {
					logger.error('Error in retrieving service id:' + JSON.stringify(error));
					logger.error(error.stack);
					reject(null);
				});
			})
			.catch(error => {
				logger.error('Error in retreiving admin config from DB:' + JSON.stringify(error));
				logger.error(error.stack);
				reject(null);
			});
		})
		.catch(error => {
			logger.error('Error in retrieving token:' + JSON.stringify(error));
			logger.error(error.stack);
			reject(null);
		});
		
	});
}

var transformApiLogs = function (payload) {
	return new Promise((resolve, reject) => {
		let data = {},
		bulkRequestBody = '';
		data.metadata = {};
		data.event_timestamp = new Date();
		data.provider = "aws";
		data.assetType = "apigateway";
		data.metadata.platform_log_group = payload.logGroup;
		data.metadata.platform_log_stream = payload.logStream;
		data.environment = getSubInfo(payload.logGroup, global_config.PATTERNS.environment, 2);
		data.request_id = getInfo(payload.logEvents, global_config.PATTERNS.request_id);
		data.metadata.method = getInfo(payload.logEvents, global_config.PATTERNS.method);
		if (!data.metadata.method) {
			// Cloudwatch do not have method info for get!
			data.metadata.method = "GET";
		}

		let apiDomainAndService = getInfo(payload.logEvents, global_config.PATTERNS.domain_service);
		let apiDomain = apiDomainAndService.substring(0, apiDomainAndService.indexOf("/"));

		if (apiDomain) {
			data.namespace = apiDomain;
			data.service = apiDomainAndService.substring(apiDomain.length + 1);
		} else {
			data.namespace = "";
			data.service = apiDomainAndService;
		}
		data.assetName = data.metadata.method + "/" + apiDomainAndService;

		data.metadata.path = getInfo(payload.logEvents, global_config.PATTERNS.path);
		data.metadata.application_logs_id = getInfo(payload.logEvents, global_config.PATTERNS.lambda_ref_id);
		if (!data.metadata.application_logs_id) {
			data.metadata.application_logs_id = "_incomplete_req";
		}
		let method_req_headers = getInfo(payload.logEvents, global_config.PATTERNS.method_req_headers);
		data.metadata.origin = getSubInfo(method_req_headers, global_config.PATTERNS.origin, 1);
		data.metadata.host = getSubInfo(method_req_headers, global_config.PATTERNS.host, 1);
		if (!data.metadata.host) {
			data.metadata.host = "_incomplete_req";
		}
		data.metadata.user_agent = getSubInfo(method_req_headers, global_config.PATTERNS.user_agent, 1);
		data.metadata.x_forwarded_port = getSubInfo(method_req_headers, global_config.PATTERNS.x_forwarded_port, 1);
		data.metadata.x_forwarded_for = getSubInfo(method_req_headers, global_config.PATTERNS.x_forwarded_for, 1);
		data.metadata.x_amzn_trace_id = getSubInfo(method_req_headers, global_config.PATTERNS.x_amzn_trace_id, 1);
		data.metadata.content_type = getSubInfo(method_req_headers, global_config.PATTERNS.content_type, 1);
		data.metadata.cache_control = getSubInfo(method_req_headers, global_config.PATTERNS.cache_control, 1);
		data.log_level = "INFO"; // Default to INFO for apilogs
		data.metadata.status = getInfo(payload.logEvents, global_config.PATTERNS.status);

		if (data.request_id && data.service) {
			bulkRequestBody = {
				sourcetype: "apilogs",
				event: data
			};
			logger.debug("Splunk payload for API Gateway LogEvent: " + JSON.stringify(bulkRequestBody));
			resolve(bulkRequestBody);
		} else {
			logger.error("Invalid api logs event: " + JSON.stringify(payload) + " data is: " + JSON.stringify(data));
			reject({
				result: "inputError",
				message: "Invalid api logs event."
			});
		}
	});
}

var transformLambdaLogs = function (logEvent, commonData) {
	return new Promise((resolve, reject) => {
		if (Object.keys(commonData).length && commonData.service) {
			try {
				let data = {};
				data.metadata = {};
				Object.keys(commonData).forEach(key => {
					data[key] = commonData[key];
				});
				data.request_id = getSubInfo(logEvent.message, global_config.PATTERNS.guid_regex, 0);
				data.event_timestamp = new Date(1 * logEvent.timestamp).toISOString();
				let message = logEvent.message;
				let messageLength = Buffer.byteLength(message, 'utf8');
				if (messageLength > 32766) { //since 32766(32KB) is the default message size
					let truncatedMessage = truncate(message, 32740); // message size + ...[TRUNCATED]
					data.message = truncatedMessage + "  ...[TRUNCATED]";
				} else {
					data.message = message;
				}

				data.log_level = getSubInfo(logEvent.message, global_config.PATTERNS.log_level, 0);
				if (!data.log_level) {
					data.log_level = global_config.DEFAULT_LOG_LEVEL;
				}

				if (!(data.message.startsWith("REPORT") || data.message.startsWith("START") || data.message.startsWith("END"))) {
					let timestmp = getSubInfo(data.message, global_config.PATTERNS.timestamp_pattern, 0);
					data.message = data.message.replace(timestmp, "");
					let guid = getSubInfo(data.message, global_config.PATTERNS.guid_regex, 0);
					data.message = data.message.replace(guid, "");
					data.message = data.message.replace(data.log_level, "");
				}

				data.message = data.message.trim();
				// try to parse JSON
				try {
					let messageObj = JSON.parse(data.message);
					data.message = messageObj;
				} catch (err) {
					logger.debug("Log Message is not JSON: " + data.message);
				}
				let bulkRequestBody = {
					sourcetype: "applicationlogs",
					event: data
				};
				logger.debug("Splunk payload for Lambda LogEvent: " + JSON.stringify(bulkRequestBody));
				resolve(bulkRequestBody);
			} catch(e) {
				logger.error(e.stack);
			}
			
		} else {
			logger.error("Invalid lambda logs event: " + JSON.stringify(logEvent) + " commonData: " + JSON.stringify(commonData));
			reject({
				result: "inputError",
				message: "Invalid lambda logs event."
			});
		}
	});
}

module.exports = {
	getCommonData,
	transformApiLogs,
	transformLambdaLogs
};
