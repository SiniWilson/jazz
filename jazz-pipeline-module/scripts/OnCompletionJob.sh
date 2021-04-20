#!/bin/bash

eventName=$1


echo "eventType:- $EVENT_TYPE"

if [ -e success ]; then
    echo "sending event- $eventName completed" 
    groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/EventsModule.groovy  sendCompletedEvent  "$eventName"   || exit 1
else
    echo "sending event- $eventName failed..."
    groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/EventsModule.groovy  sendFailureEvent  "$eventName"   || exit 1
fi  
