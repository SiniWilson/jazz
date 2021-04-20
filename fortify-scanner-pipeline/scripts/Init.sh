#!/bin/bash

# "$serviceId" "$requestId" "$branch" "$keyPrefix" "$commitHash"
serviceId=$1
requestId=$2
branch=$3
keyPrefix=$4
commitHash=$5

workingDirectory="WorkingDirectory"
moduleDirectory="$workingDirectory/jazz-pipeline-module"
propertiesFile="$workingDirectory/properties.json" 
jazzConfigFile="JazzConfigDirectory/jazz-config.json"

[[ -z "$serviceId" ]] && echo "serviceId is not come as part of the request." && exit 1
[[ -z "$requestId" ]] && echo "requestId is not come as part of the request." && exit 1
[[ -z "$branch" ]] && echo "branch is not come as part of the request." && exit 1
[[ -z "$keyPrefix" ]] && echo "keyPrefix is not come as part of the request." && exit 1
[[ -z "$commitHash" ]] && echo "commitHash is not come as part of the request." && exit 1

groovy -cp $moduleDirectory/ $moduleDirectory/Login.groovy getAuthToken || exit 1  #getting auth token
groovy -cp $moduleDirectory/ $moduleDirectory/ConfigLoader.groovy getConfigData || exit 1  #getting config data
groovy -cp $moduleDirectory/ $moduleDirectory/ServiceMetadataLoader.groovy getServiceDetails "$serviceId" || exit 1   #getting service details

prop=$(< $propertiesFile)
echo "prop: $prop"

service=$(< $propertiesFile jq '.serviceConfig.service'  | tr -d '"')
domain=$(< $propertiesFile jq '.serviceConfig.domain'  | tr -d '"')
akmid=$(< $propertiesFile jq '.serviceConfig.akmId'  | tr -d '"')
repoUrl=$(< $propertiesFile jq '.serviceConfig.repository'  | tr -d '"')

# replace repoUrl having 'http://' or 'https://' with 'ssh://git@'
if [[ $repoUrl == *"https://"* ]]; then
  repo=${repoUrl/"https://"/"ssh://git@"} 
else 
    if [[ $repoUrl == *"http://"* ]]; then
        repo=${repoUrl/"http://"/"ssh://git@"}
    fi
fi

# use config's akmid, if not found in application
if [[ $akmid == null ]]; then
    akmid=$(< $jazzConfigFile jq '.configData.CODE_QUALITY.FORTIFY_SCAN.AKMID'  | tr -d '"')
fi

# replace '/scm' with ''
repo=${repo/"/scm"/""}
echo "repo: $repo"

echo "input values: \"$service\" \"$domain\" \"$akmid\" \"$branch\" \"$requestId\" \"$repo\" \"$commitHash\""
groovy -cp $moduleDirectory/ $moduleDirectory/FortifyScanModule.groovy initiateScan "$service" "$domain" "$akmid" "$branch" "$requestId" "$repo" "$commitHash"
EXIT_CODE=$?
if [ $EXIT_CODE -gt 0 ]; then
    echo "script initiateScan failed with exit code $EXIT_CODE, continuing..."
    exit 1
fi
