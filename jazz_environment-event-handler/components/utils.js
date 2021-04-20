const format = require("string-template");

// function to get notification message from template.
var getNotificationMessage = function(event_name, event_status, service_name, error, notification_message) {
    var message;
	
	
	
    switch (event_status) {
        case "STARTED":
            message = format(notification_message.EVENT_NAME.STARTED, {
                service_name: service_name,
                event_name: event_name
            });
            break;
        case "FAILED":
            if (error === "" || error === undefined) {
                message = format(notification_message.EVENT_NAME.FAILED_REASON, {
                    service_name: service_name,
                    event_name: event_name,
                    reason: error
                });
            } else {
                message = format(notification_message.EVENT_NAME.FAILED, {
                    service_name: service_name,
                    event_name: event_name
                });
            }
            break;
        case "COMPLETED":
            message = format(notification_message.EVENT_NAME.COMPLETED, {
                service_name: service_name,
                event_name: event_name
            });
            break;
    }
	
    return message;
};

module.exports = () => {
    return {
        getNotificationMessage: getNotificationMessage
    };
};
