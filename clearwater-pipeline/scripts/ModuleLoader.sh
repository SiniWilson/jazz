#!/bin/bash

eventType=$1
serviceId=$2
requestId=$3
description=$4

echo "eventType: $eventType"
echo "serviceId: $serviceId"
echo "requestId: $requestId"
echo "description: $description"


[[ -z "$serviceId" ]] && echo "Service id is not come as part of the request." && exit 1
[[ -z "$requestId" ]] && echo "Request id is not come as part of the request." && exit 1
[[ -z "$description" ]] && echo "Description is not come as part of the request." && exit 1

groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/EventsModule.groovy" initialize "$eventType"  || exit 1
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/Login.groovy" getAuthToken "$API_BASE_URL" "$JAZZ_SVC_ACCT_USER" "$JAZZ_SVC_ACCT_PASSWORD" "$requestId"  || exit 1  #getting auth token
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ConfigLoader.groovy" getConfigData "$API_BASE_URL"  || exit 1  #getting config data
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy" getServiceDetails "$API_BASE_URL" "$serviceId"  || exit 1   #getting service details
