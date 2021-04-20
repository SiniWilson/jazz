/**
    jazz_acl-authorizer
    @Author: Sini Wilson
    @version: 1.0
 **/

const config = require('./components/config.js'); //Import the environment data.
const logger = require("./components/logger.js"); //Import the logging module.
const errorHandlerModule = require("./components/error-handler.js");
const aclServices = require("./components/acl-services");
const verifyTokenService = require("./components/verify-token");


const errorHandler = errorHandlerModule(logger);

async function handler(event, context, cb) {

    var configData = config.getConfig(event, context);
    var principalIdValue;
    logger.init();

    try {
        logger.debug('Event: ' + JSON.stringify(event));

        if (event.methodArn === undefined || event.methodArn === "") {
            return cb(JSON.stringify(errorHandler.throwInputValidationError('ARN of the resource to be accessed is not provided. Request might not come from API Gateway directly?')));
        }

        let headers = exportable.changeToLowerCase(event.headers);
        logger.debug("headers: " + JSON.stringify(headers))

        if (!headers.authorization) {
            //once we have the authorization modules ready, we can map the token with a specific principal (May be AWS username). For now using a generic principal called ""
            logger.error('No access token, request will be denied! Request event: ' + JSON.stringify(event));
            return cb("Unauthorized");
        }

        var token = headers.authorization;

        // First decode the token and check for admin and users

        let tokenInfo = await exportable.wrapper(verifyTokenService.authorizeRequest(configData, token));
        logger.debug("token info: "+ JSON.stringify(tokenInfo))
        if (tokenInfo.error) {
            logger.error(`Error while decoding token, exiting..`)
            return cb("Unauthorized");
        }
        principalIdValue = tokenInfo.data.principalId;
        let isAdmin = tokenInfo.data.isAdmin;
        logger.debug("Decoded data: "+ principalIdValue);


        let policyObj = await exportable.wrapper(exportable.verifyPolicy(event, principalIdValue, configData, isAdmin));
        if (policyObj.error) {
            logger.error(`Error while creating apigateway policy, exiting..`)
            return cb("Unauthorized");
        } else {
            cb(null, policyObj.data);
        }
    } catch (error) {
        logger.error("Decoding failed: " + JSON.stringify(error));
        return cb("Unauthorized");
    }
};

async function verifyPolicy(event, principalId, configData, isAdmin) {
    try {
        let authResult = await exportable.getAuthorizationDetails(event, configData, principalId, isAdmin);
        if (authResult && authResult.allow) {
            return exportable.generatePolicy(configData, principalId, "allow", event.methodArn, authResult, isAdmin);
        } else {
            return exportable.generatePolicy(configData, principalId, "deny", event.methodArn, authResult, isAdmin);
        }
    } catch (exception) {
        logger.error("Unexpected error occurred while accessing acl API: " + JSON.stringify(exception));
        throw new Error();
    }
}

const wrapper = (promise) => {
    return promise
        .then(data => {
            return { error: false, data: data };
        })
        .catch(err => {
            logger.error('Error in the wrapper function: ' + JSON.stringify(err));
            return { error: true };
        });
}

async function getAuthorizationDetails(event, config, user, isAdmin) {
    let resource = event.path
    let header_key = config.SERVICE_ID_HEADER_KEY.toLowerCase();
    let headers = exportable.changeToLowerCase(event.headers);
    if (event.httpMethod === 'GET' && (resource.indexOf("services") !== -1 || resource.indexOf("statistics") !== -1)) {
        let serviceData = await aclServices.getServiceMetadata(config, user, headers[header_key], isAdmin, logger);
        let allow = false;
        if (event.resource.indexOf("/services/{id}") !== -1) {
            const pathArr = event.path.split('/');
            const serviceId = pathArr[pathArr.length - 1]
            if (serviceId && serviceData.length > 0) {
                let servicePolicies = serviceData.find(service => service.serviceId === serviceId);
                allow = servicePolicies.policies.length > 0 ? true : false;
            }
        } else {
            allow = true; //serviceData can be empty for call to /services unlike a call to /services{id}
        }
        return {
            allow: allow,
            data: serviceData
        };
    } else if (resource.indexOf("acl/policies") !== -1) {
        let permission;
        if (event.httpMethod === 'GET') {
            permission = 'read'
        } else if (event.httpMethod === 'POST') {
            permission = 'admin'
        } else {
            logger.error("Incorrect method for /acl/policies" + event.httpMethod);
            throw new Error("Method not supported");
        }
        let permissionData = await aclServices.checkPermissionData(config, user, headers[header_key], "manage", permission, isAdmin, logger);
        return {
            allow: permissionData.authorized
        };
    } else {
        let category, permission;
        if (event.httpMethod === 'GET') {
            if (resource.indexOf("deployments") !== -1) {
                category = "deploy"
            } else {
                category = "manage";
            }
            permission = "read"
        } else {
            if (resource.indexOf("deployments") !== -1) {
                category = "deploy"
                permission = 'write'
            } else {
                category = "manage";
                permission = 'admin'
                if (resource.indexOf("logs") !== -1 || resource.indexOf("metrics") !== -1 || resource.indexOf("publish-to-clearwater") !== -1) {
                    permission = 'read'
                }
            }
        }
        let permissionData = await aclServices.checkPermissionData(config, user, headers[header_key], category, permission, isAdmin, logger);
        return {
            allow: permissionData.authorized
        };

    }
}

async function generatePolicy(configData, principalId, effect, resource, authResult, isAdmin) {
    return new Promise((resolve, reject) => {
        if (!principalId || principalId === "") {
            return reject("Unauthorized");
        }

        if (!effect || effect === "") {
            return reject("Unauthorized");
        }

        if (!resource || resource === "") {
            return reject("Unauthorized");
        }
        var authResponse = {};
        authResponse.principalId = principalId;

        var policyDocument = {};
        policyDocument.Version = '2012-10-17'; // default version
        policyDocument.Statement = [];

        var statementOne = {};
        statementOne.Action = 'execute-api:Invoke'; // default action
        statementOne.Effect = effect;
        statementOne.Resource = resource;
        statementOne.Condition = {
            "IpAddress": {
                "aws:SourceIp": configData.IP_WHITELIST
            }
        };
        policyDocument.Statement[0] = statementOne;
        authResponse.policyDocument = policyDocument;

        var authContext = {};
        authContext.principalId = principalId;
        authContext.isAdmin = isAdmin;

        if (authResult && authResult.data) {
            authContext.services = JSON.stringify(authResult.data)
        }

        authResponse.context = authContext;

        logger.debug("Policy created: " + JSON.stringify(authResponse))
        return resolve(authResponse);
    });
}


function changeToLowerCase(data) {
    let newArr = {};
    for (let key in data) {
        newArr[key.toLowerCase()] = data[key];
    }
    return newArr;
}

const exportable = {
    handler,
    wrapper,
    generatePolicy,
    getAuthorizationDetails,
    verifyPolicy,
    changeToLowerCase
};

module.exports = exportable;

