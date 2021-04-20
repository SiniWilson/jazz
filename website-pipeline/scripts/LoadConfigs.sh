#!/bin/bash


curl ifconfig.co 
git clone -b master $SCRIPTS_REPO  $WORKING_MODULE_DIRECTORY    # Cloning  build module
#bash $WORKING_MODULE_DIRECTORY/scripts/ModuleLoader.sh  || exit 1 

#Creating PipelineLogs repo
mkdir -p $WORKING_DIRECTORY/PipelineLogs

groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/Login.groovy  getAuthToken  || exit 1  #getting auth token
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ConfigLoader.groovy   getConfigData || exit 1  #getting config data
groovy -cp $WORKING_MODULE_DIRECTORY/ $WORKING_MODULE_DIRECTORY/ServiceMetadataLoader.groovy  getServiceDetailsByServiceRepoUrl || exit 1   #getting service details

#Initializing function pipeline
groovy -cp $WORKING_MODULE_DIRECTORY/ scripts/DeployWebsiteUtilityModule.groovy initialize || exit 1 

#Controlling the stages of the deployment
# groovy -cp $WORKING_MODULE_DIRECTORY/ scripts/DeployWebsiteUtilityModule.groovy configDeployment || exit 1 

