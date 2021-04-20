'use strict';

const logger = require("../../components/logger.js");
const errorHandlerModule = require("../../components/error-handler.js");

module.exports.hello = async (event, context) => {

  // Initialization
  const errorHandler = errorHandlerModule();
  logger.init();

  try {

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
      statusCode: 200,
      body: JSON.stringify(event),
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
