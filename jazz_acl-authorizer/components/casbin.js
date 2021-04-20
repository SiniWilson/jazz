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

const casbin = require('casbin');
const TypeORMAdapter = require('typeorm-adapter');
const logger = require('./logger.js');
const errorHandlerModule = require("./error-handler.js")();
const getList = require("./getList.js");

/* Create a connection to the DB*/
async function dbConnection(config) {
  let conn;
  try {
    conn = await TypeORMAdapter.default.newAdapter({
      type: config.CASBIN.TYPE,
      host: config.CASBIN.HOST,
      port: config.CASBIN.PORT,
      username: config.CASBIN.USER,
      password: config.CASBIN.PASSWORD,
      database: config.CASBIN.DATABASE,
      timeout: config.CASBIN.TIMEOUT,
      logging: config.CASBIN.LOGGING
    });
  } catch (err) {
    if (err.name !== "AlreadyHasActiveConnectionError") {
      logger.error(err.message);
      throw (errorHandlerModule.throwInternalServerError(err.message));
    }
  }

  return conn;
}

/* Get the policies from casbin given the serviceId*/
async function getPolicies(serviceId, config) {
  const values = [`${serviceId}_manage`, `${serviceId}_code`, `${serviceId}_deploy`];
  let result = await getFilteredPolicy(1, values, config);
  if (result && result.error) {
    return result;
  }
  result = massagePolicies(result);

  return result;
}

/* Get the policies from casbin given the values and index*/
async function getFilteredPolicy(index, values, config) {
  let result = {};
  let conn, enforcer;

  try {
    // TODO this needs to be done once per invocation
    conn = await dbConnection(config);
    enforcer = await casbin.newEnforcer("./config/rbac_model.conf", conn);
    const promisedPolicies = values.map(async value => await enforcer.getFilteredPolicy(index, value));
    const policies = await Promise.all(promisedPolicies);
    result = policies;
  } catch (err) {
    logger.error(err.message);
    result.error = err.message;
  } finally {
    // TODO this needs to be done once per invocation
    if (conn) {
      await conn.close();
    }
  }

  return result;
}

/* Check permissions for a user */
async function checkPermissions(userId, serviceId, category, permission, config, isAdmin) {
  let result = {};
  let conn;
  try {
    if (isAdmin) {
      result.authorized = true;
    } else {
      conn = await dbConnection(config);
      const user_id = userId.toLowerCase()
      const enforcer = await casbin.newEnforcer('./config/rbac_model.conf', conn);
      result.authorized = await enforcer.enforce(user_id, `${serviceId}_${category}`, permission);
    }
    logger.debug("result in checkPermissions: " + JSON.stringify(result));
  } catch (err) {
    logger.error(err.message);
    result = {
      error: err.message
    };
  } finally {
    if (conn) {
      await conn.close();
    }
  }

  return result;
}

async function constructIndividualPolicies(serviceId, policies) {
  try {
    let incomingPolicies = []
    policies.map(item => {
      let tempArray = [];
      tempArray.push(item.userId);
      tempArray.push(`${serviceId}_${item.category}`);
      tempArray.push(item.permission);

      incomingPolicies.push(tempArray);
    })

    return incomingPolicies;
  } catch(err) {
    logger.error(err.message);
  }
}

/**
 * Function to find the difference between two arrays
 * @param {array} otherArray 
 */
function comparer(otherArray){
  return function(current){
    return otherArray.filter(function(other){
      return (other.idPermission == current.idPermission) && (other.categoryRule === current.categoryRule)
    }).length == 0;
  }
}

/**
 * Function to contruct compliment policies if not present
 * @param {array} policiesOne 
 * @param {array} policiesTwo 
 */
async function constructComplimentPolicies(policiesOne, policiesTwo, policies) {
  /**
   * Making the compliment searchString for each policy
   */
  policiesOne.map(item => {
    let searchString = '';
    let categoryRule;
    let permission = item.idPermission.split('_')[1]
    if(['admin', 'write'].indexOf(permission) > -1){
      searchString = item.idPermission.split('_')[0] + '_read'
      categoryRule = item.categoryRule
    } else if(['code', 'deploy'].indexOf(item.categoryRule) > -1) {
      searchString = item.idPermission.split('_')[0] + '_write'
      categoryRule = item.categoryRule
    } else {
      searchString = item.idPermission.split('_')[0] + '_admin'
      categoryRule = item.categoryRule
    }

    if(policies.length > 0){
      /**
       * Making sure to add the compliment policy ONLY if the permission changed in the existing/new incoming policies
       * In order to detect that, we are searching with the compliment searchString in the existing/new incoming policies
       * otherwise just add/remove it and don't add the compliment policy
       */
      let result = policies.filter(obj => {
        return (obj.idPermission === searchString && obj.categoryRule === categoryRule)
      })

      if(result.length > 0){
        /**
         * In order to avoid duplicate entry, making sure to add the compliment policy
         * ONLY if searchString matches
         */
        if(policiesTwo.length > 0){

          let policyFound = policiesTwo.filter(obj => {
            return (obj.idPermission === searchString && obj.categoryRule === categoryRule)
          })

          if(policyFound.length == 0) {
            let obj = {
              idPermission:searchString,
              category:item.category,
              categoryRule:item.categoryRule
            }
            policiesTwo.push(obj)
          }
        } else {

          let obj = {
            idPermission:searchString,
            category:item.category,
            categoryRule:item.categoryRule
          }
          policiesTwo.push(obj)
        }
      }
    }
  })
}

/**
 * Returns a filtered list of policies to be removed and to be added
 * @param {*} item1 existingPolicies
 * @param {*} item2 incomingPolicies
 */
async function diffPolicies(item1, item2){
  logger.debug("diffPolcies - item1: " + JSON.stringify(item1) + " item2: " + JSON.stringify(item2));
  let a = item1.map(item => {
    return {
      idPermission: item[0]+'_'+item[2],
      category: item[1],
      categoryRule: item[1].split('_')[1]
    }
  })
  let b = item2.map(item => {
    return {
      idPermission: item[0]+'_'+item[2],
      category: item[1],
      categoryRule: item[1].split('_')[1]
    }
  })

  /**
   * Making an unadultered copy of the existing and new incoming policies
   * in order to use them for comparing in constructComplimentPolicies
   */
  let existingPolicies = JSON.parse(JSON.stringify(a))
  let incomingPolicies = JSON.parse(JSON.stringify(b))
  
  let policiesToBeRemoved = a.filter(comparer(b));
  let policiesToBeAdded = b.filter(comparer(a));
  
  /**
   * Making sure if any policy is removed/added
   * its compliment policy is added/removed
   */
  if(policiesToBeRemoved.length > 0){
    await constructComplimentPolicies(policiesToBeRemoved, policiesToBeAdded, incomingPolicies)
  }

  if(policiesToBeAdded.length > 0){
    await constructComplimentPolicies(policiesToBeAdded, policiesToBeRemoved, existingPolicies)
  }

  policiesToBeRemoved = policiesToBeRemoved.map(item => {
    let id = item.idPermission.split('_')[0]
    let permission = item.idPermission.split('_')[1]
    return [
      id,
      item.category,
      permission
    ]
  })

  policiesToBeAdded = policiesToBeAdded.map(item => {
    let id = item.idPermission.split('_')[0]
    let permission = item.idPermission.split('_')[1]
    return [
      id,
      item.category,
      permission
    ]
  })

  let result = {
    policiesToBeRemoved: policiesToBeRemoved,
    policiesToBeAdded: policiesToBeAdded
  }
  logger.debug("result of diff: " + JSON.stringify(result));

  return result;
}

/* Add and/or Remove filtered policy */
async function addOrRemovePolicy(serviceId, config, action, policies) {
  logger.debug('policies: ' + JSON.stringify(policies));
  logger.debug("addOrRemovePolicy action: " + JSON.stringify(action));
  let result = {};
  let conn, enforcer;
  const objects = [`${serviceId}_manage`, `${serviceId}_code`, `${serviceId}_deploy`];
  let totalPolicies = 0;
  let policySet;
  try {
    let getPolicies = await getFilteredPolicy(1, objects, config);
    logger.debug('getPolicies before: ' + JSON.stringify(getPolicies));
    let incomingPolicies = await constructIndividualPolicies(serviceId, policies);

    if (getPolicies && getPolicies.error) { //if there was any error capture that
      result.error = getPolicies.error;
      return result;
    }
    getPolicies = getPolicies.filter(policy => policy.length > 0);
    logger.debug('getPolicies after: ' + JSON.stringify(getPolicies));
    let existingPolicies = [];
    getPolicies.map(policy => {
      existingPolicies = existingPolicies.concat(policy)
    })
    totalPolicies = existingPolicies.length;
    policySet = await diffPolicies(existingPolicies, incomingPolicies)
  
    if (existingPolicies.length) {

      let removeResult = [];
      if (totalPolicies) {
        conn = await dbConnection(config);
        enforcer = await casbin.newEnforcer('./config/rbac_model.conf', conn);

        // remove (incase of add, remove first)
        if (action === "remove" || action === "add") {
          let removePoliciesData = policySet.policiesToBeRemoved
          logger.debug('removePoliciesData: ' + JSON.stringify(removePoliciesData));

          const removeResult = await enforcer.removePolicies(removePoliciesData)
          logger.debug('removeResult: ' + JSON.stringify(removeResult));
          if (!removeResult) {
            result.error = `Rollback transaction - could delete ${removeResult.length} of ${totalPolicies} policies`;
          }
        }
        let addPoliciesData = policySet.policiesToBeAdded
        logger.debug('addPoliciesData: ' + JSON.stringify(addPoliciesData));
        // add (after remove)
        if (action === "add" && removeResult && addPoliciesData.length > 0) {
          result = await addPolicy(addPoliciesData, enforcer);
        }
      }
    } else { //only add (nothing to remove)
      if (action === 'add') {
        conn = await dbConnection(config);
        enforcer = await casbin.newEnforcer('./config/rbac_model.conf', conn);
        let addPoliciesData = policySet.policiesToBeAdded
        result = await addPolicy(addPoliciesData, enforcer);
        if (conn) {
          await conn.close();
        }
      }
    }
  } catch (err) {
    logger.error(err.message);
    result.error = err.message;
  } finally {
    if (totalPolicies) {
      await conn.close();
    }
  }

  result.policySet = policySet;
  return result;
}

async function addPolicy(incomingPolicies, enforcer) {
  let result = {};
  logger.debug('incomingPolicies ' + JSON.stringify(incomingPolicies));
  let savedPolicies = await enforcer.addPolicies(incomingPolicies);
  logger.debug('savedPolicies ' + JSON.stringify(savedPolicies));

  if (!savedPolicies) { //rollback deletion
    result.error = `Rollback transaction - could not add any policy`;
  }

  return result;
}

/* Get the permissions for a service given a userId */
async function getPolicyForServiceUser(serviceId, userId, config, isAdmin) {
  
  if (isAdmin) {
    const dbResult = await getList.getSeviceIdList(config, serviceId)
    if (dbResult && dbResult.error) {
      return dbResult
    }
    let result = attachAdminPolicies(dbResult.data, config);
    return result
  } else {
    const result = await getPolicies(serviceId, config);
    if (result && result.error) {
      return result;
    }
    let policies = formatPolicies(result);
    const user_id = userId.toLowerCase()
    let userPolicies = policies.filter(policy => policy.userId === user_id);
    userPolicies = userPolicies.map(policy => {
      return {
        permission: policy.permission,
        category: policy.category
      }
    });

    return [{
      serviceId: serviceId,
      policies: userPolicies
    }];
  }
}

/* attach admin policies */
function attachAdminPolicies(list, config) {
  let svcIdList = []
  list.forEach(eachId => {
    let svcIdObj = Object.assign({}, config.ADMIN_SERVICES_POLICIES);
    svcIdObj.serviceId = eachId;
    svcIdList.push(svcIdObj);
  });
  return svcIdList;
}

/* Get the policies for a userId*/
async function getPolicyForUser(userId, config, isAdmin) {
  let policies = [];
  if (isAdmin) {
    const dbResult = await getList.getSeviceIdList(config, null)
    if (dbResult && dbResult.error) {
      return dbResult
    }

    let result = attachAdminPolicies(dbResult.data, config);
    return result;
  } else {
    const user_id = userId.toLowerCase()
    let result = await getFilteredPolicy(0, [user_id], config);
    let serviceIdSeen = new Set();


    if (result && result.error) {
      return result;
    }

    result.forEach(policy => {
      policy.forEach(item => {
        const serviceId = item[1].split('_')[0];
        const policy = {
          category: item[1].split('_')[1],
          permission: item[2]
        };
        if (serviceIdSeen.has(serviceId)) {
          const foundPolicies = policies.find(r => r.serviceId === serviceId);
          if (foundPolicies) {
            foundPolicies.policies.push(policy);
          }
        } else {
          const policyObj = {};
          policyObj['serviceId'] = serviceId;
          policyObj['policies'] = [policy];
          policies.push(policyObj);
        }
        serviceIdSeen.add(serviceId);
      });
    });

    return policies;
  }
}

function massagePolicies(policies) {
  if (policies && !policies.error) {
    let filteredPolicies = policies.filter(el => el.length >= 1);
    if (filteredPolicies.length) {
      filteredPolicies =
        policies = filteredPolicies.map(policyArr => policyArr.map(policy => [policy[0], policy[1].split('_')[1], policy[2]]));
    } else {
      policies = [];
    }
  }

  return policies;
}

function formatPolicies(result) {
  let policies = [];
  result.forEach(policyArr =>
    policyArr.forEach(policy => policies.push({
      userId: policy[0],
      permission: policy[2],
      category: policy[1]
    })));

  return policies;
}

module.exports = {
  addOrRemovePolicy,
  getPolicies,
  getPolicyForServiceUser,
  getPolicyForUser,
  checkPermissions
};
