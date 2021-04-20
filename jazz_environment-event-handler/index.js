"use strict";

const config = require("./components/config.js"); //Import the environment data.
const logger = require("./components/logger.js"); //Import the logging module.
const errorHandlerModule = require("./components/error-handler.js");

const _ = require("lodash");
const request = require("request");
const async = require("async");
const nanoid = require("nanoid/generate");
const AWS = require('aws-sdk');

// Load these once per cold-start
// Create a Secrets Manager client
const secretsMgrClient = new AWS.SecretsManager({
	region: process.env.AWS_REGION
});
// Used for caching secrets in memory to avoid additional service calls
var graphSecret = "";
var graphToken = "";
var graphTokenExpiry = "";
    
function handler(event, context, cb)  {
    //Initializations
    var configData = config.getConfig(event, context);
    var errorHandler = errorHandlerModule();

    logger.init();
    logger.debug("Incoming event: "+ JSON.stringify(event));
    //Parse the Event Name and Event Type configuration as a JSON from the config file.
    var event_config = configData.EVENTS;
    logger.debug("Event configuration: " + JSON.stringify(event_config));

    async.series(
        {
           getToken: function (mainCallback) {
               exportable.getSvcPrincipalCredentials(configData, function(err, data){
                    if(err) {
                        mainCallback(err);
                    } else {
                        let graphCreds = data;
                        logger.debug("got secret details: " + JSON.stringify(graphCreds))
                    
                        exportable.getGraphToken(configData, graphCreds, function(err, data){
                            if(err) {
                                mainCallback(err);
                            } else {
                                graphToken = data; 
                                mainCallback(null, {
                                    auth_token: graphToken
                                });
                            }
                        });
                    }
                    
                });
            },
            processEvents: function (mainCallback) {
                async.each(event.Records, function (record, callback) {
                    logger.debug("event.Records: " + JSON.stringify(event.Records));
                    var encodedPayload = record.kinesis.data;
                    var sequenceNumber = record.kinesis.sequenceNumber;

                    async.auto(
                        {
                            checkInterest: function (innerCallback) {

                                var EVT_NAME = record.kinesis.partitionKey;
                                logger.debug("Event Name: " + EVT_NAME);

                                //check if event-name is in the interested event-name list

                                if (_.includes(event_config.EVENT_NAME, EVT_NAME)) {
                                    var EVT_TYPE;
                                    var EVT_STATUS;

                                    var payload = JSON.parse(new Buffer(encodedPayload, "base64").toString("ascii"));
                                    if (payload.Item.EVENT_TYPE && payload.Item.EVENT_TYPE.S) {
                                        EVT_TYPE = payload.Item.EVENT_TYPE.S;
                                    }
                                    logger.debug("Event type: " + EVT_TYPE);

                                    if (!EVT_TYPE) {
                                        //Event Type is not present in the Event data.
                                        logger.debug("No EVENT_TYPE is the event");
                                        innerCallback(null, {
                                            interested_event: false
                                        });
                                    }

                                    //check if event-type is in the interested event-type list
                                    if (_.includes(event_config.EVENT_TYPE, EVT_TYPE)) {
                                        var service_name = payload.Item.SERVICE_NAME.S;
                                        logger.debug('service_name ' + JSON.stringify(service_name));
                                        if (service_name) {
                                            innerCallback(null, {
                                                interested_event: true,
                                                payload: payload,
                                                event_name: EVT_NAME,
                                                event_type: EVT_TYPE
                                            });
                                        } else {
                                            innerCallback({
                                                error: "Service Name not present in Event Payload"
                                            });
                                        }
                                    } else {
                                        //This is not an interesting event_type
                                        logger.debug("Current EVENT_TYPE is not interesting for this handler");
                                        innerCallback(null, {
                                            interested_event: false
                                        });
                                    }
                                } else {
                                    logger.debug("Current EVENT_NAME is not interesting for this handler");
                                    //This is not an interesting event_name
                                    innerCallback(null, {
                                        interested_event: false
                                    });
                                }
                            },
                            getServiceDetails: ["checkInterest", function (results, innerCallback) {
                                if (results.checkInterest.interested_event) {
                                    let serviceName = results.checkInterest.payload.Item.SERVICE_NAME.S;
                                    let serviceContxt = JSON.parse(results.checkInterest.payload.Item.SERVICE_CONTEXT.S);
                                    let domainName = serviceContxt.domain;
                                    logger.debug('service domain ' + JSON.stringify(domainName));
                                    
                                    var svcPayload = {
                                        uri: `${configData.BASE_API_URL}${configData.SERVICE_API_RESOURCE}?service=${serviceName}&domain=${domainName}`,
                                        method: "GET",
                                        headers: {
                                            'Authorization': graphToken
                                        },
                                        rejectUnauthorized: false
                                    };

                                    logger.debug("Request payload for get service details: " + JSON.stringify(svcPayload));
                                    request(svcPayload, function (error, response, body) {
                                        logger.debug("Get service details response: " + JSON.stringify(response));
                                        if (response.statusCode === 200 && typeof body !== undefined) {
                                            let resBody = JSON.parse(body);
                                            if (resBody.data && resBody.data.services && resBody.data.services.length) {
                                                let serviceDetails = resBody.data.services[0];
                                                innerCallback(null, {
                                                    "serviceDetails": serviceDetails
                                                });
                                            } else {
                                                innerCallback({
                                                    error: "No such service exists",
                                                    details: response.body.message
                                                });
                                            }
                                        } else {
                                            innerCallback({
                                                error: "Failed while getting service details",
                                                details: response.body.message
                                            });
                                        }
                                    });
                                }
                            }],
                            populateEnvironmentPayload: [
                                "checkInterest", "getServiceDetails",
                                function (results, innerCallback) {
                                    var environmentPayload = {};

                                    if (results.checkInterest.interested_event) {
                                        var payload = results.checkInterest.payload.Item;
                                        logger.debug("Service metadata from catalog: " + payload.SERVICE_CONTEXT.S);
                                        var serviceContxt = JSON.parse(payload.SERVICE_CONTEXT.S);

                                        if (!serviceContxt) {
                                            logger.debug("No service metadata!");
                                            innerCallback({
                                                error: "Service Context is not defined."
                                            });
                                        }
                                        var username = payload.USERNAME.S;
                                        if (username) {
                                            serviceContxt.created_by = username;
                                        }
                                        var service_name = payload.SERVICE_NAME.S;
                                        if (service_name) {
                                            serviceContxt.service_name = service_name;
                                        }

                                        var required_fields;
                                        var missing_required_fields;
                                      
                                        var serviceId = results.getServiceDetails.serviceDetails.id;
                                        logger.debug('service ID ' + JSON.stringify(serviceId));
                                        let deploymentAccounts = results.getServiceDetails.serviceDetails.deployment_accounts;
                                        if (!serviceId) {
                                            logger.debug("ServiceId is not available");
                                            innerCallback({
                                                error: "Service ID is not available."
                                            });
                                        }

                                        if (results.checkInterest.event_name === event_config.DELETE_ENVIRONMENT) {
                                            required_fields = configData.ENVIRONMENT_UPDATE_REQUIRED_FIELDS;
                                            // validate required fields
                                            missing_required_fields = _.difference(_.values(required_fields), _.keys(serviceContxt));
                                            if (missing_required_fields.length > 0) {
                                                // return inputError
                                                innerCallback({
                                                    error: "Following field(s) are required - " + missing_required_fields.join(", ")
                                                });
                                            } else {
                                                if (serviceContxt.endpoint !== undefined || serviceContxt.endpoint !== null || serviceContxt.endpoint !== "") {
                                                    environmentPayload.endpoint = serviceContxt.endpoint;
                                                }

                                                logger.debug('Environment Logical ID ' + JSON.stringify(serviceContxt.logical_id));

                                                var event_status = payload.EVENT_STATUS.S;
                                                if (event_status === 'STARTED') {
                                                    environmentPayload.status = configData.ENVIRONMENT_DELETE_STARTED_STATUS;
                                                } else if (event_status === 'FAILED') {
                                                    environmentPayload.status = configData.ENVIRONMENT_DELETE_FAILED_STATUS;
                                                } else if (event_status === 'COMPLETED') {
                                                    environmentPayload.status = configData.ENVIRONMENT_DELETE_COMPLETED_STATUS;
                                                }
                                                logger.debug("Environment Payload while processing event - DELETE_ENVIRONMENT: " + JSON.stringify(environmentPayload));

                                                innerCallback(null, {
                                                    environmentPayload: environmentPayload,
                                                    service_name: service_name,
                                                    service_domain: serviceContxt.domain,
                                                    environment_logical_id: serviceContxt.logical_id,
                                                    environment_id: serviceContxt.environment_id,
                                                    service_id: serviceId
                                                });
                                            }
                                        } else if (results.checkInterest.event_name === event_config.UPDATE_ENVIRONMENT) {
                                            required_fields = configData.ENVIRONMENT_UPDATE_REQUIRED_FIELDS;
                                            // validate required fields
                                            missing_required_fields = _.difference(_.values(required_fields), _.keys(serviceContxt));
                                            if (missing_required_fields.length > 0) {
                                                // return inputError
                                                innerCallback({
                                                    error: "Following field(s) are required - " + missing_required_fields.join(", ")
                                                });
                                            } else {
                                                if (serviceContxt.status) {
                                                    environmentPayload.status = serviceContxt.status;
                                                }

                                                if (serviceContxt.endpoint) {
                                                    environmentPayload.endpoint = serviceContxt.endpoint;
                                                }

                                                if (serviceContxt.friendly_name) {
                                                    environmentPayload.friendly_name = serviceContxt.friendly_name;
                                                }

                                                if (serviceContxt.metadata) {
                                                    environmentPayload.metadata = serviceContxt.metadata;
                                                }
                                                // update the deployment_descriptor when available
                                                if (serviceContxt.deployment_descriptor) {
                                                    environmentPayload.deployment_descriptor = serviceContxt.deployment_descriptor;
                                                }

                                                logger.debug("Environment Payload while processing event - UPDATE_ENVIRONMENT: " + JSON.stringify(environmentPayload));

                                                innerCallback(null, {
                                                    environmentPayload: environmentPayload,
                                                    service_name: service_name,
                                                    service_domain: serviceContxt.domain,
                                                    environment_logical_id: serviceContxt.logical_id,
                                                    environment_id: serviceContxt.environment_id,
                                                    service_id: serviceId
                                                });
                                            }
                                        } else if (results.checkInterest.event_name === event_config.UPDATE_ARCHIVED_ENVIRONMENT) {
                                            var event_status = payload.EVENT_STATUS.S;
                                            if (event_status !== 'COMPLETED') {
                                                innerCallback({
                                                    error: "UPDATE_ARCHIVED_ENVIRONMENT is not completed"
                                                });
                                            } else {
                                                var archived_logical_id = serviceContxt.archivedLogicalId
                                                environmentPayload.metadata = {"archived_environments": {[archived_logical_id]:{"status":"archived"}}}
                                                logger.debug("Environment Payload while processing event - UPDATE_ARCHIVED_ENVIRONMENT: " + JSON.stringify(environmentPayload));

                                                innerCallback(null, {
                                                    environmentPayload: environmentPayload,
                                                    service_name: service_name,
                                                    service_domain: serviceContxt.domain,
                                                    environment_id: serviceContxt.environment_id,
                                                    service_id: serviceId
                                                });
                                            }
                                        } else if (
                                            results.checkInterest.event_name === event_config.CREATE_BRANCH ||
                                            results.checkInterest.event_name === event_config.INITIAL_COMMIT
                                        ) {
                                            //Create Environment Event
                                            required_fields = configData.ENVIRONMENT_CREATE_REQUIRED_FIELDS;
                                            // validate required fields
                                            missing_required_fields = _.difference(_.values(required_fields), _.keys(serviceContxt));
                                            logger.debug('Environment Physical ID ' + JSON.stringify(serviceContxt.branch));
                                            if (missing_required_fields.length > 0) {
                                                // return inputError
                                                innerCallback({
                                                    error: "Following field(s) are required - " + missing_required_fields.join(", ")
                                                });
                                            } else {
                                                environmentPayload.service = service_name;
                                                if (serviceContxt.service_type === "sls-app") {
                                                    environmentPayload.deployment_descriptor = []
                                                }
                                                
                                                if (serviceContxt.branch === configData.ENVIRONMENT_INTEGRATION_BRANCH) {
                                                    environmentPayload.logical_id = "integration";
                                                } else {
                                                    var nano_id = nanoid(configData.RANDOM_CHARACTERS, configData.RANDOM_ID_CHARACTER_COUNT);
                                                    //environmentPayload.logical_id = "dev-" + short_id; For backward compatibility
                                                    environmentPayload.logical_id = nano_id + "-dev";
                                                }

                                                if (serviceContxt.domain) {
                                                    environmentPayload.domain = serviceContxt.domain;
                                                }

                                                if (username) {
                                                    environmentPayload.created_by = username;
                                                }

                                                if (serviceContxt.branch) {
                                                    environmentPayload.physical_id = serviceContxt.branch;
                                                }

                                                if (serviceContxt.friendly_name) {
                                                    environmentPayload.friendly_name = serviceContxt.friendly_name;
                                                }
                                                // By default is_public_endpoint is false.
                                                environmentPayload.is_public_endpoint = false;

                                                // Set the account/region as empty by default. Developer will set these from the  UI.
                                                environmentPayload.deployment_accounts = [];

                                                environmentPayload.status = configData.CREATE_ENVIRONMENT_STATUS;
                                                logger.debug("Environment Payload while processing event - CREATE_BRANCH/INITIAL_COMMIT: " + JSON.stringify(environmentPayload));
                                                innerCallback(null, {
                                                    environmentPayload: environmentPayload,
                                                    service_id: serviceId
                                                });
                                            }
                                        } else if (results.checkInterest.event_name === event_config.DELETE_BRANCH) {
                                            required_fields = configData.ENVIRONMENT_UPDATE_REQUIRED_FIELDS;
                                            var domain_name = (serviceContxt.domain) ? serviceContxt.domain : null;
                                            innerCallback(null, {
                                                service_name: service_name,
                                                service_domain: domain_name,
                                                service_id: serviceId
                                            });
                                        }
                                    }
                                }
                            ],

                            getEnvironmentId: [
                                "checkInterest",
                                "populateEnvironmentPayload",

                                function (results, innerCallback) {
                                    var environment_id = results.populateEnvironmentPayload.environment_id;
                                    var service_id = results.populateEnvironmentPayload.service_id;

                                    if (
                                        (results.checkInterest.event_name === event_config.UPDATE_ENVIRONMENT ||
                                            results.checkInterest.event_name === event_config.DELETE_ENVIRONMENT ||
                                            results.checkInterest.event_name === event_config.DELETE_BRANCH) &&
                                        !environment_id
                                    ) {
                                        var svcPayload = {
                                            uri:
                                                configData.BASE_API_URL +
                                                configData.ENVIRONMENT_API_RESOURCE +
                                                "?domain=" +
                                                results.populateEnvironmentPayload.service_domain +
                                                "&service=" +
                                                results.populateEnvironmentPayload.service_name,
                                            method: "GET",
                                            headers: {
                                                'Authorization': graphToken,
                                                'Jazz-Service-ID': service_id
                                            },
                                            rejectUnauthorized: false
                                        };
                                        logger.debug("Request payload while getting environment details: " + JSON.stringify(svcPayload));
                                        requestAPI(svcPayload, function (err, get_results) {
                                            if (err) {
                                                logger.error("Error while updating environment: " + JSON.stringify(err));
                                            }

                                            if (get_results) {
                                                logger.debug("Get environment details response: " + JSON.stringify(get_results));
                                                var env_id;
                                                var serviceContxt = JSON.parse(results.checkInterest.payload.Item.SERVICE_CONTEXT.S);

                                                var env_data = JSON.parse(get_results.environment_output);
                                                if (env_data.data.environment) {
                                                    var env_list = env_data.data.environment;
                                                    for (var count = 0; count < env_list.length; count++) {
                                                        if (
                                                            env_list[count].physical_id === serviceContxt.branch &&
                                                            env_list[count].status !== configData.ENVIRONMENT_DELETE_COMPLETED_STATUS
                                                        ) {
                                                            env_id = env_list[count].id;
                                                        }
                                                    }
                                                    logger.debug("environmentId of branch: " + serviceContxt.branch + " is " + env_id);
                                                    innerCallback(null, {
                                                        environment_id: env_id
                                                    });
                                                }
                                            } else {
                                                innerCallback({
                                                    error: "Error invoking Environment GET API to get environment logical id."
                                                });
                                            }
                                        });
                                    } else if (
                                        (results.checkInterest.event_name === event_config.UPDATE_ENVIRONMENT ||
                                            results.checkInterest.event_name === event_config.UPDATE_ARCHIVED_ENVIRONMENT ||
                                            results.checkInterest.event_name === event_config.DELETE_ENVIRONMENT) &&
                                        environment_id
                                    ) {
                                        innerCallback(null, {
                                            environment_id: environment_id
                                        });
                                    } else if (
                                        results.checkInterest.event_name === event_config.CREATE_BRANCH ||
                                        results.checkInterest.event_name === event_config.INITIAL_COMMIT
                                    ) {
                                        innerCallback(null, {});
                                    } else {
                                        innerCallback({
                                            error: "Error invoking Environment API to get environment logical id."
                                        });
                                    }
                                }
                            ],
                            callEnvironmentAPI: [
                                "checkInterest",
                                "populateEnvironmentPayload",
                                "getEnvironmentId",

                                function (results, innerCallback) {
                                    var svcPayload;
                                    var service_id = results.populateEnvironmentPayload.service_id;

                                    if (
                                        results.checkInterest.event_name === event_config.CREATE_BRANCH ||
                                        results.checkInterest.event_name === event_config.INITIAL_COMMIT
                                    ) {
                                        var environment_physical_id = results.populateEnvironmentPayload.environmentPayload.physical_id.toLowerCase();
                                        if (environment_physical_id === configData.ENVIRONMENT_PRODUCTION_PHYSICAL_ID) {
                                            svcPayload = {
                                                uri: configData.BASE_API_URL + configData.ENVIRONMENT_API_RESOURCE,
                                                method: "POST",
                                                headers: {
                                                    'Authorization': graphToken,
                                                    'Jazz-Service-ID': service_id
                                                },
                                                json: results.populateEnvironmentPayload.environmentPayload,
                                                rejectUnauthorized: false
                                            };

                                            async.parallel(
                                                {
                                                    productionEnvironment: function (callback) {
                                                        var prodEnvPayload = results.populateEnvironmentPayload.environmentPayload;
                                                        prodEnvPayload.logical_id = "prod";
                                                        svcPayload.json = prodEnvPayload;
                                                        logger.debug("Request payload during new environment (prod) creation: " + JSON.stringify(svcPayload));
                                                        requestAPI(svcPayload, function (err, results) {
                                                            callback(err, results);
                                                        });
                                                    },
                                                    stageEnvironment: function (callback) {
                                                        var stgEnvPayload = results.populateEnvironmentPayload.environmentPayload;
                                                        stgEnvPayload.logical_id = "stg";
                                                        svcPayload.json = stgEnvPayload;
                                                        logger.debug("Request payload during new environment (stg) creation: " + JSON.stringify(svcPayload));
                                                        requestAPI(svcPayload, function (err, results) {
                                                            callback(err, results);
                                                        });
                                                    }
                                                },
                                                function (err, results) {
                                                    if (err) {
                                                        logger.error("Error while creating a new environment: " + JSON.stringify(err));
                                                        var error_message;

                                                        if (results.productionEnvironment !== undefined && results.stageEnvironment !== undefined) {
                                                            error_message = "Both prod and stg environment creation failed." + err.error;
                                                            err.error = error_message;
                                                        } else if (results.productionEnvironment !== undefined) {
                                                            error_message = "Production environment creation failed." + err.error;
                                                            err.error = error_message;
                                                        } else if (results.stageEnvironment !== undefined) {
                                                            error_message = "Staging environment creation failed." + err.error;
                                                            err.error = error_message;
                                                        }
                                                    }
                                                    logger.debug("Results for create environment tasks: " + JSON.stringify(results));
                                                    innerCallback(err, results);
                                                }
                                            );
                                        } else {
                                            svcPayload = {
                                                uri: configData.BASE_API_URL + configData.ENVIRONMENT_API_RESOURCE,
                                                method: "POST",
                                                headers: {
                                                    'Authorization': graphToken,
                                                    'Jazz-Service-ID': service_id
                                                },
                                                json: results.populateEnvironmentPayload.environmentPayload,
                                                rejectUnauthorized: false
                                            };

                                            requestAPI(svcPayload, function (err, results) {
                                                if (err) {
                                                    logger.error("Error while creating a new environment: " + JSON.stringify(err));
                                                }

                                                logger.debug("Results for create environment tasks: " + JSON.stringify(results));
                                                innerCallback(err, results);
                                            });
                                        }
                                    } else if (
                                        results.checkInterest.event_name === event_config.UPDATE_ENVIRONMENT ||
                                        results.checkInterest.event_name === event_config.DELETE_ENVIRONMENT
                                    ) {
                                        var updatePayload = {};

                                        svcPayload = {
                                            uri:
                                                configData.BASE_API_URL +
                                                configData.ENVIRONMENT_API_RESOURCE +
                                                "/" +
                                                results.getEnvironmentId.environment_id +
                                                "?domain=" +
                                                results.populateEnvironmentPayload.service_domain +
                                                "&service=" +
                                                results.populateEnvironmentPayload.service_name,
                                            method: "PUT",
                                            headers: {
                                                'Authorization': graphToken,
                                                'Jazz-Service-ID': service_id
                                            },
                                            json: results.populateEnvironmentPayload.environmentPayload,
                                            rejectUnauthorized: false
                                        };

                                        logger.debug("Request payload for update environment: " + JSON.stringify(svcPayload));

                                        if (svcPayload !== undefined) {
                                            requestAPI(svcPayload, function (err, results) {
                                                if (err) {
                                                    logger.error("Error while updating environment: " + JSON.stringify(err));
                                                }

                                                logger.debug("Results for update environment tasks: " + JSON.stringify(results));
                                                innerCallback(err, results);
                                            });
                                        }
                                    } else if (results.checkInterest.event_name === event_config.UPDATE_ARCHIVED_ENVIRONMENT) {
                                        var updatePayload = {};
                                        svcPayload = {
                                            uri:
                                                configData.BASE_API_URL +
                                                configData.ENVIRONMENT_API_RESOURCE +
                                                "/" +
                                                results.getEnvironmentId.environment_id +
                                                "?domain=" +
                                                results.populateEnvironmentPayload.service_domain +
                                                "&service=" +
                                                results.populateEnvironmentPayload.service_name,
                                            method: "PUT",
                                            headers: {
                                                'Authorization': graphToken,
                                                'Jazz-Service-ID': service_id
                                            },
                                            json: results.populateEnvironmentPayload.environmentPayload,
                                            rejectUnauthorized: false
                                        };

                                        logger.debug("Request payload for update archived environment: " + JSON.stringify(svcPayload));

                                        if (svcPayload !== undefined) {
                                            requestAPI(svcPayload, function (err, results) {
                                                if (err) {
                                                    logger.error("Error while updating archived environment: " + JSON.stringify(err));
                                                }

                                                logger.debug("Results for update archived environment tasks: " + JSON.stringify(results));
                                                innerCallback(err, results);
                                            });
                                        }
                                    } else if (results.checkInterest.event_name === event_config.DELETE_BRANCH &&
                                        results.populateEnvironmentPayload &&
                                        results.getEnvironmentId.environment_id) {
                                        var envPayload = {
                                            "service_name": results.populateEnvironmentPayload.service_name,
                                            "domain": results.populateEnvironmentPayload.service_domain,
                                            "version": "LATEST",
                                            "environment_id": results.getEnvironmentId.environment_id
                                        };

                                        var delSerPayload = {
                                            uri: configData.BASE_API_URL + configData.DELETE_ENVIRONMENT_API_RESOURCE,
                                            method: "POST",
                                            headers: {
                                                'Authorization': graphToken,
                                                'Jazz-Service-ID': service_id
                                            },
                                            json: envPayload,
                                            rejectUnauthorized: false
                                        };
                                        requestAPI(delSerPayload, function (err, results) {
                                            if (err) {
                                                logger.error("Error while deleting environment with id: " + envPayload.environment_id + " exception: " + JSON.stringify(err));
                                            }
                                            logger.debug("Results for delete environment tasks: " + JSON.stringify(results));
                                            innerCallback(err, results);
                                        });
                                    } else {
                                        innerCallback({
                                            error: "Error invoking Environment API, payload is not defined."
                                        });
                                    }
                                }
                            ]
                        },
                        function (err, results) {
                            if (err) {
                                logger.error("Error in Inner Callback: " + JSON.stringify(err));
                            }
                            callback();
                        }
                    );
                });
            }
        },
        function (err, results) {
            if (err) {
                logger.error("Error occured: " + JSON.stringify(err));
                cb(err);
            } else {
                cb(null, results);
            }
        }
    );
};

var requestAPI = function (svcPayload, requestCallback) {
    request(svcPayload, function (error, response, body) {
        if (response.statusCode === 200) {
          var output = body;
          if (!output) {
              logger.error("Error returned from environment API: " + JSON.stringify(response));
              requestCallback({
                  error: "Error returned from environment API:" + JSON.stringify(response)
              });
          } else {
          requestCallback(null, {
                  result: "success",
                  environment_output: output
              });
          }
        } else if (response.statusCode === 400) {
            logger.error("Error returned from environment API (response.statusCode== 400): " + JSON.stringify(response));
            requestCallback({
                error: "Error returned from environment API: " + JSON.stringify(response)
            });
        } else {
            logger.error("Error returned from environment API: " + JSON.stringify(error));
            requestCallback({
                error: "Error returned from environment API: " + JSON.stringify(error)
            });
        }
    });
};

function getGraphToken(config, graphCreds, callback) {
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
				callback(error);
			}
			if (response.statusCode && response.statusCode === 200) {
				graphToken = JSON.parse(response.body).access_token;
				// expiry = current time + 59 minutes
				let expiryMinutes = 59;
				let minToMilliseconds = 60000;
				graphTokenExpiry = new Date(new Date().getTime() + expiryMinutes * minToMilliseconds);;
				logger.debug("got graph token: " + graphToken)
				callback(null, graphToken);
			} else {
				logger.error("Invalid/expired credentials. " + JSON.stringify(response));
				callback({
					"auth_error": `Invalid/expired credentials - ${JSON.stringify(response)}`
				});
			}
		});
	} else {
		logger.debug(`Doing nothing, graphToken is already available!`);
		callback(null, graphToken);
	}	
}

function getSvcPrincipalCredentials(config, callback) {
	logger.debug("getSvcPrincipalCredentials")
	if (!graphSecret) {
		logger.debug("No graph secret available.")
			secretsMgrClient.getSecretValue(
			{
				SecretId: config.SVC_PRINCIPAL_PWD_LOCATION
			},
			function (err, data) {
				logger.debug("getSvcPrincipalCredentials- err: " + JSON.stringify(err))
				logger.debug("getSvcPrincipalCredentials- data: " + JSON.stringify(data))
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
						graphSecret = JSON.parse(data.SecretString);
						logger.debug("graphSecret-: " + JSON.stringify(graphSecret))
						callback(null, graphSecret);
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
		callback(null, graphSecret);
	}	
}

const exportable = {
    handler,
    getSvcPrincipalCredentials,
    getGraphToken
};

module.exports = exportable;

