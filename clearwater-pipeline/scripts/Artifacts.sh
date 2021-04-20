#!/bin/bash

# beforeScript
echo "sending STARTED event - CLEARWATER_GET_ARTIFACTS"
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/EventsModule.groovy" sendStartedEvent "CLEARWATER_GET_ARTIFACTS"
EXIT_CODE=$?
swagger
puml
sequenceDiagram
if [ $EXIT_CODE -gt 0 ]; then
    echo "script Started CLEARWATER_GET_ARTIFACTS FAILED with exit code $EXIT_CODE, continuing..."
    exit 1
else
    # script
    groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy" getAssetProviderId "swagger_url" "prod"
    EXIT_CODE1=$?
    if [ $EXIT_CODE1 -gt 0 ]; then
        echo "script getAssetProviderId of swagger_url FAILED with exit code $EXIT_CODE1, continuing..."
        exit 1
    else
        prop=$(< "$PROPERTIES_FILE")
        echo "prop: $prop"
        swagger=$(< "$PROPERTIES_FILE" jq '.assetProviderId' | tr -d '"' )
        echo "swagger: $swagger"
    fi
fi


# script
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy" getAssetProviderId "puml_url" "prod"
EXIT_CODE1=$?
if [ $EXIT_CODE1 -gt 0 ]; then
    echo "script getAssetProviderId of puml_url FAILED with exit code $EXIT_CODE1, continuing..."
    exit 1
else
    puml=$(< "$PROPERTIES_FILE" jq '.assetProviderId' | tr -d '"')
    echo "puml: $puml"
fi


# script
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy" getAssetProviderId "sequence_diagram_url" "prod"
EXIT_CODE1=$?
if [ $EXIT_CODE1 -gt 0 ]; then
    echo "script getAssetProviderId of sequence_diagram_url FAILED with exit code $EXIT_CODE1, continuing..."
else
    sequenceDiagram=$(< $"PROPERTIES_FILE" jq '.assetProviderId' | tr -d '"')
    echo "sequenceDiagram: $sequenceDiagram"
fi


# script
groovy -cp "$WORKING_MODULE_DIRECTORY/" scripts/Clearwater.groovy downloadArtifact "$swagger" "$puml" "$sequenceDiagram"
EXIT_CODE1=$?
if [ $EXIT_CODE1 -gt 0 ]; then
    echo "script downloadArtifact FAILED with exit code $EXIT_CODE1, continuing..."
else
    touch "$CI_PROJECT_DIR/success"
    bash "$WORKING_MODULE_DIRECTORY/scripts/OnCompletionJob.sh" "CLEARWATER_GET_ARTIFACTS"
fi

# afterScript
rm -rf "$CI_PROJECT_DIR/success" # resetting
