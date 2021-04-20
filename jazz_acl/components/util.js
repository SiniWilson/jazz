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

const createRule = (policies, config) => {
  const userPolicies = groupBy(policies, 'userId');
  let newPolicies = [];
  for (let key in userPolicies) {
    let userPolicy = userPolicies[key];
    const manageAdminPolicy = userPolicy.filter(policy => policy.category === 'manage' && policy.permission === 'admin');
    let managePolicy = userPolicy.filter(policy => policy.category === 'manage');
    let codePolicy = userPolicy.filter(policy => policy.category === 'code');
    let deployPolicy = userPolicy.filter(policy => policy.category === 'deploy');
    const user_id = key.toLowerCase()

    managePolicy = managePolicy.map(policy => { return { permission: policy.permission, category: policy.category, userId: user_id } });
    codePolicy = codePolicy.map(policy => { return { permission: policy.permission, category: policy.category, userId: user_id } });
    deployPolicy = deployPolicy.map(policy => { return { permission: policy.permission, category: policy.category, userId: user_id } });

    if (manageAdminPolicy.length > 0) {
      let adminPolicies = config.MANAGE_ADMIN;
      adminPolicies = adminPolicies.map(policy => { return { permission: policy.permission, category: policy.category, userId: user_id } });
      newPolicies = newPolicies.concat(adminPolicies);
    } else if ((codePolicy.length > 0 || deployPolicy.length > 0)) {
      let readPolicy = config.READ;
      readPolicy = readPolicy.map(policy => { return { permission: policy.permission, category: policy.category, userId: user_id } });
      newPolicies = codePolicy.length > 0 ? newPolicies.concat(codePolicy) : newPolicies;
      newPolicies = deployPolicy.length > 0 ? newPolicies.concat(deployPolicy) : newPolicies;
      newPolicies = managePolicy.length > 0 ? newPolicies.concat(managePolicy) : newPolicies.concat(readPolicy);
    } else if (codePolicy.length === 0 && deployPolicy.length === 0 && managePolicy.length > 0) {
      let manageReadPolicy = config.MANAGE_READ;
      manageReadPolicy = manageReadPolicy.map(policy => { return { permission: policy.permission, category: policy.category, userId: user_id } });
      newPolicies = newPolicies.concat(managePolicy);
      newPolicies = newPolicies.concat(manageReadPolicy);
    }
  }
  return newPolicies;
}

const checkIfPoliciesExistByCategory = (policies,category) => {
  // process this only if code category is included in policy
  let hasCategory = false;
  const userPolicies = groupBy(policies, 'userId');
  for (let key in userPolicies) {
    let userPolicy = userPolicies[key];
    let categoryPolicy = userPolicy.filter(policy => policy.category === category);
    if (categoryPolicy.length > 0) 
    { 
      hasCategory = true; 
    }
  }
  return hasCategory;
}

const groupBy = (array, prop) => {
  return array.reduce(function (groups, item) {
    let val = item[prop];
    groups[val] = groups[val] || [];
    groups[val].push(item);
    return groups;
  }, {});
}

/**
 * Function to group code policies
 * @param {array} data 
 */
const groupCodePolicies = data => {
  let dataSet = data.filter(item => item[1].split('_')[1] === 'code')
  if(dataSet.length > 0){
    dataSet = dataSet.map(item => {
      return {
        permission: item[2],
        category: item[1].split('_')[1],
        userId: item[0]
      }
    })
  }
  return dataSet
}

/**
 * Function to group deploy write policies
 * @param {array} data 
 */
const groupDeployPolicies = data => {
  let dataSet = data.filter(item => (item[1].split('_')[1] === 'deploy' && item[2] === 'write' ))
  if(dataSet.length > 0){
    dataSet = dataSet.map(item => {
      return {
        permission: item[2],
        category: item[1].split('_')[1],
        userId: item[0]
      }
    })
  }
  return dataSet
}

/**
 * Function to segregate only code policies
 * @param {object} policies 
 */
const checkIfPoliciesExist = policies => {
  let updatedPolicies = {
    policiesToBeRemoved: [],
    policiesToBeAdded: []
  };

  if(policies.policiesToBeRemoved.length > 0){
    updatedPolicies.policiesToBeRemoved = groupCodePolicies(policies.policiesToBeRemoved)
  }

  if(policies.policiesToBeAdded.length > 0){
    updatedPolicies.policiesToBeAdded = groupCodePolicies(policies.policiesToBeAdded)
  }
  
  return updatedPolicies
}

/**
 * Function to segregate only deploy write policies
 * @param {object} policies 
 */
const checkDeployAccessPolicies = policies => {
  let deployPolicies = {
    policiesToBeAdded: [],
    policiesToBeRemoved: []
  };

  if(policies.policiesToBeRemoved.length > 0){
    deployPolicies.policiesToBeRemoved = groupDeployPolicies(policies.policiesToBeRemoved)
  }

  if(policies.policiesToBeAdded.length > 0){
    deployPolicies.policiesToBeAdded = groupDeployPolicies(policies.policiesToBeAdded)
  }

  return deployPolicies;
}


module.exports = {
  createRule,
  groupBy,
  checkIfPoliciesExistByCategory,
  checkIfPoliciesExist,
  groupCodePolicies,
  checkDeployAccessPolicies
};
