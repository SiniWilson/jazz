#!/bin/bash

echo "sending STARTED event - CLEARWATER_INITIALIZATION"
groovy -cp "$WORKING_MODULE_DIRECTORY/" "$WORKING_MODULE_DIRECTORY/EventsModule.groovy" sendStartedEvent "CLEARWATER_INITIALIZATION"
EXIT_CODE=$?
if [ $EXIT_CODE -gt 0 ]; then
    echo "script Started CLEARWATER_INITIALIZATION FAILED with exit code $EXIT_CODE, continuing..."
    exit 1
else
   touch "$CI_PROJECT_DIR/success"
   "$WORKING_MODULE_DIRECTORY/scripts/OnCompletionJob.sh" "CLEARWATER_INITIALIZATION" "$EVENT_TYPE"
fi

rm -rf "$CI_PROJECT_DIR/success" # resetting