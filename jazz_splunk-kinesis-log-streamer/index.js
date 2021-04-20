/**
	Custom Splunk cloudWatch logs streamer.
	@Author:
	@version: 1.0
**/

'use strict';

const SplunkLogger = require('splunk-logging').Logger;
const zlib = require('zlib');
const configData = require("./components/config.js"); //Import the environment data.
const logger = require("./components/logger.js"); //Import the logging module.
const utils = require("./components/utils.js"); //Import the utils module.
const global_config = require("./config/global-config.json");

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";


function handler(event, context, callback){

  logger.init();
  logger.debug('Received event: ' + JSON.stringify(event));
  let config = configData.getConfig(event, context);
  let isSplunkEnabled = (global_config.ENABLE_SPLUNK && (global_config.ENABLE_SPLUNK).toString().toLowerCase() === 'true') ? true : false;
  logger.info('Splunk Enabled: ' + isSplunkEnabled);
  
  if (isSplunkEnabled && event.Records && event.Records.length) {
    logger.debug('Number of kinesis records to process: ' + event.Records.length);
    const loggerConfig = {
      url: config.SPLUNK_ENDPOINT,
      token: config.SPLUNK_TOKEN,
      maxBatchCount: 0, // Manually flush events
      maxRetries: 1 // Retry 1 times
    };
  
    const splunkLog = new SplunkLogger(loggerConfig);
    // Set common error handler for splunkLog.send() and splunkLog.flush()
    splunkLog.error = (error, context) => {
      logger.error('splunk logger - error: ' + error + ', context: ' + JSON.stringify(context));
      return callback(JSON.stringify(error));
    };
  
    try {
      event.Records.forEach(eachRecord => {
        // CloudWatch Logs data is base64 encoded so decode here
        logger.debug ("processing stream event, id: " + eachRecord.eventID);
        let payload = Buffer.from(eachRecord.kinesis.data, 'base64');
        
        // CloudWatch logs are gzip compressed, so expand here
        zlib.gunzip(payload, (error, result) => {
          if (error) {
            logger.error("Error while unzipping payload from cloudwatch, skipping this payload: " + JSON.stringify(error));
            return callback(null, "Success");
          } else {
            // parse the result from JSON
            let awslogsData = {};
            try {
              awslogsData = JSON.parse(result.toString('ascii'));
            } catch (e) {
              logger.debug('JSON parse error while parsing decoded payload. Data might contain special characters, skipping this record..');
              logger.debug(e);
              return callback(null, "Success");
            }
            logger.debug('Decoded payload: ' + JSON.stringify(awslogsData));
            exportable.sendSplunkEvent(awslogsData, splunkLog, config)
              .then((count) => {
                if(count) {
                  splunkLog.flush((err, resp, body) => {
                    // Request failure or valid response from Splunk with HEC error code
                    if (err || (body && body.code !== 0)) {
                      logger.error("Exiting during an error scenario, error while performing splunk data flush: " + JSON.stringify(err));
                      return callback(JSON.stringify(err || body));
                    } else {
                      // If succeeded, body will be { text: 'Success', code: 0 }
                      logger.info('Response from Splunk: ' + JSON.stringify(body));
                      logger.info(`Successfully processed ${count} log event(s)`);
                      return callback(null, count); // Return number of log events
                    }
                  });
                } else {
                  logger.debug('No logs to send, continuing..');
                  return callback(null, "Success");
                }
              })
              .catch(error => {
                logger.error(JSON.stringify(error));
              	logger.debug('Error while pushing logs to Splunk, continuing..');
                return callback(null, "Success");
              });
          }
        });
      });
      
    } catch (e) {
      logger.error("Error while processing current batch of log records from cloudwatch: " + JSON.stringify(e));
      return callback(null, "Success");
    }
  } else {
    logger.debug("Either Splunk forwarder is disabled or there are no logs from cloudwatch (unlikely)");
    return callback(null, "Success");
  }
}

function sendSplunkEvent(awslogsData, splunkLog, config) {
  logger.debug("Log record to transform: " + JSON.stringify(awslogsData));
  return new Promise((resolve, reject) => {
    let count = 0;
    if (awslogsData.messageType === 'CONTROL_MESSAGE') {
      logger.debug('Received CONTROL MESSAGE, doing nothing..');
      resolve();
    } else if (awslogsData.logGroup.indexOf("API-Gateway-Execution-Logs") === 0) {
      utils.transformApiLogs(awslogsData)
        .then(splunkBulkData => {
          exportable.sendDataToSplunk(splunkLog, splunkBulkData, config);
          count++;
          resolve(count);
        })
        .catch(error => {
          logger.error("Error while transforming logs from AWS API Gateway, skipping this log event..");
          logger.error(error);
          resolve();
        });
    } else if (awslogsData.logGroup.indexOf("/aws/lambda/") === 0) {
      utils.getCommonData(awslogsData, config)
      .then(commonData => {
        awslogsData.logEvents.forEach(logEvent => {
          utils.transformLambdaLogs(logEvent, commonData)
            .then(event => {
              exportable.sendDataToSplunk(splunkLog, event, config);
              count++;
              if (count === awslogsData.logEvents.length) {
                resolve(count);
              }
            })
            .catch(error => {
              logger.error("Error while transforming logs from AWS Lambda, skipping this log event..");
              logger.error(error);
              resolve();
            });
        });
      })
      .catch(error => {
        logger.error("Error while retrieving common data for this lambda, skipping this log event..");
        logger.error(error);
        resolve();
      });
      
    } else {
      logger.debug('Received unsupported logs from cloudwatch (non APIGateway/Lambda logroups are detected). Cannot transform, doing nothing..');
      resolve();
    }
  });
}

function sendDataToSplunk(splunkLog, eventData, config) {
  logger.debug("Data being sent to splunk: " + JSON.stringify(eventData));
  let payload = {
    message: eventData.event,
    metadata:{
      sourcetype: eventData.sourcetype,
      index: config.SPLUNK_INDEX
    }
  };
  splunkLog.send(payload);
}

const exportable = {
  handler,
  sendSplunkEvent,
  sendDataToSplunk
};
module.exports = exportable;
