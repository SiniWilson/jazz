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

const request = require('request');
const logger = require("../logger.js");

function getGraphToken(config, graphCreds, graphTokenExpiry, graphToken) {
	return new Promise(async (resolve, reject) => {
		logger.debug("getGraphToken----")
		// decode graph token and determine expiry
		let expiredToken = true;
		if (graphTokenExpiry) {
			// check with current time
			let currentTime = new Date();
			if (currentTime < graphTokenExpiry) {
				expiredToken = false;
			}
		}
		if (!graphToken || expiredToken) {
			logger.debug("No graph token available.")
			let payload = {
				url: `${config.GRAPH_API_TOKEN_URL}`,
				method: "GET",
				headers: {
					"Content-Type": "application/x-www-form-urlencoded"
				},
				form: {
					grant_type: "client_credentials",
					client_id: graphCreds.username,
					client_secret: graphCreds.password,
					scope: "https://graph.microsoft.com/.default"
				},
				rejectUnauthorized: false
			};

			logger.debug("getGraphToken payload: " + JSON.stringify(payload));
			request(payload, function (error, response, body) {
				logger.debug("getGraphToken response: " + JSON.stringify(response));
				if (error) {
					logger.error("Error in getting graph token: " + JSON.stringify(error));
					return reject(error);
				}
				if (response.statusCode && response.statusCode === 200) {
					graphToken = JSON.parse(response.body).access_token;
					// expiry = current time + 59 minutes
					let expiryMinutes = 59;
					let minToMilliseconds = 60000;
					graphTokenExpiry = new Date(new Date().getTime() + expiryMinutes * minToMilliseconds);;
					logger.debug("got graph token: " + graphToken)
					return resolve({"graphToken": graphToken,  "graphTokenExpiry": graphTokenExpiry });
				} else {
					logger.error("Invalid/expired credentials. " + JSON.stringify(response));
					return reject({
						"auth_error": `Invalid/expired credentials - ${JSON.stringify(response)}`
					});
				}
			});
		} else {
			logger.debug(`Doing nothing, graphToken is already available!`);
			return resolve({"graphToken": graphToken,  "graphTokenExpiry": graphTokenExpiry});
		}
	})
}

function getSecret(secretsMgrClient, config, graphSecret) {
	return new Promise(async (resolve, reject) => {
		logger.debug("getSvcPrincipalCredentials")
		if (!graphSecret) {
			logger.debug("No graph secret available.")
			await secretsMgrClient.getSecretValue(
				{
					SecretId: config.SVC_PRINCIPAL_PWD_LOCATION
				},
				function (err, data) {
					logger.debug("getSvcPrincipalCredentials- err: " + JSON.stringify(err))
					logger.debug("getSvcPrincipalCredentials- data: " + JSON.stringify(data))
					if (err) {
						logger.error(err);
						return reject({
							"auth_error": true,
							"message": `secret_error: Error occurred while getting the secret from secret store, error: ${err.code}`
						})
					} else {
						// Decrypts secret using the associated KMS CMK.
						// Depending on whether the secret is a string or binary, one of these fields will be populated.
						if ("SecretString" in data) {
							// Secret is stored as JSON in secrets manager
							graphSecret = JSON.parse(data.SecretString);
							logger.debug("graphSecret-: " + JSON.stringify(graphSecret))
							return resolve(graphSecret);
						} else {
							return reject({
								"auth_error": true,
								"message": `secret_error: Failed while parsing password within the secret`
							})
						}
					}
				}
			);
		} else {
            /**
             * Do nothing, secret is already available!
             */
			logger.debug(`Doing nothing, secret (user password) is already available!`);
			return resolve(graphSecret);
		}
	});
}

module.exports = {
  getGraphToken,
  getSecret
};

