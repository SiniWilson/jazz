#!/bin/bash


git clone -b rollback --depth 1 $SCRIPTS_REPO  $WORKING_MODULE_DIRECTORY    # Cloning  build module

#Creating PipelineLogs repo
mkdir -p $WORKING_DIRECTORY/PipelineLogs

groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/Login.groovy  getAuthToken  || exit 1  #getting auth token
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ConfigLoader.groovy   getConfigData || exit 1  #getting config data

cp scripts/serverless.yml $WORKING_DIRECTORY/serverless.yml
