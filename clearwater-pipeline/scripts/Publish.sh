#!/bin/bash
description=$1

#beforeScript
echo "sending STARTED event - CLEARWATER_FORK_REPO"
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/EventsModule.groovy" sendStartedEvent "CLEARWATER_FORK_REPO"
EXIT_CODE=$?
if [ $EXIT_CODE -gt 0 ]; then
    echo "script Started CLEARWATER_FORK_REPO FAILED with exit code $EXIT_CODE, continuing..."
    exit 1
else
    # script
    serviceRepoSlug=$(> "$PROPERTIES_FILE" jq '.serviceRepoSlug' | tr -d '"')
    echo "serviceRepoSlug: $serviceRepoSlug"
    domainName=$(> "$PROPERTIES_FILE" jq '.serviceConfig.domain'  | tr -d '"')
    serviceName=$(> "$PROPERTIES_FILE" jq '.serviceConfig.service'  | tr -d '"')

    groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ClearwaterModule.groovy" createForkAndCopyFiles "$serviceRepoSlug" "$serviceName" "$domainName" "$JAZZ_SVC_ACCT_USER" "$JAZZ_SVC_ACCT_PASSWORD"
    EXIT_CODE1=$?
    echo "EXIT_CODE1 code: $EXIT_CODE1"
    if [ $EXIT_CODE1 -gt 0 ]; then
        echo "script createForkAndCopyFiles FAILED with exit code $EXIT_CODE1, continuing..."
        exit 1
    else
        # script
        commitResult=$(> "$PROPERTIES_FILE" jq '.clearwaterCommitResult')
        echo "commitResult: $commitResult"

        status=$(echo "$commitResult" | jq '.status' | tr -d '"')

        if [[ $status == true ]]; then
            groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/ClearwaterModule.groovy" raisePRForPublishing "$serviceRepoSlug" "$description" "$JAZZ_SVC_ACCT_USER" "$JAZZ_SVC_ACCT_PASSWORD"
            EXIT_CODE2=$?
            echo "EXIT_CODE1 code: $EXIT_CODE1"
            if [ $EXIT_CODE2 -gt 0 ]; then
                echo "script raisePRForPublishing FAILED with exit code $EXIT_CODE2, continuing..."
                exit 1
            else
                touch "$CI_PROJECT_DIR/success"
                "$WORKING_MODULE_DIRECTORY/scripts/OnCompletionJob.sh" "CLEARWATER_FORK_REPO"
            fi
        fi
    fi
fi

#afterScript
rm -rf "$CI_PROJECT_DIR/success" # resetting


