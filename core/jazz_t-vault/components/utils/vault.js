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
const logger = require('../logger.js');

function getVaultToken(configData) {
  return new Promise((resolve, reject) => {
    let payload = {
      uri: `${configData.T_VAULT_API}${global.globalConfig.API.LOGIN}`,
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      json: {
        "username": configData.T_VAULT_USERNAME,
        "password": configData.T_VAULT_PASSWORD
      }
    };

    request(payload, function (error, response, body) {
      if (response.statusCode && response.statusCode === 200 && body && body.client_token) {
        logger.debug("Successfully logged into tvault");
        return resolve(body.client_token);
      } else {
        logger.error("Error in getting vault token: " + JSON.stringify(response));
        return reject({
          "error": "Error in getting vault token: " + response.body.errors
        });
      }
    });
  });
}

function createSafe(safeDetails, configData, vaultToken, onComplete) {

  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}`,
    method: "POST",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "path": `${global.globalConfig.PATH_TO_SAFE}${safeDetails.name.toLowerCase()}`,
      "data": {
        "name": `${safeDetails.name.toLowerCase()}`,
        "owner": `${safeDetails.owner}`,
        "description": `${safeDetails.description}`
      }
    },
    rejectUnauthorized: false
  };


  logger.debug("createSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("createSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully created safe");
      const message = { "message": `Safe ${safeDetails.name} and associated read/write/deny policies are created.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in creating safe: " + JSON.stringify(error));
      return onComplete({
        "error": `Error in creating safe ${safeDetails.name}: ${response.body.errors}`
      });
    }
  });
}

function getSafeDetails(safename, configData, vaultToken, onComplete) {
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}${global.globalConfig.API.GET_SAFE}${safename.toLowerCase()}`,
    method: "GET",
    headers: {
      "vault-token": vaultToken
    },
    rejectUnauthorized: false
  };

  logger.debug("getSafeDetails payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("getSafeDetails response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully got safe details");
      let safeDetails = JSON.parse(body);
      safeDetails.data.roles = safeDetails.data['aws-roles'];
      delete safeDetails.data['aws-roles'];
      return onComplete(null, safeDetails.data);
    } else {
      logger.error("Error in getting safe details: " + JSON.stringify(error));
      return onComplete({
        "error": `Error in getting safe details with safe name ${safename}: ${response.body}`
      });
    }
  });
}

function deleteSafe(safename, configData, vaultToken, onComplete) {
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}${global.globalConfig.API.DELETE_SAFE}${safename.toLowerCase()}`,
    method: "DELETE",
    headers: {
      "vault-token": vaultToken
    },
    rejectUnauthorized: false
  };

  logger.debug("deleteSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("deleteSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully deleted safe. Response: " + JSON.stringify(body));
      const message = { "message": `Safe ${safename} deleted successfully.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in deleting safe: " + JSON.stringify(error));
      return onComplete({
        "error": `Error in deleting safe with safe name ${safename}: ${response.body.errors}`
      });
    }
  });
}

function updateSafe(safeDetails, configData, vaultToken, onComplete) {
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}`,
    method: "PUT",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "path": `${global.globalConfig.PATH_TO_SAFE}${safeDetails.safename.toLowerCase()}`,
      "data": {
        "name": `${safeDetails.safename.toLowerCase()}`,
        "owner": `${safeDetails.owner}`,
        "description": `${safeDetails.description}`
      }
    },
    rejectUnauthorized: false
  };

  logger.debug("updateSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("updateSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully updated safe: " + JSON.stringify(body));
      const message = { "message": `Safe ${safeDetails.safename} updated successfully.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in updating safe. " + JSON.stringify(error));
      return onComplete({
        "error": `Error in updating safe ${safeDetails.safename}: ${response.body.errors}`
      });
    }
  });
}

function createUserInSafe(safeDetails, configData, vaultToken, onComplete) {
  const username = safeDetails.username.replace(/[^a-zA-Z0-9_-]/g, '-');
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}${global.globalConfig.API.SAFE_USERS}`,
    method: "POST",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "path": `${global.globalConfig.PATH_TO_SAFE}${safeDetails.safename.toLowerCase()}`,
      "access": `${global.globalConfig.ACCESS_LEVEL_IN_SAFE}`,
      "username": `${username}`
    },
    rejectUnauthorized: false
  };

  logger.debug("createUserInSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("createUserInSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully created user in safe: " + JSON.stringify(body));
      const message = { "message": `User ${safeDetails.username} is successfully associated with safe ${safeDetails.safename}.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in creating user in safe. " + JSON.stringify(error));
      let errorMessage = (response.body.errors) ? response.body.errors : response.body.messages;
      return onComplete({
        "error": `Error in creating user ${safeDetails.username} in safe ${safeDetails.safename}: ${errorMessage}`
      });
    }
  });
}

function deleteUserFromSafe(safeDetails, configData, vaultToken, onComplete) {
  const username = safeDetails.username.replace(/[^a-zA-Z0-9_-]/g, '-');
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}${global.globalConfig.API.SAFE_USERS}`,
    method: "DELETE",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "path": `${global.globalConfig.PATH_TO_SAFE}${safeDetails.safename.toLowerCase()}`,
      "access": `${global.globalConfig.ACCESS_LEVEL_IN_SAFE}`,
      "username": `${username}`
    },
    rejectUnauthorized: false
  };

  logger.debug("deleteUserFromSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("deleteUserFromSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully deleted user from safe: " + JSON.stringify(body));
      const message = { "message": `User ${safeDetails.username} is successfully removed from safe ${safeDetails.safename}.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in deleting user from safe. " + JSON.stringify(error));
      return onComplete({
        "error": `Error in deleting user ${safeDetails.username} from safe ${safeDetails.safename}: response.body.errors`
      });
    }
  });
}


function getRoleInSafe(safeDetails, configData, vaultToken, onComplete) {
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.GET_ROLE}${safeDetails.rolename}`,
    method: "GET",
    headers: {
      "vault-token": vaultToken
    },
    rejectUnauthorized: false
  };

  logger.debug("getRoleInSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("getRoleInSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200 && body) {
      logger.debug("Successfully got role details: " + JSON.stringify(body));
      const roleResponse = JSON.parse(body);
      let roleDetails = {};
      if (roleResponse) {
        roleDetails.authType = roleResponse.auth_type;
        roleDetails.arns = roleResponse.bound_iam_principal_arn;
        roleDetails.policies = roleResponse.policies;
      }

      return onComplete(null, roleDetails);
    } else {
      logger.error("Error in getting role details. " + JSON.stringify(error));
      return onComplete({
        "error": `Error in getting details of role ${safeDetails.rolename}: ${response.body}`
      });
    }
  });
}

function createRole(roleDetails, configData, vaultToken, onComplete) {
  let role = roleDetails.arn.split("/")[1];
  let accountId = roleDetails.arn.split("/")[0].split(":")[4];
  let rolename = `${accountId}_${role}`;
  roleDetails.rolename = rolename;

  createRoleInVault(roleDetails, configData, vaultToken)
    .then(() => { return createRoleInSafe(roleDetails, configData, vaultToken) })
    .then((result) => {
      logger.info("Successfully created role. ");
      return onComplete(null, result);
    })
    .catch(err => {
      return onComplete(err);
    });
}

function createRoleInSafe(safeDetails, configData, vaultToken) {
  return new Promise((resolve, reject) => {
    let payload = {
      uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}${global.globalConfig.API.SAFE_ROLES}`,
      method: "POST",
      headers: {
        "vault-token": vaultToken
      },
      json: {
        "path": `${global.globalConfig.PATH_TO_SAFE}${safeDetails.safename.toLowerCase()}`,
        "access": `${global.globalConfig.ACCESS_LEVEL_IN_SAFE}`,
        "role": safeDetails.rolename
      },
      rejectUnauthorized: false
    };

    logger.debug("createRoleInSafe payload: " + JSON.stringify(payload));
    request(payload, function (error, response, body) {
      logger.debug("createRoleInSafe response: " + JSON.stringify(response));
      if (response.statusCode && response.statusCode === 200) {
        logger.debug("Successfully created role in safe: " + JSON.stringify(body));
        const message = { "message": `Role ${safeDetails.rolename} is successfully associated with safe ${safeDetails.safename}.` };
        return resolve(message);
      } else {
        logger.error("Error in creating role in safe. " + JSON.stringify(error));
        return reject({
          "error": `Error in creating role ${safeDetails.rolename}  in safe ${safeDetails.safename}: ${response.body.errors}`
        });
      }
    });
  });
}

function createRoleInVault(roleDetails, configData, vaultToken) {
  return new Promise((resolve, reject) => {
    let payload = {
      uri: `${configData.T_VAULT_API}${global.globalConfig.API.VAULT_ROLES}`,
      method: "POST",
      headers: {
        "vault-token": vaultToken
      },
      json: {
        "auth_type": "iam",
        "bound_iam_principal_arn": [roleDetails.arn],
        "policies": [
          "default"
        ],
        "resolve_aws_unique_ids": false,
        "role": roleDetails.rolename
      },
      rejectUnauthorized: false
    };

    logger.info("createRoleInVault payload: " + JSON.stringify(payload));
    request(payload, function (error, response, body) {
      logger.info("createRoleInVault response: " + JSON.stringify(response));
      if (response.statusCode && response.statusCode === 200) {
        logger.info("Successfully created role in t-vault: " + JSON.stringify(body));
        const message = { "message": `Role ${roleDetails.rolename} is successfully created in t-vault.` };
        return resolve(message);
      } else {
        logger.error("Error in creating role in t-vault. " + JSON.stringify(error));
        return reject({
          "error": `Error in creating role ${roleDetails.rolename} in t-vault: ${response.body.errors}`
        });
      }
    });
  });
}

function deleteRoleFromSafe(safeDetails, configData, vaultToken, onComplete) {
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.SAFE}${global.globalConfig.API.SAFE_ROLES}`,
    method: "DELETE",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "path": `${global.globalConfig.PATH_TO_SAFE}${safeDetails.safename.toLowerCase()}`,
      "access": `${global.globalConfig.ACCESS_LEVEL_IN_SAFE}`,
      "role": safeDetails.rolename
    },
    rejectUnauthorized: false
  };

  logger.debug("deleteRoleFromSafe payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("deleteRoleFromSafe response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully deleted role from safe: " + JSON.stringify(body));
      const message = { "message": `Role ${safeDetails.rolename} is successfully removed from safe ${safeDetails.safename}.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in deleting role from safe. " + JSON.stringify(error));
      return onComplete({
        "error": `Error in deleting role ${safeDetails.rolename} from safe ${safeDetails.safename} ${response.body.errors}`
      });
    }
  });
}

function createUserInVault(userDetails, configData, vaultToken, onComplete) {
  const username = userDetails.username.replace(/[^a-zA-Z0-9_-]/g, '-');
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.USERS}`,
    method: "POST",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "username": `${username}`,
      "password": `${userDetails.password}`,
      "policies": `${global.globalConfig.DEFAULT_USER_POLICY}`
    },
    rejectUnauthorized: false
  };

  logger.debug("createUserInVault payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("createUserInVault response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully created user in vault: " + JSON.stringify(body));
      const message = { "message": `User with username ${userDetails.username} is successfully created.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in creating user in vault. " + JSON.stringify(error));
      return onComplete({
        "error": `Error in creating user ${userDetails.username} in vault: ${response.body.errors}`
      });
    }
  });
}

function deleteUserFromVault(userDetails, configData, vaultToken, onComplete) {
  const username = userDetails.username.replace(/[^a-zA-Z0-9_-]/g, '-');
  let payload = {
    uri: `${configData.T_VAULT_API}${global.globalConfig.API.USERS}/${username}`,
    method: "DELETE",
    headers: {
      "vault-token": vaultToken
    },
    json: {
      "username": `${username}`
    },
    rejectUnauthorized: false
  };

  logger.debug("deleteUserFromVault payload: " + JSON.stringify(payload));
  request(payload, function (error, response, body) {
    logger.debug("deleteUserFromVault response: " + JSON.stringify(response));
    if (response.statusCode && response.statusCode === 200) {
      logger.debug("Successfully deleted user from vault: " + JSON.stringify(body));
      const message = { "message": `User with username ${userDetails.username} is successfully deleted.` };
      return onComplete(null, message);
    } else {
      logger.error("Error in deleting user from vault. " + JSON.stringify(error));
      return onComplete({
        "error": `Error in deleting user ${userDetails.username} from vault: ${response.body.errors}`
      });
    }
  });
}

module.exports = {
  getVaultToken,
  createSafe,
  getSafeDetails,
  deleteSafe,
  updateSafe,
  createUserInSafe,
  deleteUserFromSafe,
  getRoleInSafe,
  createRole,
  createRoleInSafe,
  createRoleInVault,
  deleteRoleFromSafe,
  createUserInVault,
  deleteUserFromVault
};
