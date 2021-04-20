/**
    Nodejs Template Project
    @author:
    @version: 1.0
 **/

const errorHandlerModule = require("./components/error-handler.js"); //Import the error codes module.
const configObj = require("./components/config.js"); //Import the config data.
const logger = require("./components/logger.js"); //Import the logging module.

module.exports.handler = (event, context, cb) => {
    //Initializations
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

        const sampleResponse = {
            "foo": "bar"
        };

        logger.info(event);
        logger.info(context);
        
        //Your GET method should be handled here
        if (event && event.method && event.method === "GET") {
            sampleResponse.input = event.query;
            logger.debug(sampleResponse);

            return cb(null, sampleResponse);
        }

        //Your POST method should be handled here
        if (event && event.method && event.method === "POST") {
            sampleResponse.input = event.body;
            logger.debug(sampleResponse);

            return cb(null, sampleResponse);
        }
    } catch (e) {
        //Sample Error response for internal server error
        return cb(JSON.stringify(errorHandler.throwInternalServerError("Sample message")));

        //Sample Error response for Not Found Error
        //return cb(JSON.stringify(errorHandler.throwNotFoundError("Sample message")));

        //Sample Error response for Input Validation Error
        //return cb(JSON.stringify(errorHandler.throwInputValidationError("Sample message")));
    }
};
