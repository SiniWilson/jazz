/**
	API to post events to event bus & get events from event store.
	@version: 2.0
**/

const errorHandlerModule = require("./components/error-handler.js");
const responseObj = require("./components/response.js");
const configObj = require("./components/config.js");
const logger = require("./components/logger.js");

const AWS = require('aws-sdk');
const async = require('async');

module.exports.handler = (event, context, cb) => {
	var errorHandler = errorHandlerModule();
	var config = configObj.getConfig(event, context);
	logger.init(event, context);
	const dynamodb = new AWS.DynamoDB();
	const eventBridge = new AWS.EventBridge();
	
	try {
		if (!event || !event.method){
			cb(JSON.stringify(errorHandler.throwInternalServerError('Unknown event/method for this request')));
		}
		else {
			if (event.method === 'GET') {
				async.series({
					get_events: function (callback) {
						var filter = "";
						var attributeValues = {};
	
						var scanparams = {
							"TableName": config.events_table,
							"ReturnConsumedCapacity": "TOTAL",
							"Limit": "500"
						};
						console.log(event.query);
						if (event.query !== undefined && event.query !== null && Object.keys(event.query).length > 0) {
							Object.keys(event.query).forEach(function (key) {
								if (key === "last_evaluated_key") {
									scanparams.ExclusiveStartKey = event.query[key];
								} else if (key === "username") {
									filter = filter + "USERNAME = :USERNAME AND ";
									attributeValues[":USERNAME"] = {
										'S': event.query[key]
									};
								} else if (key === "service_name") {
									filter = filter + "SERVICE_NAME = :SERVICE_NAME AND ";
									attributeValues[":SERVICE_NAME"] = {
										'S': event.query[key]
									};
								} else {
									//do nothing because we only support username, service_name, and last_evaluated_key for now
	
								}
							});
	
							if (filter === "") {
								return callback(null, {});
							}
							scanparams.FilterExpression = filter.substring(0, filter.length - 5);
							scanparams.ExpressionAttributeValues = attributeValues;
							dynamodb.scan(scanparams, function (err, items) {
								if (err) {
	
									logger.error("error in dynamodb scan");
									logger.error(err);
									callback(err);
	
								} else {
									callback(null, items);
								}
							});
						} else {
							callback(null, {});
						}
					}
				}, function (err, results) {
	
					if (err) {
						logger.error(err);
						cb(JSON.stringify(errorHandler.throwInternalServerError("An internal error occured. message: " + err.message)));
					} else {
						var events = [];
						if (results.get_events !== undefined && results.get_events !== "" && results.get_events.Items !== undefined && results.get_events.Items !== "") {
							results.get_events.Items.forEach(function (item) {
								var event = {};
								
								Object.keys(item).forEach(function (key) {
									if (key === "SERVICE_CONTEXT" ){
										event.service_context = item.SERVICE_CONTEXT.S;
									}else if (key === "EVENT_HANDLER"){
										event.event_handler = item.EVENT_HANDLER.S;
									}else if (key === "EVENT_NAME"){
										event.event_name = item.EVENT_NAME.S;
									}else if (key === "SERVICE_NAME"){
										event.service_name = item.SERVICE_NAME.S;
									}else if (key === "EVENT_TYPE"){
										event.event_type = item.EVENT_TYPE.S;
									}else if (key === "EVENT_STATUS"){
										event.event_status = item.EVENT_STATUS.S;
									}else if (key === "USERNAME"){
										event.username = item.USERNAME.S;
									}else if (key === "EVENT_TIMESTAMP"){
										event.event_timestamp = item.EVENT_TIMESTAMP.S;
									}
									else{
										if (item[key].NULL === true){
											event[key.toLowerCase()] = null;
										}
										else{
											event[key.toLowerCase()] = item[key].S;
										}
									}
								});
								events.push(event);
							});
	
							if (results.get_events.LastEvaluatedKey !== undefined || results.get_events.LastEvaluatedKey !== "") {
								cb(null, responseObj({
										"events": events,
										"last_evaluated_key": results.get_events.LastEvaluatedKey
									}, event.query));
							} else {
								cb(null, responseObj({
										"events": events
									}, event.query));
							}
						} else {
							cb(JSON.stringify(errorHandler.throwInputValidationError("Bad request. message: The query parameters supported are username, service_name, and last_evaluated_index")));
						}
					}
	
				});
			} else if (event.method === 'POST') {
				if (!event.body) {
					return cb(JSON.stringify(errorHandler.throwInternalServerError("Missing payload!")));
				}
				if (!event.body.service_context) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("service_context not provided!")));
				}
				if (!event.body.event_handler) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("event_handler not provided!")));
				}
				if (!event.body.event_name) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("event_name not provided!")));
				}
				if (!event.body.service_name) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("service_name not provided!")));
				}
				if (!event.body.event_status) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("event_status not provided!")));
				}
				if (!event.body.event_type) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("event_type not provided!")));
				}
				if (!event.body.username) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("username not provided!")));
				}
				if (!event.body.event_timestamp) {
					return cb(JSON.stringify(errorHandler.throwInputValidationError("event_timestamp not provided!")));
				}
	
				var jazzEvent = {
					Source: config.eventSource,
					EventBusName: config.eventBusName,
					Resources: [],
					Detail: {},
					DetailType: ""
				};
	
				Object.keys(event.body).forEach(function (key) {
					if (key === "event_type") {
						jazzEvent.DetailType = `Jazz ${event.body.event_type.toLowerCase()} notification`;
						jazzEvent.Detail[key] = event.body.event_type.toLowerCase();
					} else if (key === "service_context") {
						jazzEvent.Detail[key] = JSON.parse(JSON.stringify(event.body.service_context));
					} else if (['event_handler', 'event_name', 'event_status'].includes(key)) {
						jazzEvent.Detail[key] = event.body[key.toLowerCase()];
					} else {
						jazzEvent.Detail[key] = event.body[key];
					}
				});
				var params = {
					Entries: [
						jazzEvent
					]
				};
				eventBridge.putEvents(params, function(err, data) {
					if (err) {
						logger.error("Error while ingesting event to the event bus: " + JSON.stringify(err));
						cb(JSON.stringify(errorHandler.throwInternalServerError("Internal error occurred: " + err.message)));
					} else {
						logger.debug("Ingested the event to the event bus. Response: " + JSON.stringify(data));
						if (data.Entries.length > 0) {
							logger.debug("Response from event bus: " + JSON.stringify(data.Entries[0]));
							if (data.Entries[0].EventId) {
								logger.info("Event got sent successfully! Response: " + JSON.stringify(data.Entries[0]));
								cb(null, {"event_id": data.Entries[0].EventId});
							} else {
								logger.info("Failed while ingesting the event! Response: " + JSON.stringify(data.Entries[0].ErrorMessage));
								cb(JSON.stringify(errorHandler.throwInternalServerError('Failed while ingesting the event, please retry!')));
							}	
						} else {
							logger.error("Unknown error while ingesting event to the event bus, invalid response from event bus: " + JSON.stringify(data));
							cb(JSON.stringify(errorHandler.throwInternalServerError("An internal error occurred: ")));
						}
					}
				});
			} else {
				cb(JSON.stringify(errorHandler.throwInternalServerError('Method not implemented')));
			}
		}
	} catch (e) {
		logger.error(e);
		cb(JSON.stringify(errorHandler.throwInternalServerError(e)));
	}
};
