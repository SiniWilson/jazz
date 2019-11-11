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

const assert = require('chai').assert;
const expect = require('chai').expect;
const sinon = require('sinon');
const awsContext = require('aws-lambda-mock-context');
const index = require('../index');
const configModule = require("../components/config.js");
const vault = require("../components/utils/vault.js");
const validations = require("../components/utils/validations.js");

//Validations
describe('Validations', () => {
  beforeEach(function () {
    event = {
      "method": "POST",
      "stage": "test",
      "resourcePath": "test/service",
      "body": {
        "name": "tester",
        "owner": "test",
        "description": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
  });

  it('should resolve while validating create safe input params with valid input', (done) => {
    validations.validateCreateSafeInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating create safe input params with empty input', (done) => {
    event.body = {};
    validations.validateCreateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create safe input params with out event.body', (done) => {
    delete event.body;
    validations.validateCreateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create safe input params with out required fields', (done) => {
    delete event.body.name;
    validations.validateCreateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required - name'
        });
      });
    done();
  });

  it('should resolve while validating update safe input params with valid input', (done) => {
    validations.validateUpdateSafeInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating update safe input params with empty input', (done) => {
    event.body = {};
    validations.validateUpdateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating update safe input params with out event.body', (done) => {
    delete event.body;
    validations.validateUpdateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should resolve while validating valid safename as path param', (done) => {
    event.path = { "safename": "testsafe" }
    validations.validateSafeInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating empty path param', (done) => {
    event.path = {};
    validations.validateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input path cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating with out event.path', (done) => {
    validations.validateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input path cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating path params with out safename', (done) => {
    event.path = { "name": "test" };
    validations.validateSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required in path- safename'
        });
      });
    done();
  });

  it('should resolve while validating create user in safe input params with valid input', (done) => {
    event.body = { "username": "test@test.com" }
    validations.validateUserInSafeInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating create user in safe input params with empty input', (done) => {
    event.body = {};
    validations.validateUserInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create user in safe input params with out event.body', (done) => {
    delete event.body;
    validations.validateUserInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create user in safe input params with out required fields', (done) => {
    event.body = { "name": "safe" };
    validations.validateUserInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required - username'
        });
      });
    done();
  });

  it('should resolve while validating create role in safe input params with valid input', (done) => {
    event.body = { "arn": "arn:aws:iam::1234567889:role/test_role" }
    validations.validateRoleInSafeInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating create role in safe input params with empty input', (done) => {
    event.body = {};
    validations.validateRoleInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create role in safe input params with out event.body', (done) => {
    delete event.body;
    validations.validateRoleInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create role in safe input params with out required fields', (done) => {
    event.body = { "name": "safe" };
    validations.validateRoleInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required - arn'
        });
      });
    done();
  });

  it('should resolve while validating valid rolename as query param', (done) => {
    event.query = { "rolename": "testrole" }
    validations.validateGetRoleInSafeInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating empty query param', (done) => {
    event.query = {};
    validations.validateGetRoleInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Query cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating with out event.query', (done) => {
    validations.validateGetRoleInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Query cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating query params with out rolename', (done) => {
    event.query = { "name": "test" };
    validations.validateGetRoleInSafeInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required in query- rolename'
        });
      });
    done();
  });

  it('should resolve while validating create user in vault input params with valid input', (done) => {
    event.body = { "username": "test@test.com", "password": "test" }
    validations.validateUserInVaultInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating create user in vault input params with empty input', (done) => {
    event.body = {};
    validations.validateUserInVaultInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create user in vault input params with out event.body', (done) => {
    delete event.body;
    validations.validateUserInVaultInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating create user in vault input params with out required fields', (done) => {
    event.body = { "name": "safe" };
    validations.validateUserInVaultInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required - username, password'
        });
      });
    done();
  });

  it('should resolve while validating delete user from vault input params with valid input', (done) => {
    event.body = { "username": "test@test.com", "password": "test" }
    validations.validateUserInVaultDeleteInput(event)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject while validating delete user from vault input params with empty input', (done) => {
    event.body = {};
    validations.validateUserInVaultDeleteInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating delete user from vault input params with out event.body', (done) => {
    delete event.body;
    validations.validateUserInVaultDeleteInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

  it('should reject while validating delete user from vault input params with out required fields', (done) => {
    event.body = { "name": "safe" };
    validations.validateUserInVaultDeleteInput(event)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) are required - username'
        });
      });
    done();
  });

  it('should resolve with valid iam role arn', (done) => {
    let arn = "arn:aws:iam::123456788909:role/test_role";
    validations.validateRoleArn(arn)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject with in valid iam role arn', (done) => {
    let arn = "arn:aws:iam::1234567889:role/test_role";
    validations.validateRoleArn(arn)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'The provided arn is not valid - arn:aws:iam::1234567889:role/test_role'
        });
      });
    done();
  });

  it('should resolve with valid length of fields', (done) => {
    let data = { "name": "test", "description": "test safe details" }
    validations.validateFieldLength(data)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should reject if the inputs does not satisfy the required field length constraints', (done) => {
    let data = { "name": "te", "description": "test " }
    validations.validateFieldLength(data)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Following field(s) not satisfying the char length {"name":3,"description":10} -  name,description'
        });
      });
    done();
  });

  it('should resolve with non empty inputs', (done) => {
    let data = { "username": "test@test.com", "password": "test" }
    validations.genericInputValidation(data)
      .then((result) => {
        assert(true);
      })
    done();
  });

  it('should rejects with non empty inputs', (done) => {
    let data = {};
    validations.genericInputValidation(data)
      .catch((err) => {
        expect(err).to.include({
          errorType: 'inputError',
          message: 'Input cannot be empty'
        });
      });
    done();
  });

});

//Create Safe
describe('Create safe', () => {
  beforeEach(function () {
    event = {
      "method": "POST",
      "stage": "test",
      "resourcePath": "/safes",
      "body": {
        "name": "testsafe",
        "owner": "test",
        "description": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `Safe testsafe and associated read/write/deny policies are created.` } }
    };
  });

  it('should create safe in tvault with valid input', (done) => {
    const createSafeStub = sinon.stub(vault, "createSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.createSafe(event.body, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(createSafeStub);
        createSafeStub.restore();
      })
    done();
  });

  it('should rejects create safe in tvault if safe creation in tvault fails.', (done) => {
    const createSafeStub = sinon.stub(vault, "createSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.createSafe(event.body, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(createSafeStub);
        createSafeStub.restore();
      });
    done();
  });

});

//Get Safe
describe('Get safe details', () => {
  beforeEach(function () {
    event = {
      "method": "GET",
      "stage": "test",
      "resourcePath": "/safes/{safename}"
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: {
        "data": {
          "description": "test",
          "name": "safe1",
          "owner": "test@tst.com",
          "ownerid": null,
          "type": "",
          "users": {
            "test1-tst-com": {
              "permission": "write"
            },
            "test2-tst-com": {
              "permission": "write"
            }
          },
          "roles": {
            "123456_tstrole1": {
              "arn": "arn:aws:iam::123456:role/tstrole1",
              "permission": "write"
            },
            "123456_tstrole2": {
              "arn": "arn:aws:iam::123456:role/tstrole2",
              "permission": "write"
            }
          }
        }
      }
    };
  });

  it('should get safe details with valid safename', (done) => {
    let getSafeStub = sinon.stub(vault, "getSafeDetails").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.getSafeDetails(event.body, config, vaultToken)
      .then((result) => {
        expect(result.body.data).to.eq(response.body.data)
        sinon.assert.calledOnce(getSafeStub);
        getSafeStub.restore();
      })
    done();
  });

  it('should rejects get safe details if the tvault api fails.', (done) => {
    let getSafeStub = sinon.stub(vault, "getSafeDetails").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.getSafeDetails(event.body, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(getSafeStub);
        getSafeStub.restore();
      });
    done();
  });
});

//Update Safe
describe('Update safe', () => {
  beforeEach(function () {
    event = {
      "method": "PUT",
      "stage": "test",
      "resourcePath": "/safes/{safename}",
      "body": {
        "name": "testsafe",
        "owner": "test",
        "description": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `Safe testsafe successfully updated.` } }
    };
  });

  it('should update safe in tvault with valid input', (done) => {
    let updateSafeStub = sinon.stub(vault, "updateSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.updateSafe(event.body, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(updateSafeStub);
        updateSafeStub.restore();
      })
    done();
  });

  it('should rejects update safe in tvault if safe updation in tvault fails.', (done) => {
    let updateSafeStub = sinon.stub(vault, "updateSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.updateSafe(event.body, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(updateSafeStub);
        updateSafeStub.restore();
      });
    done();
  });

});

//Delete Safe
describe('Delete safe', () => {
  beforeEach(function () {
    event = {
      "method": "DELETE",
      "stage": "test",
      "resourcePath": "/safes/{safename}",
      "path": {
        "safename": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `Safe testsafe successfully deleted.` } }
    };
  });

  it('should delete safe in tvault with valid input', (done) => {
    let deleteSafeStub = sinon.stub(vault, "deleteSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.deleteSafe(event.path.safename, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(deleteSafeStub);
        deleteSafeStub.restore();
      })
    done();
  });

  it('should rejects delete safe in tvault if safe deletion in tvault fails.', (done) => {
    let deleteSafeStub = sinon.stub(vault, "deleteSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.deleteSafe(event.path.safename, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(deleteSafeStub);
        deleteSafeStub.restore();
      });
    done();
  });
});

//Create user in safe
describe('Create user in safe', () => {
  beforeEach(function () {
    event = {
      "method": "POST",
      "stage": "test",
      "resourcePath": "/safes/{safename}/user",
      "path": {
        "safename": "testsafe"
      },
      "body": {
        "username": "test@test.com"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `User test@test.com associated with safe testsafe` } }
    };
  });

  it('should create user in safe in tvault with valid input', (done) => {
    let createUserInSafeStub = sinon.stub(vault, "createUserInSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.createUserInSafe(event.body, event.path.safename, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(createUserInSafeStub);
        createUserInSafeStub.restore();
      })
    done();
  });

  it('should rejects create user in safe in tvault if the tvault api fails.', (done) => {
    let createUserInSafeStub = sinon.stub(vault, "createUserInSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.createUserInSafe(event.body, event.path.safename, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(createUserInSafeStub);
        createUserInSafeStub.restore();
      });
    done();
  });

});

//Delete user from safe
describe('Delete user from safe', () => {
  beforeEach(function () {
    event = {
      "method": "DELETE",
      "stage": "test",
      "resourcePath": "/safes/{safename}/user",
      "path": {
        "safename": "testsafe"
      },
      "body": {
        "username": "test@test.com"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `User test@test.com successfully deleted` } }
    };
  });

  it('should delete user from safe in tvault with valid input', (done) => {
    let deleteUserFromSafeStub = sinon.stub(vault, "deleteUserFromSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.deleteUserFromSafe(event.body, event.path.safename, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(deleteUserFromSafeStub);
        deleteUserFromSafeStub.restore();
      })
    done();
  });

  it('should rejects delete user from safe in tvault if the tvault api fails.', (done) => {
    let deleteUserFromSafeStub = sinon.stub(vault, "deleteUserFromSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.deleteUserFromSafe(event.body, event.path.safename, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(deleteUserFromSafeStub);
        deleteUserFromSafeStub.restore();
      });
    done();
  });

});

//Create role in safe
describe('Create role in safe', () => {
  beforeEach(function () {
    event = {
      "method": "POST",
      "stage": "test",
      "resourcePath": "/safes/{safename}/role",
      "path": {
        "safename": "testsafe"
      },
      "body": {
        "rolename": "testrole"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `Role successfully created` } }
    };
  });

  it('should create role in safe in tvault with valid input', (done) => {
    let createRoleInSafeStub = sinon.stub(vault, "createRole").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.createRoleInSafe(event.body, event.path.safename, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(createRoleInSafeStub);
        createRoleInSafeStub.restore();
      })
    done();
  });

  it('should rejects create role in safe in tvault if the tvault api fails.', (done) => {
    let createRoleInSafeStub = sinon.stub(vault, "createRole").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.createRoleInSafe(event.body, event.path.safename, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(createRoleInSafeStub);
        createRoleInSafeStub.restore();
      });
    done();
  });

});

//Delete role from safe
describe('Delete role from safe', () => {
  beforeEach(function () {
    event = {
      "method": "DELETE",
      "stage": "test",
      "resourcePath": "/safes/{safename}/role",
      "path": {
        "safename": "testsafe"
      },
      "body": {
        "username": "testrole"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `User test@test.com successfully deleted` } }
    };
  });

  it('should delete role from safe in tvault with valid input', (done) => {
    let deleteRoleFromSafeStub = sinon.stub(vault, "deleteRoleFromSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.deleteRoleFromSafe(event.body, event.path.safename, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(deleteRoleFromSafeStub);
        deleteRoleFromSafeStub.restore();
      })
    done();
  });

  it('should rejects delete role from safe in tvault if the tvault api fails.', (done) => {
    let deleteRoleFromSafeStub = sinon.stub(vault, "deleteRoleFromSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.deleteRoleFromSafe(event.body, event.path.safename, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(deleteRoleFromSafeStub);
        deleteRoleFromSafeStub.restore();
      });
    done();
  });

});

//Get role
describe('Get role details', () => {
  beforeEach(function () {
    event = {
      "method": "GET",
      "stage": "test",
      "resourcePath": "/safes/{safename}/role",
      "query": {
        "rolename": "testrole"
      },
      "path": {
        "safename": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: {
        "data": {

        }
      }
    };
  });

  it('should get role details with valid safename', (done) => {
    let getRoleInSafeStub = sinon.stub(vault, "getRoleInSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.getRoleInSafe(event.body, config, vaultToken)
      .then((result) => {
        expect(result.body.data).to.eq(response.body.data)
        sinon.assert.calledOnce(getRoleInSafeStub);
        getRoleInSafeStub.restore();
      })
    done();
  });

  it('should rejects get role details if the tvault api fails.', (done) => {
    let getRoleInSafeStub = sinon.stub(vault, "getRoleInSafe").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.getRoleInSafe(event.body, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(getRoleInSafeStub);
        getRoleInSafeStub.restore();
      });
    done();
  });
});

//Create user in vault
describe('Create user in vault', () => {
  beforeEach(function () {
    event = {
      "method": "POST",
      "stage": "test",
      "resourcePath": "/safes/user",
      "body": {
        "username": "test@test.com",
        "password": "tester"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `User test@test.com successfully created` } }
    };
  });

  it('should create user in tvault with valid input', (done) => {
    let createUserInVaultStub = sinon.stub(vault, "createUserInVault").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.createUserInVault(event.body, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(createUserInVaultStub);
        createUserInVaultStub.restore();
      })
    done();
  });

  it('should rejects create user in tvault if the tvault api fails.', (done) => {
    let createUserInVaultStub = sinon.stub(vault, "createUserInVault").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.createUserInVault(event.body, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(createUserInVaultStub);
        createUserInVaultStub.restore();
      });
    done();
  });

});

//Delete user from vault
describe('Delete user from vault', () => {
  beforeEach(function () {
    event = {
      "method": "DELETE",
      "stage": "test",
      "resourcePath": "/safes/user",
      "body": {
        "username": "test@test.com"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `User test@test.com successfully deleted` } }
    };
  });

  it('should delete user in tvault with valid input', (done) => {
    let deleteUserFromVaultStub = sinon.stub(vault, "deleteUserFromVault").callsFake((config, service_data, vaultToken, cb) => {
      return cb(null, response);
    });

    index.deleteUserFromVault(event.body, config, vaultToken)
      .then((result) => {
        sinon.assert.calledOnce(deleteUserFromVaultStub);
        deleteUserFromVaultStub.restore();
      })
    done();
  });

  it('should rejects delete user from tvault if the tvault api fails.', (done) => {
    let deleteUserFromVaultStub = sinon.stub(vault, "deleteUserFromVault").callsFake((config, service_data, vaultToken, cb) => {
      return cb({ message: "Intenal server error" });
    });

    index.deleteUserFromVault(event.body, config, vaultToken)
      .catch((error) => {
        expect(error).to.include({
          message: "Intenal server error"
        })
        sinon.assert.calledOnce(deleteUserFromVaultStub);
        deleteUserFromVaultStub.restore();
      });
    done();
  });

});

//Index file - Create safe
describe('Index file - Create safe', () => {
  beforeEach(function () {
    event = {
      "method": "POST",
      "stage": "test",
      "principalId": "test123",
      "resourcePath": "/safes",
      "body": {
        "name": "testsafe",
        "owner": "test",
        "description": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";
    response = {
      statusCode: 200,
      body: { "data": { "message": `Safe testsafe and associated read/write/deny policies are created.` } }
    };
  });

  it('should throw error if the method is empty', (done) => {
    delete event.method;
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const createSafeStub = sinon.stub(index, "createSafe").resolves();
    let error = "{\"errorType\":\"BadRequest\",\"message\":\"Method cannot be empty\"}";

    index.handler(event, context, (err, res) => {
      console.log("ERRRRR: " + JSON.stringify(err))
      expect(err).to.eq(error);
      sinon.assert.notCalled(validateCreateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should throw error if the principalId is not there', (done) => {
    delete event.principalId;
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const createSafeStub = sinon.stub(index, "createSafe").resolves();
    let error = "{\"errorType\":\"Unauthorized\",\"message\":\"You aren't authorized to access this service\"}";

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(error);
      sinon.assert.notCalled(validateCreateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should create safe in tvault with valid input', (done) => {
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const createSafeStub = sinon.stub(index, "createSafe").resolves();

    index.handler(event, context, (err, res) => {
      sinon.assert.calledOnce(validateCreateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.calledOnce(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should throw error if validateCreateSafeInput throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Input cannot be empty" };
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").rejects(error);
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const createSafeStub = sinon.stub(index, "createSafe").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Input cannot be empty" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateCreateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should throw error if genericInputValidation throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Following field(s) has empty value - name" };
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").rejects(error);
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const createSafeStub = sinon.stub(index, "createSafe").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Following field(s) has empty value - name" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateCreateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should throw error if validateFieldLength throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Following field(s) not satisfying the char length - name" };
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").rejects(error);
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const createSafeStub = sinon.stub(index, "createSafe").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Following field(s) not satisfying the char length - name" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateCreateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should throw error if getVaultToken fails', (done) => {
    let error = { "error": "InternalServerError", "message": "Internal server error" };
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").rejects(error);
    const createSafeStub = sinon.stub(index, "createSafe").resolves();

    let errorResp = { "errorType": "InternalServerError", "message": "InternalServerError" };

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errorResp));
      sinon.assert.calledOnce(validateCreateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.notCalled(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

  it('should throw error if createSafe fails', (done) => {
    let error = { "error": "InternalServerError", "message": "Internal server error" };
    const validateCreateSafeInputStub = sinon.stub(validations, "validateCreateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves(vaultToken);
    const createSafeStub = sinon.stub(index, "createSafe").rejects(error);

    let errorResp = { "errorType": "InternalServerError", "message": "InternalServerError" };

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errorResp));
      sinon.assert.calledOnce(validateCreateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.calledOnce(createSafeStub);
      validateCreateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      createSafeStub.restore();
    })
    done();
  });

});

//Index file - Update safe
describe('Index file - Update safe', () => {
  beforeEach(function () {
    event = {
      "method": "PUT",
      "stage": "test",
      "principalId": "test123",
      "resourcePath": "/safes/{safename}",
      "path": {
        "safename": "testsafe"
      },
      "body": {
        "owner": "test",
        "description": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";   
  });

  it('should throw error if the method is empty', (done) => {
    delete event.method;
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();
    let error = "{\"errorType\":\"BadRequest\",\"message\":\"Method cannot be empty\"}";

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(error);
      sinon.assert.notCalled(validateUpdateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should throw error if the principalId is not there', (done) => {
    delete event.principalId;
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();
    let error = "{\"errorType\":\"Unauthorized\",\"message\":\"You aren't authorized to access this service\"}";

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(error);
      sinon.assert.notCalled(validateUpdateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should update safe in tvault with valid input', (done) => {
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();

    index.handler(event, context, (err, res) => {
      sinon.assert.calledOnce(validateUpdateSafeInputStub);
      sinon.assert.calledTwice(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.calledOnce(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should throw error if validateUpdateSafeInput throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Input cannot be empty" };
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").rejects(error);
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Input cannot be empty" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateUpdateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should throw error if genericInputValidation throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Following field(s) has empty value - name" };
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").rejects(error);
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Following field(s) has empty value - name" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateUpdateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);
      sinon.assert.notCalled(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should throw error if validateFieldLength throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Following field(s) not satisfying the char length - name" };
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").rejects(error);
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Following field(s) not satisfying the char length - name" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateUpdateSafeInputStub);
      sinon.assert.calledTwice(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should throw error if getVaultToken fails', (done) => {
    let error = { "error": "InternalServerError", "message": "Internal server error" };
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").rejects(error);
    const updateSafeStub = sinon.stub(index, "updateSafe").resolves();

    let errorResp = { "errorType": "InternalServerError", "message": "InternalServerError" };

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errorResp));
      sinon.assert.calledOnce(validateUpdateSafeInputStub);
      sinon.assert.calledTwice(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.notCalled(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

  it('should throw error if updateSafe fails', (done) => {
    let error = { "error": "InternalServerError", "message": "Internal server error" };
    const validateUpdateSafeInputStub = sinon.stub(validations, "validateUpdateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();
    const validateFieldLengthStub = sinon.stub(validations, "validateFieldLength").resolves();
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves("s.token");
    const updateSafeStub = sinon.stub(index, "updateSafe").rejects(error);

    let errorResp = { "errorType": "InternalServerError", "message": "InternalServerError" };

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errorResp));
      sinon.assert.calledOnce(validateUpdateSafeInputStub);
      sinon.assert.calledTwice(genericInputValidationStub);
      sinon.assert.calledOnce(validateFieldLengthStub);
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.calledOnce(updateSafeStub);
      validateUpdateSafeInputStub.restore();
      genericInputValidationStub.restore();
      validateFieldLengthStub.restore();
      getVaultTokenStub.restore();
      updateSafeStub.restore();
    })
    done();
  });

});

//Index file - Get safe details
describe('Index file - Get safe details', () => {
  beforeEach(function () {
    event = {
      "method": "GET",
      "stage": "test",
      "principalId": "test123",
      "resourcePath": "/safes/{safename}",
      "path": {
        "safename": "testsafe"
      }
    };
    context = awsContext();
    callback = (value) => {
      return value;
    };
    config = configModule.getConfig(event, context);
    vaultToken = "s.ktdfdsfltn";   
  });

  it('should throw error if the method is empty', (done) => {
    delete event.method;
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").resolves();
    let error = "{\"errorType\":\"BadRequest\",\"message\":\"Method cannot be empty\"}";

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(error);
      sinon.assert.notCalled(validateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);        
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });

  it('should throw error if the principalId is not there', (done) => {
    delete event.principalId;
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").resolves();
    let error = "{\"errorType\":\"Unauthorized\",\"message\":\"You aren't authorized to access this service\"}";

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(error);
      sinon.assert.notCalled(validateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);        
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });

  it('should get safe details with valid input', (done) => {
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").resolves();

    index.handler(event, context, (err, res) => {
      sinon.assert.calledOnce(validateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);       
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.calledOnce(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });

  it('should throw error if validateSafeInput throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Input cannot be empty" };
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").rejects(error);
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Input cannot be empty" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateSafeInputStub);
      sinon.assert.notCalled(genericInputValidationStub);        
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });

  it('should throw error if genericInputValidation throws error', (done) => {
    let error = { "errorType": "inputError", "message": "Following field(s) has empty value - name" };
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").rejects(error);      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves();
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").resolves();

    let errResp = { "errorType": "BadRequest", "message": "Following field(s) has empty value - name" }
    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errResp));
      sinon.assert.calledOnce(validateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);        
      sinon.assert.notCalled(getVaultTokenStub);
      sinon.assert.notCalled(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });    

  it('should throw error if getVaultToken fails', (done) => {
    let error = { "error": "InternalServerError", "message": "Internal server error" };
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").rejects(error);
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").resolves();

    let errorResp = { "errorType": "InternalServerError", "message": "InternalServerError" };

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errorResp));
      sinon.assert.calledOnce(validateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);       
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.notCalled(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });

  it('should throw error if getSafeDetails fails', (done) => {
    let error = { "error": "InternalServerError", "message": "Internal server error" };
    const validateSafeInputStub = sinon.stub(validations, "validateSafeInput").resolves();
    const genericInputValidationStub = sinon.stub(validations, "genericInputValidation").resolves();      
    const getVaultTokenStub = sinon.stub(vault, "getVaultToken").resolves("s.token");
    const getSafeDetailsStub = sinon.stub(index, "getSafeDetails").rejects(error);

    let errorResp = { "errorType": "InternalServerError", "message": "InternalServerError" };

    index.handler(event, context, (err, res) => {
      expect(err).to.eq(JSON.stringify(errorResp));
      sinon.assert.calledOnce(validateSafeInputStub);
      sinon.assert.calledOnce(genericInputValidationStub);       
      sinon.assert.calledOnce(getVaultTokenStub);
      sinon.assert.calledOnce(getSafeDetailsStub);
      validateSafeInputStub.restore();
      genericInputValidationStub.restore();        
      getVaultTokenStub.restore();
      getSafeDetailsStub.restore();
    })
    done();
  });

});