/**
	Nodejs Function Template
	@Author:
	@version: 1.0
**/

const logger = require("./components/logger.js");
const configObj = require('./components/config.js');
const errorHandlerModule = require("./components/error-handler.js");

module.exports.handler = async (event, context) => {

  // Initialization
  const errorHandler = errorHandlerModule();
  const config = configObj.getConfig(event, context);
  logger.init();

  try {
    
    //Following is a code snippet to fetch values from config file:
    //const myVal = config.configKey;

    // Following code snippet describes how to log messages within your code:
    /*
      logger.error('Runtime errors or unexpected conditions.');
      logger.warn('Runtime situations that are undesirable or unexpected, but not necessarily "wrong".');
      logger.info('Interesting runtime events (Eg. connection established, data fetched etc.)');
      logger.debug('Detailed information on the flow through the system.');
    */

    logger.info("Sample log");

    // Your business logic goes here!

    return {
      message: 'Your function just got executed successfully!',
      input: event
    };

  } catch (e) {
    //Sample Error response for internal server error
    return errorHandler.throwInternalServerError("Sample error message");

  //Sample Error response for Not Found Error
  //return errorHandler.throwNotFoundError("Sample message");

  //Sample Error response for Input Validation Error
  //return errorHandler.throwInputValidationError("Sample message");
}

};
