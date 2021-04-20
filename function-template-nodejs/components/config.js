/**
  @module: config.js
  @description: Function to retrieve environment related configurations
	@author:
	@version: 1.0
**/

const fs = require('fs');
const path = require('path');

const getStageConfig = (event, context) => {
  let stage, configObj;

  if (event && event.stage) {
    stage = event.stage;
  } else if (context && context.functionName && context.functionName.length > 0) {
    let functionName = context.functionName;

    // Function naming convention: $domain_$service_$stage
    let fnName = functionName.substr(functionName.lastIndexOf('_') + 1, functionName.length);

    if (fnName.endsWith('stg')) {
      stage = 'stg';
    } else if (fnName.endsWith('prod')) {
      stage = 'prod';
    } else {
      stage = 'dev';
    } 
  }

  if (stage) {
    let configFile = path.join(__dirname, `../config/${stage}-config.json`);

    if (fs.existsSync(configFile)) {
      configObj = JSON.parse(fs.readFileSync(configFile));
    }
  }

  return configObj;
};

module.exports = {
	getConfig: getStageConfig
}
