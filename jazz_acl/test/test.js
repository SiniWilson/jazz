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

const index = require('../index');
const casbinUtil = require('../components/casbin');
const adminGroupUtil = require("../components/admin-group");
const sinon = require('sinon');
const chai = require('chai')
const chaiAsPromised = require('chai-as-promised');
const expect = chai.expect;
const validationUtil = require('../components/validation');
const util = require("../components/util");
const globalConfig = require("../config/global-config.json");
const auth = require("../components/scm/login");
const gitlabUtil = require("../components/scm/gitlab");
const services = require("../components/scm/services");
const request = require('request');
const getList = require("../components/getList");
const AWS = require("aws-sdk-mock");
const casbin = require("../components/casbin");
chai.use(chaiAsPromised);

describe("Validation tests", () => {
  describe("Validate basic input tests", () => {
    it("validate throws error when inputs are not provided", () => {
      expect(() => validationUtil.validateBasicInput()).to.throw();
    });

    it("validate throws error when method is missing", () => {
      expect(() => validationUtil.validateBasicInput({ method: '' })).to.throw();
    });

    it("validate throws error when path is missing", () => {
      expect(() => validationUtil.validateBasicInput({ method: 'GET', path: '' })).to.throw();
    });

    it("validate throws error when user is not authenticated", () => {
      expect(() => validationUtil.validateBasicInput({ method: 'GET', path: 'policies' })).to.throw();
    });
  });

  describe("Validate get policies input tests", () => {
    it("validate throws error when service id is missing", () => {
      expect(() => validationUtil.validateGetPoliciesInput({ query: {} })).to.throw();
    });
  });

  describe("Validate post policies input tests", () => {
    it("validate throws error when body is missing", () => {
      expect(() => validationUtil.validatePostPoliciesInput({})).to.throw();
    });

    it("validate throws error when service id is missing", () => {
      expect(() => validationUtil.validatePostPoliciesInput({ body: {} })).to.throw();
    });

    it("validate throws error when policies are missing", () => {
      expect(() => validationUtil.validatePostPoliciesInput({ body: { serviceId: '342342' } })).to.throw();
    });

    it("validate throws error when policy keys are missing", () => {
      expect(() => validationUtil.validatePostPoliciesInput({ body: { serviceId: '342342', policies: [{ userId: '23222' }] } })).to.throw();
    });
  });

  describe("Validate get check perms input", () => {
    it("validate throws error when userId is missing", () => {
      expect(() => validationUtil.validateGetCheckPermsInput({ query: {} })).to.throw();
    });

    it("validate throws error when service Id is missing", () => {
      expect(() => validationUtil.validateGetCheckPermsInput({ query: { "userId": "231313" } })).to.throw();
    });

    it("validate throws error when permission is missing", () => {
      expect(() => validationUtil.validateGetCheckPermsInput({ query: { "userId": "231313", "serviceId": "3432424" } })).to.throw();
    });

    it("validate throws error when category is missing", () => {
      expect(() => validationUtil.validateGetCheckPermsInput({ query: { "userId": "231313", "serviceId": "3432424", "permission": "write" } })).to.throw();
    });
  });

  describe("Validate get services input", () => {
    it("validate throws error when userId is missing", () => {
      expect(() => validationUtil.validateGetServicesInput({ query: {} })).to.throw();
    });
  });
});

describe('processACLRequest tests', () => {
  describe('policies path tests', () => {
    let postEvent;
    let getEvent;

    beforeEach(() => {
      postEvent = {
        method: 'POST',
        resourcePath: 'policies',
        body: {
          serviceId: "324234234",
          policies: [{ userId: "data", category: "manage", permission: "read" }, { userId: "data", category: "code", permission: "read" }]
        }
      };

      getEvent = {
        method: 'GET',
        resourcePath: 'policies',
        query: {
          serviceId: "324234234",
        }
      };
    });

    afterEach(() => {
      postEvent = {};
      getEvent = {};
    });

    it('POST policies - add user successfully', async () => {
      // arrange
      const addOrRemovePolicyStub = sinon.stub(casbinUtil, "addOrRemovePolicy").resolves({
        policySet: {
          policiesToBeAdded: [[
            'user',
            'abcd1234_code',
            'read'
          ]], policiesToBeRemoved: [[
            'user',
            'abcd1234_code',
            'read'
          ]]
        }
      });
      const processScmPermissionsStub = sinon.stub(index, "processScmPermissions").resolves(true);

      const config = {};

      // act
      const result = await index.processACLRequest(postEvent, config);

      // assert
      expect(result.success).to.equal(true);
      sinon.assert.calledOnce(addOrRemovePolicyStub);
      sinon.assert.calledOnce(processScmPermissionsStub);
      addOrRemovePolicyStub.restore();
      processScmPermissionsStub.restore();
    });

    it('POST policies - add user error', async () => {
      // arrange
      const addOrRemovePolicyStub = sinon.stub(casbinUtil, "addOrRemovePolicy").resolves({ error: "Error" });
      const config = {};

      // act & assert
      await expect(index.processACLRequest(postEvent, config)).to.be.rejected;
      sinon.assert.calledOnce(addOrRemovePolicyStub);
      addOrRemovePolicyStub.restore();
    });

    it('POST policies - remove users successfully', async () => {
      // arrange
      const addOrRemovePolicyStub = sinon.stub(casbinUtil, "addOrRemovePolicy").resolves(true);
      const processScmPermissionsStub = sinon.stub(index, "processScmPermissions").resolves(true);
      const config = {};
      postEvent.body.policies = [];

      // act
      const result = await index.processACLRequest(postEvent, config);

      // assert
      expect(result.success).to.equal(true);
      sinon.assert.calledOnce(addOrRemovePolicyStub);
      sinon.assert.calledOnce(processScmPermissionsStub);
      addOrRemovePolicyStub.restore();
      processScmPermissionsStub.restore();
    });

    it('POST policies - remove users throws error', async () => {
      // arrange
      const addOrRemovePolicyStub = sinon.stub(casbinUtil, "addOrRemovePolicy").resolves({ error: "Error" });
      const config = {};
      postEvent.body.policies = [];

      // act & assert
      await expect(index.processACLRequest(postEvent, config)).to.be.rejected;
      sinon.assert.calledOnce(addOrRemovePolicyStub);
      addOrRemovePolicyStub.restore();
    });

    it('GET policies - get user throws error', async () => {
      // arrange
      const getPoliciesStub = sinon.stub(casbinUtil, "getPolicies").resolves({ error: "Error" });
      const config = {};

      // act & assert
      await expect(index.processACLRequest(getEvent, config)).to.be.rejected;
      sinon.assert.calledOnce(getPoliciesStub);
      getPoliciesStub.restore();
    });

    it('GET policies - get user policies', async () => {
      // arrange
      const policies = [[["user1", "admin", "manage"]]];
      const getPoliciesStub = sinon.stub(casbinUtil, "getPolicies").resolves(policies);
      const config = {};

      // act
      const result = await index.processACLRequest(getEvent, config);

      // assert
      expect(result.policies[0].userId).to.eq("user1");
      expect(result.serviceId).to.eq(getEvent.query.serviceId);
      sinon.assert.calledOnce(getPoliciesStub);
      getPoliciesStub.restore();
    });
  });

  describe('checkPermission path tests', () => {
    let getEvent;

    beforeEach(() => {
      getEvent = {
        method: 'GET',
        resourcePath: 'checkpermission',
        query: {
          userId: "3423",
          serviceId: "324234234",
          category: "manage",
          permission: "write"
        }
      };
    });

    afterEach(() => {
      getEvent = {};
    });

    it('GET checkpermission - authorize true', async () => {
      // arrange
      const getCheckPermissionsStub = sinon.stub(casbinUtil, "checkPermissions").resolves({ authorized: true });
      const stubRequest = sinon.stub(request, "Request").callsFake((obj) => {
        let responseObject = { statusCode: 200 }
        responseObject.body = "{\"@odata.context\":\"https://graph.microsoft.com/beta/$metadata#directoryObjects\",\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tes, test\",\"mail\":\"tet@Tezt.com\",\"mailNickname\":\"test\"},{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tees, test1\",\"mail\":\"test1@Tezt.com\",\"mailNickname\":\"Prakash.Raghothamachar1\"},{\"@odata.type\":\"#microsoft.graph.servicePrincipal\",\"displayName\":\"test_svc\",\"appDisplayName\":\"test_svc\"}]}"
        return obj.callback(null, responseObject, responseObject.body);

      });
      const authTokenStub = sinon.stub(index, "getAuthToken").resolves(true);

      const config = { "SERVICE_PRINCIPAL_DATA_TYPE": "#microsoft.graph.servicePrincipal" };

      // act
      const result = await index.processACLRequest(getEvent, config);

      // assert
      expect(result.authorized).to.eq(true);
      sinon.assert.calledOnce(getCheckPermissionsStub);
      getCheckPermissionsStub.restore();
      stubRequest.restore();
      authTokenStub.restore();
    });

    it('GET checkpermission - throws error', async () => {
      // arrange
      const getCheckPermissionsStub = sinon.stub(casbinUtil, "checkPermissions").resolves({ error: "Error" });
      const authTokenStub = sinon.stub(index, "getAuthToken").resolves(true);
      const stubRequest = sinon.stub(request, "Request").callsFake((obj) => {
        let responseObject = { statusCode: 200 }
        responseObject.body = "{\"@odata.context\":\"https://graph.microsoft.com/beta/$metadata#directoryObjects\",\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tes, test\",\"mail\":\"tet@Tezt.com\",\"mailNickname\":\"test\"},{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tees, test1\",\"mail\":\"test1@Tezt.com\",\"mailNickname\":\"Prakash.Raghothamachar1\"},{\"@odata.type\":\"#microsoft.graph.servicePrincipal\",\"displayName\":\"test_svc\",\"appDisplayName\":\"test_svc\"}]}"
        return obj.callback(null, responseObject, responseObject.body);

      });
      const config = { "SERVICE_PRINCIPAL_DATA_TYPE": "#microsoft.graph.servicePrincipal" };

      // act & assert
      await expect(index.processACLRequest(getEvent, config)).to.be.rejected;
      sinon.assert.calledOnce(authTokenStub);
      sinon.assert.calledOnce(getCheckPermissionsStub);
      getCheckPermissionsStub.restore();
      stubRequest.restore();
      authTokenStub.restore();
    });
  });

  describe('services path tests', () => {
    let getEvent;

    beforeEach(() => {
      getEvent = {
        method: 'GET',
        resourcePath: 'services',
        path: {
          serviceId: "sdadaada"
        },
        query: {
          userId: "3423"
        }
      };
    });

    afterEach(() => {
      getEvent = {};
    });

    it('GET services - with service id for a given user', async () => {
      // arrange
      const policies = [{
        serviceId: "324234234",
        policies: [{ category: "manage", permission: "read" }]
      }];
      getEvent.resourcePath = 'services/123456';
      const getCheckPermissionsStub = sinon.stub(casbinUtil, "getPolicyForServiceUser").resolves(policies);
      const authTokenStub = sinon.stub(index, "getAuthToken").resolves(true);
      const stubRequest = sinon.stub(request, "Request").callsFake((obj) => {
        let responseObject = { statusCode: 200 }
        responseObject.body = "{\"@odata.context\":\"https://graph.microsoft.com/beta/$metadata#directoryObjects\",\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tes, test\",\"mail\":\"tet@Tezt.com\",\"mailNickname\":\"test\"},{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tees, test1\",\"mail\":\"test1@Tezt.com\",\"mailNickname\":\"Prakash.Raghothamachar1\"},{\"@odata.type\":\"#microsoft.graph.servicePrincipal\",\"displayName\":\"test_svc\",\"appDisplayName\":\"test_svc\"}]}"
        return obj.callback(null, responseObject, responseObject.body);

      });
      const config = { "SERVICE_PRINCIPAL_DATA_TYPE": "#microsoft.graph.servicePrincipal" };

      // act
      const result = await index.processACLRequest(getEvent, config);

      // assert
      expect(result[0].serviceId).to.eq("324234234");
      sinon.assert.calledOnce(getCheckPermissionsStub);
      getCheckPermissionsStub.restore();
      stubRequest.restore();
      authTokenStub.restore();
    });

    it('GET services - for a given user', async () => {
      // arrange
      getEvent.path = {};
      const policies = [{
        serviceId: "324234234",
        policies: [{ category: "manage", permission: "read" }]
      }];

      const getCheckPermissionsStub = sinon.stub(casbinUtil, "getPolicyForUser").resolves(policies);
      const authTokenStub = sinon.stub(index, "getAuthToken").resolves(true);
      const stubRequest = sinon.stub(request, "Request").callsFake((obj) => {
        let responseObject = { statusCode: 200 }
        responseObject.body = "{\"@odata.context\":\"https://graph.microsoft.com/beta/$metadata#directoryObjects\",\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tes, test\",\"mail\":\"tet@Tezt.com\",\"mailNickname\":\"test\"},{\"@odata.type\":\"#microsoft.graph.user\",\"displayName\":\"tees, test1\",\"mail\":\"test1@Tezt.com\",\"mailNickname\":\"Prakash.Raghothamachar1\"},{\"@odata.type\":\"#microsoft.graph.servicePrincipal\",\"displayName\":\"test_svc\",\"appDisplayName\":\"test_svc\"}]}"
        return obj.callback(null, responseObject, responseObject.body);

      });
      const config = { "SERVICE_PRINCIPAL_DATA_TYPE": "#microsoft.graph.servicePrincipal" };
      // act
      const result = await index.processACLRequest(getEvent, config);

      // assert
      expect(result[0].serviceId).to.eq("324234234");
      sinon.assert.calledOnce(getCheckPermissionsStub);
      getCheckPermissionsStub.restore();
      stubRequest.restore();
      authTokenStub.restore();
    });
  });
});

describe("Util", () => {
  it('Make rule for user has manage admin policy only', async () => {
    let policies = [{
      "userId": "test",
      "permission": "admin",
      "category": "manage"
    }];

    const userPolicies = util.createRule(policies, globalConfig.POLICY);
    const managePolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'admin');
    const codePolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'write');
    const deployPolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'write');

    expect(userPolicies.length).to.be.eql(3);
    expect(managePolicy.length).to.be.eql(1);
    expect(codePolicy.length).to.be.eql(1);
    expect(deployPolicy.length).to.be.eql(1);
  });

  it('Make rule for user has manage read policy only', async () => {
    let policies = [{
      "userId": "test",
      "permission": "read",
      "category": "manage"
    }];

    const userPolicies = util.createRule(policies, globalConfig.POLICY);
    const manageAdminPolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'admin');
    const codeWritePolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'write');
    const deployWritePolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'write');
    const manageReadPolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'read');
    const codeReadPolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'read');
    const deployReadPolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'read');

    expect(userPolicies.length).to.be.eql(3);
    expect(manageAdminPolicy.length).to.be.eql(0);
    expect(codeWritePolicy.length).to.be.eql(0);
    expect(deployWritePolicy.length).to.be.eql(0);
    expect(manageReadPolicy.length).to.be.eql(1);
    expect(codeReadPolicy.length).to.be.eql(1);
    expect(deployReadPolicy.length).to.be.eql(1);
  });

  it('Make rule for user has code write policy ', async () => {
    let policies = [{
      "userId": "test",
      "permission": "write",
      "category": "code"
    }];

    const userPolicies = util.createRule(policies, globalConfig.POLICY);
    const manageAdminPolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'admin');
    const codeWritePolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'write');
    const deployWritePolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'write');
    const manageReadPolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'read');
    const codeReadPolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'read');
    const deployReadPolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'read');

    expect(userPolicies.length).to.be.eql(2);
    expect(manageAdminPolicy.length).to.be.eql(0);
    expect(codeWritePolicy.length).to.be.eql(1);
    expect(deployWritePolicy.length).to.be.eql(0);
    expect(manageReadPolicy.length).to.be.eql(1);
    expect(codeReadPolicy.length).to.be.eql(0);
    expect(deployReadPolicy.length).to.be.eql(0);
  });

  it('Make rule for user has deploy write and code read policy ', async () => {
    let policies = [{
      "userId": "test1",
      "permission": "write",
      "category": "deploy"
    },
    {
      "userId": "test2",
      "permission": "read",
      "category": "code"
    }];

    const userPolicies = util.createRule(policies, globalConfig.POLICY);
    const manageAdminPolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'admin' && policy.userId === 'test2');
    const codeWritePolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'write' && policy.userId === 'test2');
    const deployWritePolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'write' && policy.userId === 'test2');
    const manageReadPolicy = userPolicies.filter(policy => policy.category === 'manage' && policy.permission === 'read' && policy.userId === 'test2');
    const codeReadPolicy = userPolicies.filter(policy => policy.category === 'code' && policy.permission === 'read' && policy.userId === 'test2');
    const deployReadPolicy = userPolicies.filter(policy => policy.category === 'deploy' && policy.permission === 'read' && policy.userId === 'test2');

    expect(userPolicies.length).to.be.eql(4);
    expect(manageAdminPolicy.length).to.be.eql(0);
    expect(codeWritePolicy.length).to.be.eql(0);
    expect(deployWritePolicy.length).to.be.eql(0);
    expect(manageReadPolicy.length).to.be.eql(1);
    expect(codeReadPolicy.length).to.be.eql(1);
    expect(deployReadPolicy.length).to.be.eql(0);
  });
});

describe("getServiceMetadata", () => {
  it('getServiceMetadata will be reject for status code 500', async () => {
    let responseObject = {
      statusCode: 500,
      body: {}
    };

    reqStub = sinon.stub(request, "Request").callsFake((obj) => {
      return obj.callback(null, responseObject, responseObject.body);
    });

    await expect(services.getServiceMetadata({}, "abc", "id")).to.be.rejected;
    sinon.assert.calledOnce(reqStub);
    reqStub.restore();
  });

  it('getServiceMetadata will be reject for empty body', async () => {
    let responseObject = {
      statusCode: 200,
      body: {}
    };

    reqStub = sinon.stub(request, "Request").callsFake((obj) => {
      return obj.callback(null, responseObject, responseObject.body);
    });

    await expect(services.getServiceMetadata({}, "abc", "id")).to.be.rejected;
    sinon.assert.calledOnce(reqStub);
    reqStub.restore();
  });

  it('getServiceMetadata will be resolve for valid response', async () => {
    let responseObject = {
      statusCode: 200,
      body: JSON.stringify({ data: { data: "dada" } })
    };

    reqStub = sinon.stub(request, "Request").callsFake((obj) => {
      return obj.callback(null, responseObject, responseObject.body);
    });

    await expect(services.getServiceMetadata({}, "abc", "id")).to.be.resolves;
    sinon.assert.calledOnce(reqStub);
    reqStub.restore();
  });
});

describe("ScmUtil -- Gitlab", () => {

  it('removeAllGitlabRepoUsers will remove all the permissions of the given service', async () => {
    const serviceInfo = { service: "dada", domain: "test" };
    const config = {};
    const resp = [{ id: "a", name: "test" }, { id: "b", name: "test2" }];
    const res = { body: JSON.stringify(resp) }
    const getAllRepoUsersStub = sinon.stub(gitlabUtil, "getAllRepoUsers").resolves(res);
    const getGitLabsProjectIdStub = sinon.stub(gitlabUtil, "getGitLabsProjectId").resolves("repo_id");
    const removeRepoUserStub = sinon.stub(gitlabUtil, "removeRepoUser").resolves();

    const result = await gitlabUtil.removeAllRepoUsers(config, serviceInfo);
    sinon.assert.calledOnce(getAllRepoUsersStub);
    sinon.assert.calledOnce(getGitLabsProjectIdStub);
    getAllRepoUsersStub.restore();
    getGitLabsProjectIdStub.restore();
    removeRepoUserStub.restore();
  });

  it('removeAllGitlabRepoUsers will give the users list who has permission on the given service', async () => {
    const serviceInfo = { service: "dada", domain: "test" };
    const config = {};
    const resp = [{ id: "a", name: "test" }, { id: "b", name: "test2" }];
    const res = { body: JSON.stringify(resp) }
    const getAllRepoUsersStub = sinon.stub(gitlabUtil, "getAllRepoUsers").resolves(res);
    const getGitLabsProjectIdStub = sinon.stub(gitlabUtil, "getGitLabsProjectId").resolves("repo_id");
    const removeRepoUserStub = sinon.stub(gitlabUtil, "removeRepoUser").resolves();

    const result = await gitlabUtil.removeAllRepoUsers(config, serviceInfo);
    sinon.assert.calledOnce(getAllRepoUsersStub);
    sinon.assert.calledOnce(getGitLabsProjectIdStub);
    expect(result.length).to.equal(2);
    expect(result[0]).to.equal("test");
    getAllRepoUsersStub.restore();
    getGitLabsProjectIdStub.restore();
    removeRepoUserStub.restore();
  });
});

describe("getList", () => {
  it("should successfully get data from db", () => {
    const config = {
      "SERVICES_TABLE_NAME": "test_table",
      "REGION": "region"
    }
    let data = {
      Items: [{
        SERVICE_ID: {
          S: "1"
        }
      }, {
        SERVICE_ID: {
          S: "2"
        }
      }, {
        SERVICE_ID: {
          S: "3"
        }
      }]
    };
    let count = 1;
    let res = Object.assign({}, data);
    AWS.mock("DynamoDB", "scan", (params, cb) => {
      let dataObj;
      if (count) {
        data.LastEvaluatedKey = {
          SERVICE_ID: {
            S: "3"
          }
        }
        dataObj = data;
        count--;
      } else {
        dataObj = res;
      }
      return cb(null, dataObj);
    });

    const list = getList.getSeviceIdList(config, '123');
    list.then(res => {
      expect(res.data).to.include('1', '2', '3');
      AWS.restore("DynamoDB");
    });
  });
});

describe("super user implementation", () => {
  it("should return list of services with admin policies for super user", () => {
    let adminId = "adminUser";
    const config = {
      "SERVICE_USER": adminId,
      "SERVICES_TABLE_NAME": "test_table",
      "REGION": "region",
    };
    let response = { data: ["123", "456", "789"] };
    let getSeviceIdList = sinon.stub(getList, "getSeviceIdList").returns(response);
    casbin.getPolicyForUser(adminId, config, "true").then(res => {
      sinon.assert.calledOnce(getSeviceIdList);
      for (eachSvc of res) {
        for (each of eachSvc.policies) {
          if (each.category === "manage") {
            expect(each.permission).to.be.eq("admin")
          } else {
            expect(each.permission).to.be.eq("write")
          }
        }
      }
      getSeviceIdList.restore();
    });
  });

  it("should thow error if db result in error", () => {
    let adminId = "adminUser";
    const config = {
      "SERVICE_USER": adminId,
      "SERVICES_TABLE_NAME": "test_table",
      "REGION": "region",
    };
    let errorRes = { error: "No data available" };
    let getSeviceIdList = sinon.stub(getList, "getSeviceIdList").returns(errorRes);
    casbin.getPolicyForUser(adminId, config, "true").then(res => {
      sinon.assert.calledOnce(getSeviceIdList);
      expect(res.error).to.be.eq(errorRes.error);
      getSeviceIdList.restore();
    });
  });

  it("should return admin policies for the requested serviceId", () => {
    let adminId = "adminUser";
    const config = {
      "SERVICE_USER": adminId
    };
    let response = { data: ["123"] };
    let getSeviceIdList = sinon.stub(getList, "getSeviceIdList").returns(response);
    casbin.getPolicyForServiceUser("123", adminId, config, "true")
      .then(res => {
        sinon.assert.calledOnce(getSeviceIdList);
        for (eachSvc of res) {
          for (each of eachSvc.policies) {
            if (each.category === "manage") {
              expect(each.permission).to.be.eq("admin")
            } else {
              expect(each.permission).to.be.eq("write")
            }
          }
        }
        getSeviceIdList.restore();
      });
  });

  it("should show no data available if no items available from db", () => {
    let adminId = "adminUser";
    const config = {
      "SERVICE_USER": adminId
    };
    let errorRes = { error: "No data available" };
    let getSeviceIdList = sinon.stub(getList, "getSeviceIdList").returns(errorRes);
    casbin.getPolicyForServiceUser("123", adminId, config, "true").then(res => {
      sinon.assert.calledOnce(getSeviceIdList);
      expect(res.error).to.be.eq(errorRes.error);
      getSeviceIdList.restore();
    });
  });

});
