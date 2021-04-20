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

const errorHandler = require("./components/error-handler.js")();
const configModule = require("./components/config.js");
const logger = require("./components/logger.js");
const validation = require("./components/validation.js");
const casbinUtil = require("./components/casbin.js");
const adminGroupUtil = require("./components/admin-group.js");
const util = require("./components/util.js");
const scmUtil = require("./components/scm/index.js");
const services = require("./components/scm/services.js");
const auth = require("./components/scm/login.js");
const globalConfig = require("./config/global-config.json");
const AWS = require('aws-sdk');

var graphSecret;
var graphTokenExpiry;
var graphToken;

// Create a Secrets Manager client
const secretsMgrClient = new AWS.SecretsManager({ 
  region: process.env.AWS_REGION
});

async function handler(event, context) {

  //Initializations
  const config = configModule.getConfig(event, context);
  logger.init();
  logger.debug("event: "+ JSON.stringify(event));


  try {
    validation.validateBasicInput(event);
    const aclResult = await exportable.processACLRequest(event, config);
    logger.debug("final result: " + JSON.stringify(aclResult));
    return {
      data: aclResult
    };
  } catch (err) {
    logger.error(err.message);
    return {
      data: err.message
    };
  }
};

async function processACLRequest(event, config) {
  let resourcePath = event.resourcePath;
  let path = event.path;

  //1. POST - add and delete the policy
  if (event.method === 'POST' && resourcePath.indexOf("policies") !== -1) {
    validation.validatePostPoliciesInput(event);

    const serviceId = event.body.serviceId;
    let result = {};
    let response = 'only updated policies';

    //add policies
    if (event.body.policies && event.body.policies.length) {
      logger.debug("input policies: " + JSON.stringify(event.body.policies));
      const policies = util.createRule(event.body.policies, globalConfig.POLICY);
      logger.debug("rule policies: " + JSON.stringify(policies));
      result = await casbinUtil.addOrRemovePolicy(serviceId, config, 'add', policies);
      logger.debug("add policy result: " + JSON.stringify(result));
      if (result && result.error) {
        throw (errorHandler.throwInternalServerError(`Error adding the policy for service ${serviceId}. ${result.error}`));
      }
      logger.debug("policyOnly: " + JSON.stringify(event.body.policyOnly));
      if(result && result.policySet && !event.body.policyOnly) {
        let updatedPolicies = util.checkIfPoliciesExist(result.policySet);
        let deployAccessPolicies = util.checkDeployAccessPolicies(result.policySet);
        logger.debug("updatedPolicies: " + JSON.stringify(updatedPolicies));
        /**
         * we would want to call the gitlab APIs ONLY if there are code access changes OR deploy access changes in the policies
         */
        if ((updatedPolicies && (updatedPolicies.policiesToBeRemoved.length > 0 || updatedPolicies.policiesToBeAdded.length > 0)) || 
        (deployAccessPolicies && (deployAccessPolicies.policiesToBeAdded && deployAccessPolicies.policiesToBeAdded.length > 0))){
          response = await exportable.processScmPermissions(config, serviceId, updatedPolicies, 'add', deployAccessPolicies);
          logger.debug("add scm response: " + JSON.stringify(response));
        }
      }
    } else {//delete policies
      result = await casbinUtil.addOrRemovePolicy(serviceId, config, 'remove', event.body.policies);
      logger.debug("remove policy result: " + JSON.stringify(result));

      if (result && result.error) {
        throw (errorHandler.throwInternalServerError(result.error));
      }

      response = await exportable.processScmPermissions(config, serviceId, null, 'remove');
      logger.debug("remove scm response: " + JSON.stringify(response));
    }

    return { success: true, data: response };
  }

  //2. GET the policy for the given service id
  if (event.method === 'GET' && resourcePath.indexOf("policies") !== -1) {

    validation.validateGetPoliciesInput(event);
    const serviceId = event.query.serviceId;
    const result = await casbinUtil.getPolicies(serviceId, config);

    if (result && result.error) {
      throw (errorHandler.throwInternalServerError(result.error));
    }

    let policies = [];
    result.forEach(policyArr =>
      policyArr.forEach(policy => policies.push({
        userId: policy[0],
        permission: policy[2],
        category: policy[1]
      })
      ));

    return {
      serviceId: serviceId,
      policies: policies
    };
  }

  //3. GET the permissions for a given user
  if (event.method === 'GET' && resourcePath.indexOf("services") !== -1) {
    validation.validateGetServicesInput(event);
    let result;
    await exportable.getAuthToken(config);
    let adminUsers = await adminGroupUtil.getAdminGroupMembers(config, graphToken);
    let admin = false;
    if (event.query.userId && adminUsers.indexOf(event.query.userId.toLowerCase()) >=0) {
        admin = true;
    }
    if (path && path.serviceId) {
      result = await casbinUtil.getPolicyForServiceUser(path.serviceId, event.query.userId, config, admin);
    } else {
      result = await casbinUtil.getPolicyForUser(event.query.userId, config, admin);
    }

    if (result && result.error) {
      throw (errorHandler.throwInternalServerError(result.error));
    }

    return result;
  }

  //4. GET the permissions for a specific service for a given user
  if (event.method === 'GET' && resourcePath.indexOf("checkpermission") !== -1) {

    
    validation.validateGetCheckPermsInput(event);
    const query = event.query;
    await exportable.getAuthToken(config);
    let adminUsers = await adminGroupUtil.getAdminGroupMembers(config, graphToken);
    let admin = false;
    if (query.userId && adminUsers.indexOf(query.userId.toLowerCase()) >=0) {
        admin = true;
    }
    const result = await casbinUtil.checkPermissions(query.userId, query.serviceId, query.category, query.permission, config, admin);
    logger.debug("checkPermissions res: " + JSON.stringify(result));
    if (result && result.error) {
      throw (errorHandler.throwInternalServerError(result.error));
    }

    return result;
  }
}


/**
 * Function to retrieve scm data from service Data
 * @param {object} serviceData 
 */
function getScmDetails(serviceData) {
  try {
    let scmObj = {};
    scmObj['scmType'] = serviceData.metadata.scmType
    scmObj['scmManaged'] = serviceData.metadata.scmManaged
    return scmObj;
  } catch(e){
    throw (e);
  }
}

async function processScmPermissions(config, serviceId, policies, key, deployAccessPolicies = null) {
  try {
    let scm = new scmUtil(globalConfig);
    await exportable.getAuthToken(config);
    let serviceData = await services.getServiceMetadata(config, graphToken, serviceId);
    logger.debug('serviceData ' + JSON.stringify(serviceData));
    let scmDetails = getScmDetails(serviceData);
    let res = await scm.processScmPermissions(serviceData, policies, key, scmDetails, deployAccessPolicies);
    return (res);
  } catch (ex) {
    throw (JSON.stringify(errorHandler.throwInternalServerError(ex)));
  }
}

async function getAuthToken(config) {
    try {
      graphSecret = await auth.getSecret(secretsMgrClient, config, graphSecret)
      let tokenInfo = await auth.getGraphToken(config, graphSecret, graphTokenExpiry, graphToken);
      graphToken = tokenInfo.graphToken || graphToken;
      graphTokenExpiry = tokenInfo.graphTokenExpiry || graphTokenExpiry;
      return (graphToken);
    } catch(ex) {
      throw (JSON.stringify(errorHandler.throwInternalServerError(ex)));
    }    
}

const exportable = {
  handler,
  processACLRequest,
  processScmPermissions,
  getScmDetails,
  getAuthToken
};

module.exports = exportable;
