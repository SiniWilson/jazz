// =========================================================================
// Copyright © 2017 T-Mobile USA, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =========================================================================

const request = require("request");
const logger = require("../logger.js");
const AWS = require('aws-sdk');

var gitlabToken;
var deployerList = [];

// Create a Secrets Manager client
const secretsMgrClient = new AWS.SecretsManager({
	region: process.env.AWS_REGION
});

/**
 * Function to add repo permission to respective users
 * @param {object} config 
 * @param {object} serviceInfo 
 * @param {object} policies 
 */
const addRepoPermission = async (config, serviceInfo, policies, deployAccessPolicies) => {
    const secretData = await exportable.getSecret(config.GITLAB_TOKEN_LOCATION);
    logger.info('policies ' + JSON.stringify(policies));
    await exportable.removeSelectedRepoUsers(config, serviceInfo, policies.policiesToBeRemoved);
    // TODO one call to get gitlab project
    const repoName = `${serviceInfo.domain}_${serviceInfo.service}`;
    const repoId = await exportable.getGitLabsProjectId(config, repoName);
    let usersList = [];
    let nonOnboardedUsers = [];
    if(policies.policiesToBeAdded){
        for (const policy of policies.policiesToBeAdded) {
            try {
                let permission = policy.permission.toUpperCase()
                let repoInfo = {
                    "permission": config.ACCESS_LEVEL[permission],
                    "gitlabRepoId": repoId
                };
    
                if (config.PERMISSION_CATEGORIES.includes(policy.category)) {
                    const gitlabUserId = await exportable.getGitlabUserId(config, policy.userId);
                    if(gitlabUserId.onboarded) {
                        repoInfo.gitlabUserId = gitlabUserId.data;
                        let memberRes = await exportable.getGitlabProjectMember(config, repoInfo);
                        if (memberRes.isMember) {
                            await exportable.updateProjectMemberPerms(config, repoInfo);
                        }
                        else {
                            await exportable.addProjectMember(config, repoInfo);
                        }
                        usersList.push(policy.userId);
                    } else {
                        /**
                         * If some users are not onboarded to gitlab
                         * sending those list back to the user
                         */
                        nonOnboardedUsers.push(policy.userId);
                    }
                }
            } catch (e) {
                logger.error("addRepoPermission error: " + JSON.stringify(e));
            }
        }
    }
    /**
     * Only if the user has deploy write access policy
     * we are adding the user to the deployerList
     */
    if(deployAccessPolicies && deployAccessPolicies.policiesToBeAdded && deployAccessPolicies.policiesToBeAdded.length > 0) {
        for (const policy of deployAccessPolicies.policiesToBeAdded) {
            const gitlabUserId = await exportable.getGitlabUserId(config, policy.userId);
            let deployedList = deployerList ? deployerList : [];
            let isDeployerMember = false;
            if(deployedList.indexOf(gitlabUserId.data) > -1) {
                isDeployerMember = true;
            }
            logger.debug('isDeployerMember ' + JSON.stringify(isDeployerMember));
            if(!isDeployerMember) {
                await exportable.addMembertoDeployersList(config, gitlabUserId.data);
                deployerList.push(gitlabUserId.data);
            }
        }
    }

    let msgObj = {};
    if(nonOnboardedUsers.length > 0){
        msgObj = {
            msg: `These are the list of users who are not yet onboarded to gitlab`,
            usersList: nonOnboardedUsers
        }
    } else {
        msgObj = {
            msg: `ACL Policy updated for these users`,
            usersList: usersList
        }
    }
    return msgObj;
};

/**
 * Function to add an user to a particular project
 * @param {object} config 
 * @param {object} repoInfo 
 */
const addProjectMember = async (config, repoInfo) => {
    try {
        let dataString = `user_id=${repoInfo.gitlabUserId}&access_level=${repoInfo.permission}`;
        const url = `${config.HOSTNAME}${config.REPO_BASE_API}${repoInfo.gitlabRepoId}/members/`;
        const payload = getPayload(config, url, 'POST', dataString);
        let response = await sendRequest(payload);
        logger.debug("addProjectMember response: " + JSON.stringify(response));
        return response;
    } catch (e) {
        logger.error("addProjectMember error: " + JSON.stringify(e));
        throw (e);
    };
}

/**
 * Function to update the project/repo with new/updated members
 * @param {object} config 
 * @param {object} repoInfo 
 */
const updateProjectMemberPerms = async (config, repoInfo) => {
    try {
        const url = `${config.HOSTNAME}${config.REPO_BASE_API}${repoInfo.gitlabRepoId}/members/${repoInfo.gitlabUserId}/?access_level=${repoInfo.permission}`;
        const payload = getPayload(config, url, 'PUT', null);
        let response = await sendRequest(payload);
        logger.debug("updateProjectMemberPerms response: " + JSON.stringify(response));
        return response;
    } catch (e) {
        logger.error("updateProjectMemberPerms error: " + JSON.stringify(e));
        throw (e);
    };
}

/**
 * Function to add user as a member to deployerList
 * @param {object} config 
 * @param {string} userId 
 */
const addMembertoDeployersList = async (config, userId) => {
    try {
        let dataString = `user_id=${userId}&access_level=${config.DEPLOYER_GROUP_PERMISSION}`;
        const url = `${config.HOSTNAME}${config.GROUPS_BASE_API}${config.GROUP_ID}/members`;
        const payload = getPayload(config, url, 'POST', dataString);
        let response = await sendRequest(payload);
        logger.debug("addMembertoDeployersList response: " + JSON.stringify(response));
        return response;
    } catch(e) {
        logger.error("something went wrong while adding user to the deployers' list: " + JSON.stringify(e));
        throw (e);
    }
}

/**
 * Function to determine if an user with userId is a member of a project or not
 * @param {object} config 
 * @param {object} repoInfo 
 */
const getGitlabProjectMember = async (config, repoInfo) => {
    try {
        const url = `${config.HOSTNAME}${config.REPO_BASE_API}${repoInfo.gitlabRepoId}/members/all/${repoInfo.gitlabUserId}`;
        const payload = getPayload(config, url, 'GET', null);
        let response = await sendRequest(payload);
        logger.debug("getGitlabProjectMember response: " + JSON.stringify(response));
        if (response.statusCode === 200 || response.statusCode === 201) {
            const results = JSON.parse(response.body);
            /**
             * If any user is added via jazz_acl api via the Jazz_UI, they will have atleast developer level access
             * access-level = 30
             * otherwise those users are not part of the project
             */
            if(results.access_level > 20) {
                return { "isMember": true };
            } else {
                return { "isMember": false };
            }
        }
        else if (response.statusCode === 404) {
            return { "isMember": false };
        }
    } catch (e) {
        logger.error("getGitlabProjectMember error: " + JSON.stringify(e));
        throw (e);
    };
}

/**
 * Function to get gitlab user id of an user from incoming user Email
 * @param {object} config 
 * @param {string} userEmail 
 */
const getGitlabUserId = async (config, userEmail) => {
    try {
        const url = `${config.HOSTNAME}${config.USER_SEARCH_API}${userEmail}`;
        const payload = getPayload(config, url, 'GET', null);
        const response = await sendRequest(payload);
        const results = JSON.parse(response.body);
        logger.debug("getGitlabUserId results: " + JSON.stringify(results));
        if(results.length > 0){
            let returnObj = {'onboarded': true, data: results[0].id}
            return returnObj;
        } else {
            let returnObj = {'onboarded': false}
            return returnObj;
        }
    } catch (e) {
        logger.error("getGitlabUserId error: " + JSON.stringify(e));
        throw (e);
    };
}

/**
 * Function to get gitab project id
 * @param {object} config 
 * @param {object} repoName 
 */
const getGitLabsProjectId = async (config, repoName) => {
    try {
        const url = `${config.HOSTNAME}${config.REPO_BASE_API}?search=${repoName}`;
        const payload = getPayload(config, url, 'GET', null);
        const response = await sendRequest(payload);
        logger.debug("getGitLabsProjectId response: " + JSON.stringify(response));
        const results = JSON.parse(response.body);
        let gitlabProjectId;
        /**
         * Since gitlab search gives substring matching and not exact match results,
         * looping through the results and getting the exact gitlab repo ID
         */
        for(let i=0; i<results.length; i++) {
            if(results[i].name === repoName) {
                gitlabProjectId = results[i].id;
                break;
            }
        }
        return gitlabProjectId;
    } catch (e) {
        logger.error("getGitLabsProjectId error: " + JSON.stringify(e))
        throw (e);
    };
}

/**
 * Function to remove user(s) from a project
 * @param {object} config 
 * @param {object} serviceInfo 
 */
const removeAllRepoUsers = async (config, serviceInfo) => {
    try {
        const repoName = `${serviceInfo.domain}_${serviceInfo.service}`;
        const repoId = await exportable.getGitLabsProjectId(config, repoName);
        const response = await exportable.getAllRepoUsers(config, repoId);
        const users = JSON.parse(response.body);
        let list = [];
        if (users.length > 0) {
            for (const user of users) {
                try {
                    await exportable.removeRepoUser(config, repoId, user.id);
                    list.push(user.name);
                } catch (e) {
                    logger.error("Remove user failed for user : " + user.name);
                }
            }
        }
        logger.debug("Removed users : " + list);
        return list;
    } catch (e) {
        logger.error("removeAllRepoUsers error: " + JSON.stringify(e));
        throw (e);
    }
}

/**
 * Function to remove ONLY selected user(s) from a project
 * @param {obejct} config 
 * @param {object} serviceInfo 
 * @param {array} policies 
 */
const removeSelectedRepoUsers = async (config, serviceInfo, policies) => {
    try {
        const repoName = `${serviceInfo.domain}_${serviceInfo.service}`;
        const repoId = await exportable.getGitLabsProjectId(config, repoName);
        let list = [];
        for (const user of policies) {
            const gitlabUserId = await exportable.getGitlabUserId(config, user.userId);
            try {
                await exportable.removeRepoUser(config, repoId, gitlabUserId.data);
                list.push(user.userId);
            } catch (e) {
                logger.error("Remove user failed for user with id : " + gitlabUserId);
            }
        }
        logger.debug("Removed users : " + list);
        return list;
    } catch (e) {
        logger.error("removeSelectedRepoUsers error: " + JSON.stringify(e));
        throw (e);
    }
}

/**
 * Function to get list of all members of a project
 * @param {object} config 
 * @param {string} gitlabRepoId 
 */
const getAllRepoUsers = async (config, gitlabRepoId) => {
    try {
        const url = `${config.HOSTNAME}${config.REPO_BASE_API}${gitlabRepoId}/members`;
        const payload = getPayload(config, url, 'GET', null);
        let response = await sendRequest(payload);
        logger.debug("getAllRepoUsers response: " + JSON.stringify(response));
        return response;
    } catch (e) {
        logger.error("getAllRepoUsers error: " + JSON.stringify(e));
        throw (e);
    }
}

/**
 * Function to remove one user from a project/repo
 * @param {object} config 
 * @param {string} gitlabRepoId 
 * @param {string} user_id 
 */
const removeRepoUser = async (config, gitlabRepoId, user_id) => {
    try {
        const url = `${config.HOSTNAME}${config.REPO_BASE_API}${gitlabRepoId}/members/${user_id}`;
        const payload = getPayload(config, url, 'DELETE', null);
        let response = await sendRequest(payload);
        logger.debug("removeRepoUser response: " + JSON.stringify(response));
        return response;
    } catch (e) {
        logger.error("removeRepoUser error: " + JSON.stringify(e))
        throw (e);
    }
}

/**
 * Function to execute http requests
 * @param {object} payload 
 */
const sendRequest = async (payload) => {
    return new Promise((resolve, reject) => {
        logger.debug("payload: " + JSON.stringify(payload));
        request(payload, (error, response, body) => {
            logger.debug("Response: " + JSON.stringify(response));
            if (error) {
                logger.error("sendRequest error: " + JSON.stringify(error));
                return reject(error);
            } else {
                if (((response.statusCode === 200 || response.statusCode === 201) && (response.body)) ||
                response.statusCode === 204 || response.statusCode === 404 || response.statusCode === 409 || 
                response.statusCode === 400) { // Adding 404 and 400, since it is a valid condition
                    /**
                     * Handling 400 scenario only for a specific condition
                     * Only for adding jazz Maintainers or Owners at T-Mobile level
                     * Otherwise it is a genuine error
                     */
                    let responseError = false;
                    if(response.statusCode === 400) {
                        if(JSON.parse(response.body).message && JSON.parse(response.body).message.access_level && JSON.parse(response.body).message.access_level[0].indexOf('should be greater than or equal to') > -1) {
                            responseError = false;
                        } else {
                            responseError = true;
                        }
                    }
                    if(responseError) {
                        return reject({ "error": "Error occured while executing request." });
                    } else {
                        return resolve(response);
                    }
                }
                return reject({ "error": "Error occured while executing request." });
            }
        });
    });
}

/**
 * Function to make a payload for http requests
 * @param {object} config 
 * @param {string} url 
 * @param {string} method 
 * @param {string} dataString 
 */
const getPayload = (config, url, method, dataString) => {
    let payload = {
        url: url,
        headers: {
            'Accept': 'application/json',
            'Accept-Charset': 'utf-8',
            'Private-Token': gitlabToken
        },
        method: method,
        rejectUnauthorized: false
    };

    if (dataString) {
        payload.body = dataString;
    }
    return payload;
}

/**
 * Function to retrieve gitlab private token
 * @param {string} secretLocationPath 
 */
const getSecret = async (secretLocationPath) => {
    return new Promise((resolve, reject) => {
        if (!gitlabToken) {
          logger.debug(`Getting gitlab token from secret store!`)
          secretsMgrClient.getSecretValue({SecretId: secretLocationPath}, function(err, data) {
            if (err) {
                logger.error(err)
                return reject({
                    "secret_error": `Error occurred while getting the secret at ${secretLocationPath}, error: ${err.code}`
                });
            }
            else {
                // Decrypts secret using the associated KMS CMK.
                // Depending on whether the secret is a string or binary, one of these fields will be populated.
                if ('SecretString' in data) {
                    // Secret is stored as JSON in secrets manager								
                    const secret = JSON.parse(data.SecretString)
                    logger.debug(`Retrieved secret successfully`);
                    if (!secret.token) {
                      return reject({
                        "secret_error": `Failed while parsing secret JSON string`
                      });
                    }
                    gitlabToken = secret.token;
                    resolve(gitlabToken);
                } else {
                    return reject({
                        "secret_error": `Failed while parsing secret JSON string`
                    });
                }
            }
          });
        } else {
          // token is already available. Using cached secret!
          logger.debug(`Gitlab token is already available, using cached secret!`)
          resolve(gitlabToken);
        }
    });
}

const exportable = {
  addRepoPermission,
  removeAllRepoUsers,
  getGitlabProjectMember,
  getAllRepoUsers,
  removeRepoUser,
  getGitLabsProjectId,
  getGitlabUserId,
  updateProjectMemberPerms,
  addProjectMember,
  getSecret,
  removeSelectedRepoUsers,
  addMembertoDeployersList
};

module.exports = exportable;
