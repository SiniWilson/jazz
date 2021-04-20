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

const casbinUtil = require("./casbin.js");

const getServiceMetadata = async (config, userId, serviceId, isAdmin, logger) => {
  return new Promise(async (resolve, reject) => {
    try {
      let result;
      if (serviceId) {
        result = await casbinUtil.getPolicyForServiceUser(serviceId, userId, config, isAdmin);
      } else {
        result = await casbinUtil.getPolicyForUser(userId, config, isAdmin);
      }
      logger.debug("getServiceMetadata : "+ JSON.stringify(result));
      return resolve(result);
    } catch (ex) {
      logger.error("getServiceMetadata error: "+ JSON.stringify(ex));
      return reject(ex);
    }
  });
};

const checkPermissionData = async (config, userId, serviceId, category, permission, isAdmin, logger) => {
  return new Promise(async (resolve, reject) => {
    try {
      let result = await casbinUtil.checkPermissions(userId, serviceId, category, permission, config, isAdmin);
      logger.debug("checkPermissionData : "+ JSON.stringify(result));
      return resolve(result);
    } catch (ex) {
      logger.error("checkPermissionData error: "+ JSON.stringify(ex));
      return reject(ex);
    }
  });
};

module.exports = {
  getServiceMetadata,
  checkPermissionData
};

