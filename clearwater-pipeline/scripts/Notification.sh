#!/bin/bash

#beforeScript
echo "sending STARTED event - CLEARWATER_SEND_NOTIFICATION"
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/EventsModule.groovy"  sendStartedEvent  "CLEARWATER_SEND_NOTIFICATION"
EXIT_CODE=$?
if [ $EXIT_CODE -gt 0 ]; then
    echo "script Started CLEARWATER_SEND_NOTIFICATION FAILED with exit code $EXIT_CODE, continuing..."
    exit 1
else
    # script
    pRLink=$(> "$PROPERTIES_FILE" jq '.clearwaterPrLink')
    echo "pRLink: $pRLink"

    serviceMeta=$(groovy -cp "$WORKING_MODULE_DIRECTORY/" scripts/Clearwater.groovy createMetadataObj pRLink)
    echo "serviceMeta: $serviceMeta"

    groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ClearwaterModule.groovy" getServiceOwnerInfo $serviceMeta
    EXIT_CODE1=$?
    if [ $EXIT_CODE1 -gt 0 ]; then
        echo "script getServiceOwnerInfo of swagger_url FAILED with exit code $EXIT_CODE1, continuing..."
        exit 1
    else
        # script
        serviceOwnerInfo=$(> "$PROPERTIES_FILE" jq '.clearwaterServiceOwner')
        echo "serviceOwnerInfo: $serviceOwnerInfo"

        cClists=$(> "$PROPERTIES_FILE" jq '.configData.CODE_QUALITY.CLEARWATER.PUBLISH_CCLIST')
        echo "cClists: $cClists"

        commitResult=$(> "$PROPERTIES_FILE" jq '.clearwaterCommitResult')
        echo "commitResult: $commitResult"

        groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ClearwaterModule.groovy" sendNotifications "$serviceOwnerInfo" "$cClists" "$serviceMeta" "$commitResult"
        EXIT_CODE2=$?
        if [ $EXIT_CODE2 -gt 0 ]; then
            echo "script getAssetProviderId of swagger_url FAILED with exit code $EXIT_CODE2, continuing..."
            exit 1
        else
            touch "$CI_PROJECT_DIR/success"
            "$WORKING_MODULE_DIRECTORY/scripts/OnCompletionJob.sh" "CLEARWATER_SEND_NOTIFICATION"
        fi
    fi
fi

#afterScript
rm -rf "$CI_PROJECT_DIR/success" # resetting
