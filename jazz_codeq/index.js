/**
Code Quality API
@author: Deepak Babu
@version: 1.0
 **/
'use strict';
const errorHandlerModule = require("./components/error-handler.js"); //Import the error codes module.
const responseObj = require("./components/response.js"); //Import the response module.
const configObj = require("./components/config.js"); //Import the environment data.
const logger = require("./components/logger.js"); //Import the logging module.
const request = require('request');
const moment = require('moment');
const async = require('async');
const _ = require("lodash");
const AWS = require('aws-sdk');

// Load these once per cold-start
// Create a Secrets Manager client
const secretsMgrClient = new AWS.SecretsManager({
	region: process.env.AWS_REGION
});

var sonarSecret = "";
var gitlabToken = "";
var projectId = "";

module.exports.handler = (event, context, cb) => {

	//Initializations
	var event = event;
	var errorHandler = errorHandlerModule();
	var config = configObj.getConfig(event, context);
	logger.init();
	var serviceId;

	var query = {};
	var metrics = [];


	try {

		var pathString;
		let headers = changeToLowerCase(event.headers);
		let header_key = config.SERVICE_ID_HEADER_KEY.toLowerCase();
		
		if (!headers[header_key]) {
			logger.error('No service id provided in  headers');
			return cb(JSON.stringify(errorHandler.throwInputValidationError('No service id provided in headers.')));
		}
		serviceId = headers[header_key];
		logger.debug("serviceId: " + serviceId);
		
		getAPIPath(event.resourcePath, function (err, data) {
			if (err) {
				cb(JSON.stringify(errorHandler.throwInputValidationError(err.errorMessage)));
			} else {
				pathString = data.pathString;
			}
		});

		if (event !== undefined && event.method !== undefined && event.method === 'GET' && pathString === "codeq") {

			var required_fields = config.required_params.map(v => v.toLowerCase());

			loadQuery(event);

			var field;
			var missing_required_fields = [];

			var serviceRepoUrl;
			var fromDate;
			var toDate;
			var limit;
			var offset;
			const DATE_FORMAT = "YYYY-MM-DDTHH:mm:ss"; // 24 hr format
			const APPEND_ZEROS = "-0000";
			// validate required fields
			for (var i = required_fields.length - 1; i >= 0; i--) {
				field = required_fields[i];
				var value = query[field];
				if (value === undefined || value === null || value === "") {
					missing_required_fields.push(field);
				}
			}

			if (missing_required_fields.length > 0) {
				const message = "Following field(s) are required - " + missing_required_fields.join(", ");
				logger.error(message);
				return cb(JSON.stringify(errorHandler.throwInputValidationError(message)));
			}

			loadMetrics(query);

			//if from and to dates are provided validate, else assign default dates
			if (query.from !== undefined && query.from !== null) {

				if (!moment(query.from, moment.ISO_8601, true).isValid()) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("Invalid from date. Date should be in ISO_8601 format [YYYY]-[MM]-[DD]T[hh]:[mm]:[ss]-[SSSS]")));
				} else {
					fromDate = moment(query.from).format(DATE_FORMAT) + APPEND_ZEROS;
				}
			} else {

				fromDate = moment().subtract(1, "days").format(DATE_FORMAT) + APPEND_ZEROS;
			}

			if (query.to !== undefined && query.to !== null) {

				if (!moment(query.to, moment.ISO_8601, true).isValid()) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("Invalid to date. Date should be in ISO_8601 format [YYYY]-[MM]-[DD]T[hh]:[mm]:[ss]-[SSSS]")));
				} else {
					toDate = moment(query.to).format(DATE_FORMAT) + APPEND_ZEROS;
				}
			} else {
				toDate = moment().format(DATE_FORMAT) + APPEND_ZEROS;
			}

			if (moment(fromDate).isAfter(toDate)) {
				return cb(JSON.stringify(errorHandler.throwInputValidationError("From date should be earlier than To date.")));
			}

			if (query.limit !== undefined && query.limit !== null) {
				limit = query.limit;
			} else {
				limit = 100;
			}

			if (query.offset !== undefined && query.offset !== null) {
				offset = query.offset;
			} else {
				offset = 0;
			}

			/**
			 * Getting the service repository
			 */
			if (query.servicerepourl !== undefined && query.servicerepourl !== null) {
				serviceRepoUrl = query.servicerepourl;
				logger.debug('serviceRepoUrl: ' + JSON.stringify(serviceRepoUrl));
				let groupNameStr = serviceRepoUrl.split("https://gitlab.com/")
				let finalStr = groupNameStr[1].split(".git")
				serviceRepoUrl = encodeURIComponent(finalStr[0]);
			} else {
				return cb(JSON.stringify(errorHandler.throwInputValidationError("serviceRepoUrl is not present")));
			}

			var pageSize = limit + offset;

			logger.info("metrics: " + metrics);
			logger.info("from date: " + fromDate);
			logger.info("to date: " + toDate);

			//call sonar
			async.auto({
				get_sonar_creds: function (callback) {
					logger.debug("Getting token for SonarQube...");
					getSonarCredentials(config, function(err, data){
						if(err) {
							callback(err);
						} else {
							logger.debug("got secret details: " + JSON.stringify(data))
							callback(null, {
								"sonarCreds": data.token
							});
						}});
				},
				get_available_metrics: ['get_sonar_creds', (results, callback) => {
					var auth = new Buffer(results.get_sonar_creds.sonarCreds + ':' + '').toString('base64');
						const svcPayload = {
							uri: config.sonar_search_metrics_url,
							method: 'GET',
							headers: {
								'Authorization': "Basic " + auth,
								'Content-Type': 'application/json'
							},
							rejectUnauthorized: false
						};

						logger.debug("get_available_metrics - payload:" + JSON.stringify(svcPayload));
						
						request(svcPayload, (error, response, body) => {
							logger.debug("get_available_metrics - response..." + JSON.stringify(response));
							if(response.statusCode === 200 && body) {	
								const parsedBody = (typeof body === 'string') ? JSON.parse(body) : body;
								const metricKeys = metrics.map(metric => config.metric_map[metric]);
								const foundMetrics = parsedBody.metrics.filter(metric => metricKeys.indexOf(metric.key) !== -1);
								if(foundMetrics.length === 0) {
									callback({
										"report_error": "Configured metrics did not match with available metrics in Sonar.",
										"code": response.statusCode
									});
								} else {
									const selectedMetrics = foundMetrics.map(metric => metric.key);
									callback(null, {
										"selectedMetrics": selectedMetrics
									});
								}
							} else {
								if(response.body) {
									callback({
										"report_error": response.body.errors[0].msg,
										"code": response.statusCode										
									});
									
								} else {
									callback({
										"report_error": "No metrics found from Sonar.",
										"code": response.statusCode
									});
								}
							}
						});
					}
				],
				get_gitlab_secret: function (callback) {
					logger.debug('Getting gitlab secret token');
					getGitlabSecret(config.gitlabTokenPath, function(err, data) {
						if(err) {
							callback(err);
						} else {
							logger.debug("got secret details: " + JSON.stringify(data))
							callback(null, {
								"gitlabToken": data
							});
						}
					})
				},
				get_project_key: ['get_gitlab_secret', function (results, callback) {
					projectId = '';
					logger.debug('Getting the project key from gitlab API');
					let url = `${config.gitlabUrl}/${serviceRepoUrl}`;
					var svcPayload = {
						url: url,
						headers: {
							'Accept': 'application/json',
							'Accept-Charset': 'utf-8',
							'Private-Token': gitlabToken || results.get_gitlab_secret.gitlabToken
						},
						method: 'GET',
						rejectUnauthorized: false
					}
					logger.debug("get gitlab project id : " + JSON.stringify(svcPayload));
					request(svcPayload, function(error, response, body) {
						logger.debug('response: ' + JSON.stringify(response));
						if(response.statusCode === 200 && body) {
							let result = JSON.parse(response.body);
							let gitlabProjectId = result.id;
							projectId = gitlabProjectId;
							callback(null, {
								"projectKey": gitlabProjectId
							});
						} else {
							if(response.body) {
								let errMessage = JSON.parse(response.body)
								callback({
									"report_error": errMessage.message,
									"code": response.statusCode
								});
								
							} else {
								callback({
									"report_error": "No Project found for the repoUrl.",
									"code": response.statusCode
								});
							}
						}
					})
				}],
				get_codeq_report: ['get_sonar_creds', 'get_available_metrics', 'get_project_key', function (results, callback) {
					var auth = new Buffer(results.get_sonar_creds.sonarCreds + ':' + '').toString('base64');

						const metricString = results.get_available_metrics.selectedMetrics.join(",");
						const projectKey = results.get_project_key.projectKey;
						var svcPayload = {
							uri: config.sonar_url + "?metrics=" + metricString + "&from=" + fromDate + "&to=" + toDate + "&component=prj-" + projectKey + "&branch=" + query.physicalid,
							method: 'GET',
							headers: {
								'Authorization': "Basic " + auth,
								'Content-Type': 'application/x-www-form-urlencoded'
							},
							rejectUnauthorized: false
						};
						logger.debug("get_codeq_report..." + JSON.stringify(svcPayload));
						request(svcPayload, function (error, response, body) {
							logger.debug("get_codeq_report response..." + JSON.stringify(response));	

							if (response.statusCode === 200 && typeof body !== undefined && typeof body.data !== undefined) {

								//callback with results
								var sonarResponse = JSON.parse(body);

								getReport(metrics, sonarResponse.measures, function (err, results) {
									callback(err, results);
								});

							} else {
								if(response.body){
									callback({
										"report_error": JSON.parse(response.body).errors[0].msg,
										"code": response.statusCode										
									});
									
								} else{
									callback({
										"report_error": "No response data from SonarQube.",
										"code": response.statusCode
									});
								}	
							}
						});
					}
				]
			}, function (err, results) {

				if (err && err.report_error ) {

					switch (err.code) {
					case 404:
						getReport(metrics, null, function (err, output) {
							if (err) {
								cb(errorHandler.throwInternalServerError(err.report_error));
							} else {
								cb(null, responseObj(output, event.query, projectId));
							}
						});
						break;
					default:
						cb(JSON.stringify(errorHandler.throwInternalServerError("Unable to report metrics for the given input.")));
					}

				} else if (err && err.decrypt_error) {
					logger.error(err.decrypt_error);
					cb(JSON.stringify(errorHandler.throwInternalServerError("Error getting quality report from provider.")));
				} else {
					logger.debug('projectId: ' + JSON.stringify(projectId));
					cb(null, responseObj(results.get_codeq_report, event.query, projectId));
				}
			});

		} else if (event !== undefined && event.method !== undefined && event.method === 'GET' && pathString === "help") {
			logger.debug("code quality help service called.");

			loadQuery(event);
			loadMetrics(query);
			var output = {};
			output.metrics = [];

			for (var q = metrics.length - 1; q >= 0; q--) {
				output.metrics.push({
					"name": metrics[q],
					"description": config.metrics_help[metrics[q]].description,
					"unit": config.metrics_help[metrics[q]].unit,
					"minValue": config.metrics_help[metrics[q]].minValue,
					"maxValue": config.metrics_help[metrics[q]].maxValue
				});
			}

			cb(null, responseObj(output, event.query));
		} else {
			cb(JSON.stringify(errorHandler.throwInputValidationError("Service inputs not defined.")));
		}

	} catch (e) {
		cb(JSON.stringify(errorHandler.throwInternalServerError(e)));
	}

	function getAPIPath(url, callback) {
		var resourcePath = url.split("/");
		if (!resourcePath || resourcePath.length === 0) {
			callback({
				"errorMessage": "Invalid resource path."
			}, null);
		}

		var pathString = resourcePath.pop();
		if (pathString === undefined || pathString === "") {
			callback({
				"errorMessage": "Invalid resource path."
			}, null);
		}

		if (pathString.toLowerCase() === "codeq" || pathString.toLowerCase() === "help") {
			callback(null, {
				"pathString": pathString.toLowerCase()
			});
		} else {
			callback({
				"errorMessage": "Invalid resource path."
			}, null);
		}

	}

	function getReport(metrics, sonarMeasures, callback) {

		if (!metrics) {
			return callback({
				"report_error": "No metrics defined.",
				"code": 500
			});

		} else {
			var output = {metrics: []};
			var record;
			if (sonarMeasures !== null) {

				for (var r = sonarMeasures.length - 1; r >= 0; r--) {
					record = sonarMeasures[r];
					var metricName = _.findKey(config.metric_map, _.partial(_.isEqual, record.metric));
					output.metrics.push({
						"name": metricName,
						"link": config.help_service + "?metrics=" + metricName,
						"values": getHistoryValues(record.history)
					});
				}
			} else {
				for (var n = metrics.length - 1; n >= 0; n--) {
					record = metrics[n];
					output.metrics.push({
						"name": record,
						"link": config.help_service + "?metrics=" + record,
						"values": []
					});
				}
			}

			callback(null, output);
		}

	}

	function loadQuery(event) {
		if (event.query !== undefined && event.query !== {}) {
			var key,
			keys = Object.keys(event.query);
			var n = keys.length;
			while (n--) {
				key = keys[n];
				query[key.toLowerCase()] = event.query[key];
			}
		}
	}

	function loadMetrics(query) {
		//if metrics is in query validate the metrics requested against allowed metrics
		if (query.metrics) {

			metrics = query.metrics.split(",");
			if (metrics.length >= 1) {

				var allowed_metrics = config.allowed_metrics.map(v => v.toLowerCase());
				var invalid_metrics = [];

				let matchedMetrics = config.allowed_metrics.filter(metric => metrics.indexOf(metric) !== -1);

				if (matchedMetrics.length === 0) {
					let message = "All provided metrics are invalid - " + metrics;
					logger.error(message);
					return cb(JSON.stringify(errorHandler.throwInputValidationError(message)));
				} else {
					metrics = matchedMetrics;
				}
			} else {
				return cb(JSON.stringify(errorHandler.throwInputValidationError("Invalid metrics:" + query.metrics)));
			}
		} else {
			metrics = config.allowed_metrics;
		}
	}

	function replaceKeys(obj, find, replace) {
		return Object.keys(obj).reduce(
			(acc, key) => Object.assign(acc, {
				[key.replace(find, replace)]: obj[key]
			}), {});
	}

	function getHistoryValues(valuesArray){
		return valuesArray.map(obj => replaceKeys(obj, 'date', 'ts'));
	}

	function changeToLowerCase(data) {
		let newArr = {};
		for (let key in data) {
			newArr[key.toLowerCase()] = data[key];
		}
		return newArr;
	}

	
	function getSonarCredentials(config, callback) {
		logger.debug("getSonarCredentials")
		if (!sonarSecret) {
			logger.debug("No graph secret available.")
			secretsMgrClient.getSecretValue(
			{
				SecretId: config.CAS_SONAR_SECRETS_LOCATION
			},
			function (err, data) {
				logger.debug("getSonarCredentials- err: " + JSON.stringify(err))
				logger.debug("getSonarCredentials- data: " + JSON.stringify(data))
				if (err) {
				logger.error(err);
				callback({
					"auth_error": true,
					"message": `secret_error: Error occurred while getting the secret from secret store, error: ${err.code}`
				})
				} else {
				// Decrypts secret using the associated KMS CMK.
				// Depending on whether the secret is a string or binary, one of these fields will be populated.
				if ("SecretString" in data) {
					// Secret is stored as JSON in secrets manager
					sonarSecret = JSON.parse(data.SecretString);
					logger.debug("sonarSecret-: " + JSON.stringify(sonarSecret))
					callback(null, sonarSecret);
				} else {
					callback({
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
			callback(null, sonarSecret);
		}	
	}

	/**
	 * Function to retrieve gitlab private token
	 * @param {string} secretLocationPath 
	 */
	function getGitlabSecret (secretLocationPath, callback) {
		logger.debug('getGitlabSecret');
		if(!gitlabToken) {
			logger.debug(`Getting gitlab token from secret store!`)
			secretsMgrClient.getSecretValue({SecretId: secretLocationPath}, function(err, data) {
				if (err) {
					logger.error(err);
					callback({
						"auth_error": true,
						"secret_error": `Error occurred while getting the secret at ${secretLocationPath}, error: ${err.code}`
					})
				}
				else {
					// Decrypts secret using the associated KMS CMK.
					// Depending on whether the secret is a string or binary, one of these fields will be populated.
					if ('SecretString' in data) {
						// Secret is stored as JSON in secrets manager								
						let secret = JSON.parse(data.SecretString)
						logger.debug(`Retrieved secret successfully`);
						if (!secret.token) {
							callback({
								"auth_error": true,
								"secret_error": `Failed while parsing secret JSON string`
							})
						}
						gitlabToken = secret.token;
						callback(null, gitlabToken);
					} else {
						callback({
							"auth_error": true,
							"secret_error": `Failed while parsing secret JSON string`
						})
					}
				}
			});
		} else {
			/**
			 * Do nothing, secret is already available!
			 */
			logger.debug(`Gitlab token is already available, using cached secret!`);
			callback(null, gitlabToken);
		}
	} 

};
