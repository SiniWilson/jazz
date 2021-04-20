#!/bin/bash

workingDirectory="WorkingDirectory"
moduleDirectory="$workingDirectory/jazz-pipeline-module"
propertiesFile="$workingDirectory/properties.json"
jazzConfigFile="JazzConfigDirectory/jazz-config.json"

scanConfig=$(< $jazzConfigFile jq '.configData.CODE_QUALITY.FORTIFY_SCAN')

echo "scanConfig: $scanConfig"

scanStatusUrl=$(< $propertiesFile jq '.scanStatusUrl'  | tr -d '"')
echo "scanStatusUrl: $scanStatusUrl"
retryCount=$(echo "$scanConfig" | jq '.RETRY_COUNT' | tr -d '"')
sleepTime=$(echo "$scanConfig" | jq '.SLEEP_TIME_IN_SECONDS' | tr -d '"')
echo "parse scan data"
for ((i = 0; i < retryCount; i++)) 
do
    groovy -cp $moduleDirectory/ $moduleDirectory/FortifyScanModule.groovy parseScanData "$scanStatusUrl"
    exitCode=$?
    echo "exitCode: $exitCode"
    sonarData=$(< $propertiesFile jq '.fortifySonarData')
    echo "sonarData: $sonarData"
    if [[ $sonarData ]]; then
        break
    fi
    sleep "$sleepTime"
done
if [ $exitCode -gt 0 ]; then
    echo "script parseScanData failed with exit code $exitCode, continuing..."
    exit 1
fi
