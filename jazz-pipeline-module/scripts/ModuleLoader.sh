#!/bin/bash

groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/Login.groovy  getAuthToken  || exit 1  #getting auth token
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ConfigLoader.groovy   getConfigData || exit 1  #getting config data
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy  getServiceDetails || exit 1   #getting service details
