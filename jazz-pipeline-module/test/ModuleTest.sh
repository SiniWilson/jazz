#!/bin/bash
moduleDirectory="WorkingDirectory/jazz-pipeline-module"

newLine=$'\n'
#/JUnit/ selects lines that contain JUnit. sub(/.*JUnit/, "") tells awk to remove all text from the beginning of the line to the last occurrence of JUnit on the line. print tells awk to print those lines.
# showing detailed test reports
ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/QcpModuleTest.groovy clean test )
echo "QcpModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'QcpModuleTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/ClearwaterModuleTest.groovy clean test )
echo "ClearwaterModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'ClearwaterModuleTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/AclModuleTest.groovy clean test )
echo "AclModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'AclModuleTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/EnvModuleTest.groovy clean test )
echo "EnvModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'EnvModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/UtilityModuleTest.groovy clean test )
echo "UtilityModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'UtilityModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/AWSApigatewayModuleTest.groovy clean test )
echo "AWSApigatewayModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'AWSApigatewayModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/AWSAcmModuleTest.groovy clean test )
echo "AWSAcmModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'AWSAcmModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/SonarModuleTest.groovy clean test )
echo "SonarModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'SonarModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/ApigeeModuleTest.groovy clean test )
echo "ApigeeModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'ApigeeModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/ServiceMetaTest.groovy clean test )
echo "ServiceMetadataModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'ServiceMetaStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/AWSLambdaModuleTest.groovy clean test )
echo "AWSLambdaModuletest: $ModuleStatusReport"
summaryReport+=${newLine}'AWSLambdaModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

ModuleStatusReport=${newLine}$(groovy -cp $moduleDirectory/test/spock.jar:$moduleDirectory/ testModules/FortifyScanModuleTest.groovy clean test )
echo "FortifyScanModuleTest: $ModuleStatusReport"
summaryReport+=${newLine}'FortifyScanModuleStatusTest: '$(echo "$ModuleStatusReport" | awk '/JUnit/{sub(/.*JUnit/, ""); print}')

echo "************** SUMMARY TEST REPORT **************"

# showing the summary of test reports
echo "summaryReport: $summaryReport"
