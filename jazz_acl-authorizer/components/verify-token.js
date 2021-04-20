
const async = require('async');
const logger = require("./logger.js"); //Import the logging module.
const request = require("request");

/**
 * verify-token.js
 * @author SWilson162
*/
var adminList = []

async function authorizeRequest(configData, token) {
    return new Promise((resolve, reject) => {
        //callback(null, token);
        try {
            async.auto({
                getUser: function (callback) {
                    let payload = {
                        uri: `${configData.GRAPH_API_USER}`,
                        method: "GET",
                        headers: {
                            "Content-Type": "application/json",
                            "Authorization": `Bearer ${token}`
                        },
                        rejectUnauthorized: false
                    };

                    logger.debug("getUser params: " + JSON.stringify(payload))
                    request(payload, function (error, response, body) {
                        logger.debug("getUser response: " + JSON.stringify(response))
                        if (error) {
                            logger.error(error);
                            callback(error);
                        }
                        if (response.statusCode && response.statusCode === 200) {
                            callback(null, { "statusCode": 200, "email": JSON.parse(response.body).mail });
                        } else if (response.statusCode && response.statusCode === 404) {
                            callback(null, { "statusCode": 404 });
                        } else {
                            logger.error("Failed to get user details " + JSON.stringify(response));
                            callback({
                                "auth_error": `Failed to get user details - ${JSON.stringify(response)}`
                            });
                        }
                    });
                },
                getAdminGroupDetails: ['getUser', function (results, callback) {
                    if (results.getUser.statusCode === 200) {
                        logger.debug("its a normal user - 200");
                        let principalId = results.getUser.email
                        logger.debug("Cached admin list: " + JSON.stringify(adminList))
                        if(adminList.indexOf(principalId.toLowerCase()) >= 0) {
                            callback(null, {principalId: principalId, userInAdminCache: true});
                        } else {
                            getAdminGroupDetails(configData, token, (error, data) => {
                                if(error) callback(error)
                                else {
                                    callback(null, {principalId: principalId, groupInfo: data});
                                }
                            })
                        }

                    } else if (results.getUser.statusCode === 404) {
                        logger.debug("its a service principle - 404");
                        getServicePrincipalDetails(configData, token, (error, data) => {
                            if(error) callback(error)
                            else {
                                let principalId = data;
                                logger.debug("Cached admin list: " + JSON.stringify(adminList))
                                if(adminList.indexOf(principalId.toLowerCase()) >= 0) {
                                    callback(null, {principalId: principalId, userInAdminCache: true});
                                } else {
                                    getServicePrincipalAdminGroupDetails(configData, token, (error, data) => {
                                        if(error) callback(error)
                                        else {
                                            callback(null, {principalId: principalId, groupInfo: data});
                                        }
                                    })
                                }
                            }
                        })
                    }
                }],
                verifyAdmin: ['getAdminGroupDetails', function (results, callback) {
                    var groupInfo = results.getAdminGroupDetails.groupInfo;
                    if(!groupInfo && results.getAdminGroupDetails.userInAdminCache) {
                        // Set isAdmin as true when adminCache is explicitly checked
                        callback(null, {principalId: results.getAdminGroupDetails.principalId,isAdmin: true})
                    } else {
                        let adminGroups = groupInfo.value.filter((gp) => gp.displayName === configData.SUPPORT_GROUP)
                        let isAdmin = adminGroups.length > 0 ? true : false;
                        if(isAdmin) {
                            // Caching the user for later use
                            adminList.push(results.getAdminGroupDetails.principalId.toLowerCase())
                        }
                        callback(null, {principalId: results.getAdminGroupDetails.principalId,isAdmin: isAdmin})
                    }              
                }]
            }, function (error, results) {
                if (error) {
                    logger.error("Decoding failed: - "+ JSON.stringify(error));
                    return reject({ "error": 'Forbidden. Invalid token' });
                } else {
                    return resolve(results.verifyAdmin);
                }
            });

        } catch (e) {
            logger.debug(e.message);
            return reject({ "error": 'Internal Server Error.' });
        }
    });
}

function getAdminGroupDetails(configData, authorizationToken, callback) {
    let payload = {
        uri: `${configData.GRAPH_API_GROUP_INFO}`,
        method: "GET",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${authorizationToken}`
        },
        rejectUnauthorized: false
    };


    logger.debug("getAdminGroupDetails payload: " + JSON.stringify(payload));
    request(payload, function (error, response, body) {
        logger.debug("getAdminGroupDetails response: " + JSON.stringify(response));
        if (error) {
            logger.error("Error in getting admin group details: " + JSON.stringify(error));
            callback(error);
        }
        if (response.statusCode && response.statusCode === 200) {
            logger.debug("Successfully getAdminGroupDetails");
            callback(null, JSON.parse(response.body));
        } else {
            logger.error("Failed to get admin group details. " + JSON.stringify(response));
            callback({
                "error": `Failed to get admin group details: ${JSON.stringify(response)}`
            });
        }
    });
}

function getServicePrincipalDetails(configData, authorizationToken, callback) {
    let payload = {
        uri: `${configData.GRAPH_API_SERVICE_PRINCIPAL}`,
        method: "GET",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${authorizationToken}`
        },
        rejectUnauthorized: false
    };


    logger.debug("getServicePrincipalDetails payload: " + JSON.stringify(payload));
    request(payload, function (error, response, body) {
        logger.debug("getServicePrincipalDetails response: " + JSON.stringify(response));
        if (error) {
            logger.error("Error in getting service principal details: " + JSON.stringify(error));
            callback(error);
        }
        if (response.statusCode && response.statusCode === 200) {
            logger.debug("Successfully getServicePrincipalDetails");
            callback(null, JSON.parse(response.body).appDisplayName);
        } else {
            logger.error("Failed to get service principal details. " + JSON.stringify(response));
            callback({
                "error": `Failed to get service principal details: ${JSON.stringify(response)}`
            });
        }
    });
}

function getServicePrincipalAdminGroupDetails(configData, authorizationToken, callback) {
    let payload = {
        uri: `${configData.GRAPH_API_SERVICE_PRINCIPAL_GROUP_INFO}`,
        method: "GET",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${authorizationToken}`
        },
        rejectUnauthorized: false
    };


    logger.debug("getServicePrincipalAdminGroupDetails payload: " + JSON.stringify(payload));
    request(payload, function (error, response, body) {
        logger.debug("getServicePrincipalAdminGroupDetails response: " + JSON.stringify(response));
        if (error) {
            logger.error("Error in getting service principal admin group details: " + JSON.stringify(error));
            callback(error);
        }
        if (response.statusCode && response.statusCode === 200) {
            logger.debug("Successfully getAdminGroupDetails");
            callback(null, JSON.parse(response.body));
        } else {
            logger.error("Failed to get service principal  admin group details. " + JSON.stringify(response));
            callback({
                "error": `Failed to get service principal admin group details: ${JSON.stringify(response)}`
            });
        }
    });
}

module.exports = {
    authorizeRequest
};
