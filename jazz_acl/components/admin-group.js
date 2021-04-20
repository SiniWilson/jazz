
const logger = require("./logger.js"); //Import the logging module.
const request = require("request");

/**
 * admin-group.js
 * @author SWilson162
*/

var adminGroupMembers;

async function getAdminGroupMembers(configData, authorizationToken) {
    return new Promise((resolve, reject) => {

        if (!adminGroupMembers) {
            logger.debug("adminGroupMembers not existing... So getting the list..")
            let payload = {
                uri: `${configData.GRAPH_API_GROUP_MEMBER}`,
                method: "GET",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${authorizationToken}`
                },
                rejectUnauthorized: false
            };


            logger.debug("checkAdminGroup payload: " + JSON.stringify(payload));
            request(payload, function (error, response, body) {
                logger.debug("checkAdminGroup response: " + JSON.stringify(response));
                if (error) {
                    logger.error("Error in getting admin group members details: " + JSON.stringify(error));
                    return reject(error);
                }
                if (response.statusCode && response.statusCode === 200) {
                    logger.debug("Successfully checkAdminGroup");
                    let groupInfo = JSON.parse(response.body).value;
                    adminGroupMembers = groupInfo.map((gp) => {
                        if (gp['@odata.type'] === configData.SERVICE_PRINCIPAL_DATA_TYPE) return gp.appDisplayName.toLowerCase();
                        else return gp.mail.toLowerCase();
                    });
                    logger.debug("adminGroupMembers- " + adminGroupMembers);
                    return resolve(adminGroupMembers);
                } else {
                    logger.error("Failed to get admin group members details. " + JSON.stringify(response));
                    return reject({
                        "error": `Failed to get admin group members details: ${JSON.stringify(response)}`
                    });
                }
            });
        } else {
            logger.debug("adminGroupMembers already cached... So getting from cache..")
            logger.debug("adminGroupMembers- " + adminGroupMembers);
            return resolve(adminGroupMembers);
        }

    });
}


module.exports = {
    getAdminGroupMembers
};
