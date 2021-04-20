#!/bin/bash


curl ifconfig.co 
git clone -b master --depth 1 $SCRIPTS_REPO  $WORKING_MODULE_DIRECTORY    # Cloning  build module

#Creating PipelineLogs repo
mkdir -p $WORKING_DIRECTORY/PipelineLogs

groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/Login.groovy  getAuthToken  || exit 1  #getting auth token
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ConfigLoader.groovy   getConfigData || exit 1  #getting config data
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy  getServiceDetailsByServiceRepoUrl || exit 1   #getting service details

#Initializing function pipeline
groovy -cp $WORKING_MODULE_DIRECTORY/ scripts/ServerlessPipelineUtility.groovy initialize || exit 1 

#Controlling the stages of the deployment
# groovy -cp $WORKING_MODULE_DIRECTORY/ scripts/ServerlessPipelineUtility.groovy configDeployment || exit 1 

