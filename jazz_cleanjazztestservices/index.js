/**
	Clean Jazz test services
	@Author: surya jakhotia
	@version: 1.0
**/

'use strict';

const errorHandlerModule = require('./components/error-handler.js');
const config = require('./components/config.js');
const logger = require("./components/logger.js");
const services = require("./components/services.js");
const servicesByAppName = require("./components/servicesByAppName.js");
const util = require('util');
const rp = require('request-promise');
const AWS = require('aws-sdk');
const request = require('request');

// Load these once per cold-start
// Create a Secrets Manager client
const secretsMgrClient = new AWS.SecretsManager({
	region: process.env.AWS_REGION
});

var errorHandler = errorHandlerModule();
var graphSecret = "";
var graphToken = "";
var graphTokenExpiry;

module.exports.handler = (event, context, cb) => {

  //Initializations
  var configData = config.getConfig(event, context);
 
  
  logger.init();
  logger.debug("incoming event: "+ JSON.stringify(event));
  try {
    // Validate configurations
    validateInput(configData, cb);

    var servicesToBeDeleted = [];
    var deletionFailedServices = [];
    var authHeader;

    getServiceList(configData)
      .then(function (result) {
        let allServices = [];
        // since the output of each promise is an Array, and Promise.all() will return Array[Arrays], 
        // so destructuring the data as one single array of all results/objects
        result.map(item => {
          allServices = allServices.concat(item)
        })
        servicesToBeDeleted = allServices;
        /**
         * Filter our duplicate services from the list
         */
        servicesToBeDeleted = servicesToBeDeleted.reduce((unique, o) => {
          /**
           * unique.some tests whether at least one element in the array
           * matches with the provided service id
           */
          if(!unique.some(obj => obj.id === o.id)) {
            unique.push(o);
          }
          return unique;
        },[]);
        var compiledServices = filterDeletionServices(servicesToBeDeleted);
        logger.debug('compiledServices ' + JSON.stringify(compiledServices));
        servicesToBeDeleted = compiledServices.deletionServices;
        deletionFailedServices = compiledServices.failedServices;

        logger.info('Services that are being deleted: ' + JSON.stringify(servicesToBeDeleted));
        if (servicesToBeDeleted.length > 0) {
          // request for auth header
          return getLoginRequest(configData);
        }
      })
      .then(function (result) {
        if (result) {
          // make request to delete service(s)
          authHeader = result;
          return Promise.all(servicesToBeDeleted.map(function (elem) {
            logger.debug("Ready to submit request for deleting service " + elem.s + " in domain " + elem.d);
            if(elem.id){
              return rp(getServiceDeleteRequest(configData, authHeader, elem.d, elem.s, elem.id));
            }
          }));
        }
      })
      .then(function (results) {
        // Send email with results
        if (results && results.length > 0) {
          logger.debug('Send email for ' + JSON.stringify(results));

          var successful_deletes = servicesToBeDeleted.reduce(function (str, obj) {
            return str + (obj.d + " :: " + obj.s + "<br/>");
          }, '<br/>');


          var failed_deletes = deletionFailedServices.reduce(function (str, obj) {
            return str + (obj.d + " :: " + obj.s + "<br/>");
          }, '<br/>');

          successful_deletes = successful_deletes + "<br/>" + util.format('Following services are marked as deletion_failed which might require manual intervention: %s', failed_deletes);
          
          return rp(getSendEmailRequest(configData, authHeader, util.format(configData.JAZZ_EMAIL_SUBJECT_TEMPLATE, servicesToBeDeleted.length),
            util.format(configData.JAZZ_EMAIL_BODY_TEMPLATE, successful_deletes)));
        } else {
          logger.info("No services to cleanup");
        }
      })
      .then(function () {
        return cb(null, {
          message: 'Finished running cleanjazztestservices!',
          input: event
        });
      })
      .catch(function (err) {
        logger.error(JSON.stringify(err.stack));
        logger.error('Error occured during cleanjazztestservices  ' + err);
        return cb(JSON.stringify(errorHandler.throwInternalServerError("Error occured during cleanjazztestservices.")));
      });

  } catch (e) {
    logger.error(JSON.stringify(e.stack));
    logger.error("Unhandled error during execution: " + JSON.stringify(e));
    return cb(JSON.stringify(errorHandler.throwInternalServerError(JSON.stringify(e))));
  }
};

/**
 * Function to seggregate services based on deletion_failed and other status
 * @param {array} servicesToBeDeleted 
 */
function filterDeletionServices(servicesToBeDeleted) {
  let services = {};
  var deletionServices = servicesToBeDeleted.filter(item => {
    if(item.status != 'deletion_failed'){
      return item
    }
  })
  var failedServices = servicesToBeDeleted.filter(item => {
    if(item.status == 'deletion_failed'){
      return item
    }
  })
  services['deletionServices'] = deletionServices;
  services['failedServices'] = failedServices;
  return services;
}

function validateInput(configData, cb) {
 
  if (configData.CLEANUP_DOMAIN_LIST === undefined || configData.CLEANUP_DOMAIN_LIST === "") {
    logger.error("Error in configuration file. CLEANUP_DOMAIN_LIST is required");
    return cb(JSON.stringify(errorHandler.throwInputValidationError("Error in configuration file")));
  }

  if (configData.JAZZ_SERVICE_HOST === undefined || configData.JAZZ_SERVICE_HOST === "") {
    logger.error("Error in configuration file. JAZZ_SERVICE_HOST is required");
    return cb(JSON.stringify(errorHandler.throwInputValidationError("Error in configuration file")));
  }

  if (configData.JAZZ_DELETE_API === undefined || configData.JAZZ_DELETE_API === "") {
    logger.error("Error in configuration file. JAZZ_DELETE_API is required");
    return cb(JSON.stringify(errorHandler.throwInputValidationError("Error in configuration file")));
  }
}

function getServiceList(configData) {
  // Daily jazztestServices
  let dailyServices = new Promise((resolve, reject) => {
    services.getServiceMetadata(configData, function (error, data) {
      if (error) {
        reject(error);
      } else {
        logger.debug("Daily interal test services that are being deleted: " + JSON.stringify(data));
        resolve(data);
      }
    });
  });
  // Services with unapproved applicationName & with expired allowedTime
  let testService = new Promise((resolve, reject) => {
    servicesByAppName.getTestServiceMetadata(configData, function (error, data) {
      if (error) {
        reject(error);
      } else {
        logger.debug("Services under unapproved application name that are being deleted: " + JSON.stringify(data));
        resolve(data);
      }
    });
  });
  // merge both the list
  return Promise.all([dailyServices, testService])
}

function getLoginRequest(configData) {
  return new Promise(async (resolve, reject) => {
    try {
      let graphCreds = await getSvcPrincipalCredentials(configData);
      logger.debug("got secret details: " + JSON.stringify(graphCreds))
    
      graphToken = await getGraphToken(configData, graphCreds);
      return resolve(graphToken);
    } catch(ex) {
      return reject(ex)
    }
 
  });
}

function getServiceDeleteRequest(configData, authHeader, domain, service, serviceId) {
  logger.debug("domain " + JSON.stringify(domain));
  logger.debug("service " + JSON.stringify(service));
  logger.debug("serviceId " + JSON.stringify(serviceId));
  return {
    uri: configData.JAZZ_SERVICE_HOST + configData.JAZZ_DELETE_API,
    headers: {
      'Authorization': authHeader,
      'Jazz-Service-ID': serviceId
    },
    method: 'POST',
    json: {
      "domain": domain,
      "service_name": service,
      "serviceId": serviceId,
      "userIdentifier": configData.SERVICE_DELETED_BY
    },
    rejectUnauthorized: false
  };
}

function getSendEmailRequest(configData, authHeader, subject, body) {
  return {
    uri: configData.JAZZ_SERVICE_HOST + configData.JAZZ_SENDEMAIL_API,
    headers: { 'Authorization': authHeader },
    method: 'POST',
    json: {
      "from": configData.JAZZ_EMAIL_FROM,
      "to": configData.JAZZ_EMAIL_TO,
      "subject": subject,
      "html": body
    },
    rejectUnauthorized: false
  };
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
  })
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
