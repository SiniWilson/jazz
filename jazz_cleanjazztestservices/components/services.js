const AWS = require('aws-sdk');


const getServiceMetadata = (configData, onComplete) => {
    let dateValue = new Date();
    dateValue.setDate( dateValue.getDate() - parseInt(configData.JAZZTEST_LIFE_IN_DAYS) );
    dateValue = dateValue.toISOString();
    dateValue = dateValue.slice(0, -1);

    // initialize dynamodb
    AWS.config.update({ region: configData.DB_REGION });
    let dynamodb = new AWS.DynamoDB();

    var scanparams = {
        TableName: configData.SERVICES_TABLE_NAME,
        ReturnConsumedCapacity: "TOTAL",
        Limit: "500",
        ProjectionExpression: "SERVICE_ID, SERVICE_NAME, SERVICE_DOMAIN, SERVICE_STATUS"
    };

    let filter = "", attributeValues = {}, attributeNames = {};
    let filterString = "( ";
    configData.CLEANUP_DOMAIN_LIST.forEach(function (value) {
        filterString += " :" + value + " , ";
    });
    filterString = filterString.substring(0, filterString.length - 3);
    filterString += " )";
    filter = filter + "SERVICE_DOMAIN IN " + filterString;
    filter = filter + " AND " + "SERVICE_STATUS <> :status"
    filter = filter + " AND " + "#createdAt <= :datevalue"
    filter = filter + " AND " + "attribute_exists(SERVICE_REPOSITORY) AND SERVICE_REPOSITORY<> :repository"

    attributeNames[("#createdAt")] = 'TIMESTAMP'

    configData.CLEANUP_DOMAIN_LIST.forEach(function (value) {
        attributeValues[":" + value] = {
            S: value
        };
    });

    attributeValues[(":status")] = {
        'S': "deletion_completed"
    };

    attributeValues[(":datevalue")] = {
        'S': dateValue
    };
  
    attributeValues[(":repository")] = {
        'S': "[Archived]"
    };

    scanparams.FilterExpression = filter;
    scanparams.ExpressionAttributeValues = attributeValues;
    scanparams.ExpressionAttributeNames = attributeNames;

    var items_formatted = [];

    var scanExecute = function (onComplete) {
        dynamodb.scan(scanparams, function (err, items) {
            if (err) {
                onComplete(err);
            } else {
                let formatted_array = items.Items.map(item => {
                    return AWS.DynamoDB.Converter.unmarshall(item);
                });
                items_formatted.push(formatted_array)
                if (items.LastEvaluatedKey) {
                    scanparams.ExclusiveStartKey = items.LastEvaluatedKey;
                    scanExecute(onComplete);
                } else {
                    let servicesToDelete = [];
                    items_formatted = [].concat.apply([], items_formatted);
                    if (items_formatted.length) {
                        servicesToDelete = items_formatted.map(item => {
                            return {
                                "s": item.SERVICE_NAME,
                                "d": item.SERVICE_DOMAIN,
                                "id": item.SERVICE_ID,
                                "status": item.SERVICE_STATUS
                            };
                        });
                    }
                    onComplete(null, servicesToDelete);
                }
            }
        });
    };

    scanExecute(onComplete);
};

module.exports = {
    getServiceMetadata
};
