#!/bin/bash

keyPrefix=$1
branch=$2

workingDirectory="WorkingDirectory"
moduleDirectory="$workingDirectory/jazz-pipeline-module"
propertiesFile="$workingDirectory/properties.json"
jazzConfigFile="JazzConfigDirectory/jazz-config.json"

prop=$(< $propertiesFile);
echo "prop: $prop"

service=$(< $propertiesFile jq '.serviceConfig.service'  | tr -d '"')
domain=$(< $propertiesFile jq '.serviceConfig.domain'  | tr -d '"')
scanConfig=$(< $jazzConfigFile jq '.configData.CODE_QUALITY.FORTIFY_SCAN')
sonarHostName=$(< $jazzConfigFile jq '.configData.CODE_QUALITY.SONAR.HOST_NAME'  | tr -d '"')

echo "sonarHostName: $sonarHostName"

sonarData=$(< $propertiesFile jq '.fortifySonarData')
echo "fortifySonar: $sonarData"
high=$(echo "$sonarData" | jq '.high')
medium=$(echo "$sonarData" | jq '.medium')
low=$(echo "$sonarData" | jq '.low')

if [[ $sonarData && $high && $medium && $low ]]; then
    highActiveOpen=$(echo "$high" | jq '.active_open' | tr -d '"')
    mediumActiveOpen=$(echo "$high" | jq '.active_open' | tr -d '"')
    lowActiveOpen=$(echo "$high" | jq '.active_open' | tr -d '"')
    echo "high: $highActiveOpen"
    echo "medium: $mediumActiveOpen"
    echo "low: $lowActiveOpen"
    highMetric=$(echo "$scanConfig" | jq '.METRIC_NAME_HIGH' | tr -d '"')
    mediumMetric=$(echo "$scanConfig" | jq '.METRIC_NAME_MEDIUM' | tr -d '"')
    lowMetric=$(echo "$scanConfig" | jq '.METRIC_NAME_LOW' | tr -d '"')
    groovy -cp $moduleDirectory/ $moduleDirectory/SonarModule.groovy createSonarMeasureForFortifyScan "$sonarHostName" "$highMetric" "$high" "$service" "$domain" "$branch" "$keyPrefix" "$JAZZ_SVC_ACCT_USER" "$JAZZ_SVC_ACCT_PASSWORD"  || exit 1
    groovy -cp $moduleDirectory/ $moduleDirectory/SonarModule.groovy createSonarMeasureForFortifyScan "$sonarHostName" "$mediumMetric" "$medium" "$service" "$domain" "$branch" "$keyPrefix" "$JAZZ_SVC_ACCT_USER" "$JAZZ_SVC_ACCT_PASSWORD"  || exit 1
    groovy -cp $moduleDirectory/ $moduleDirectory/SonarModule.groovy createSonarMeasureForFortifyScan "$sonarHostName" "$lowMetric" "$low" "$service" "$domain" "$branch" "$keyPrefix" "$JAZZ_SVC_ACCT_USER" "$JAZZ_SVC_ACCT_PASSWORD"  || exit 1
fi