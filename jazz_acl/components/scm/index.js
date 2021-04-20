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

const gitlab = require("./gitlab.js");

module.exports = class ScmUtil {
  constructor(config) {
    this.config = config;
    this.scmMap = {
      "gitlab": {
        remove: gitlab.removeAllRepoUsers,
        add: gitlab.addRepoPermission
      }
    }
  }

  async processScmPermissions(serviceInfo, policies, key, scmDetails, deployAccessPolicies) {
    if (this.config && this.config.SCM_TYPE && this.scmMap[this.config.SCM_TYPE]) {
      if (scmDetails.scmManaged || (deployAccessPolicies && deployAccessPolicies.policiesToBeAdded && deployAccessPolicies.policiesToBeAdded.length > 0)) {
        let scmConfig = this.config.SCM_CONFIG[scmDetails.scmType];
        if(scmDetails.scmType === 'gitlab'){
          return await this.scmMap[scmDetails.scmType][key](scmConfig, serviceInfo, policies, deployAccessPolicies)
        } else {
          return { message: `we are not supporting ${scmDetails.scmType}`}
        }
      } else {
        return { message: `scmManaged is false, so user has to manage their own repository`}
      }
    }
  }
};
