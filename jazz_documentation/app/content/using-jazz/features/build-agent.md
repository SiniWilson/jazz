
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Bring Your Own Build Agent"
weight = 17

[menu.main]
Name = "Bring Your Own Build Agent"
parent = "features"

pre = "Jazz now brings to you Bring Your Own Build Agent feature, empowering you to use your own build agent"

+++
<!-- Add a short description in the pre field inside menu

================================================== -->

## Overview

By default, when you do a deployment in Jazz, we use default Jazz docker images during different stages of the pipeline. Oftentimes, we have seen developers requesting for a specific software version or the ability to add few additional binaries to the build agent. This feature brings you exactly that! You can now create your own build agent by customizing Jazz default base image as per your requirement. 
This allows you to manage the build agent and publish it in your repository, and Jazz can pick up the same during the pipeline execution.


Following are the detailed steps for using your own build agent and using it with a Jazz pipeline -

- [Understanding Jazz images and stages] (#understanding)
- [Download the jazz image and customize it] (#jazz-image)
- [Create a new custom image with the modifications and publishing it in repository] (#custom-image)
- [Override the variables so that Jazz can pick these up during the build] (#override-variables)
- [Validate that the new image is being used] (#verification)

<div id="understanding"></div>

## Understanding Jazz images and stages

It's important for you to understand the base docker images associated with each stage in a Jazz pipeline before you can create your own build agent out of it. Following is the description of all the base docker images for each stages per pipeline.

| Docker Variables Name | Description |
|:------------------:|:-------:|
| BUILD_IMAGE  | Docker image used to build the project in the pipeline |
| UNIT_TEST_IMAGE | Docker image used to run unit test cases in the pipeline |
| CODE_SCAN_IMAGE | Docker image used to run code quality check in the pipeline |
| DEPLOYMENT_IMAGE | Docker image used to deploy development or staging environment |
| PRODUCTION_DEPLOYMENT_IMAGE | Docker image used to deploy production environment |

**Note** You can find all the respective docker images for each of the stages [here](https://gitlab.com/tmobile/jazz/core/packages)

Below is the detailed description of the docker images being used per pipeline (based on your service type).

<br/>

### Serverless Pipeline (Service type: serverless)

| Docker Image Name | Docker Image Registry |
|:------------------:|:-------:|
| BUILD_IMAGE  | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |
| UNIT_TEST_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |
| CODE_SCAN_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/sonar-cdp:1.0.0 |
| DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-serverless:1.0.0 |
| PRODUCTION_DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |

<br/>

### Website Pipeline (Service type: website)

| Docker Image Name | Docker Image Registry |
|:------------------:|:-------:|
| BUILD_IMAGE  | registry.gitlab.com/tmobile/jazz/core/packages/website-lite:1.0.0 |
| UNIT_TEST_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/website-test:1.0.0 |
| CODE_SCAN_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/sonar-cdp:1.0.0 |
| DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-npm-lite:1.0.0 |
| PRODUCTION_DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/website-lite:1.0.0 |

<br/>

### Function Pipeline (Service type: function)

| Docker Image Name | Docker Image Registry |
|:------------------:|:-------:|
| BUILD_IMAGE  | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |
| UNIT_TEST_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |
| CODE_SCAN_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/sonar-cdp:1.0.0 |
| DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-serverless:1.0.0 |
| PRODUCTION_DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-serverless:1.0.0 |

<br/>

### Api Pipeline (Service type: api)

| Docker Image Name | Docker Image Registry |
|:------------------:|:-------:|
| BUILD_IMAGE  | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |
| UNIT_TEST_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/groovy-test:1.0.0 |
| CODE_SCAN_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/sonar-cdp:1.0.0 |
| DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/sls-api:1.0.0 |
| PRODUCTION_DEPLOYMENT_IMAGE | registry.gitlab.com/tmobile/jazz/core/packages/sls-api:1.0.0 |

<br/>

<div id="jazz-image"></div>

<br/>

## Download the Jazz base image and customize it

Now that you have a clear idea about all the docker images Jazz uses in respective pipelines, you can proceed further with downloading one of these and customizing the same. From the [docker image repository](https://gitlab.com/tmobile/jazz/core/packages) you can click on any docker image folder (you need as per your requirements) that will open the respective **DockerFile** for you. Download the DockerFile.

<img src='/content/jazz-features/media/build-agent/docker-download.png' width='800px'>

Once downloaded, you can add any specific binary image or edit version of existing software in the image.
<br/>
For example, if you want to leverage new features of serverless framework released in the latest version, you may update the serverless framework version from the current - **serverless@1.79.0** to the latest - **serverless@2.28.7**.

<br/>

<div id="custom-image"></div>

<br/>

## Create a new custom image with the modifications and publishing it into the gitlab repository

**Note**: While customizing the Dockerfile, make sure that you do not modify other binaries in the image, as these are the libraries/plugins Jazz uses for running the pipelines. It is recommended to only update the image for the binaries that you are interested in modifying.

Once you are done customizing the image, you can upload your DockerFile to your repository along with the **.tmo.function.docker.extended.gitlab-ci.yml** file as shown below (required for creating the docker image). You can get the contents of the file [here](#https://gitlab.com/tmobile/jazz/core/packages/-/blob/master/templates/.tmo.function.docker.extended.gitlab-ci.yml)

<img src='/content/jazz-features/media/build-agent/required-files.png' width='800px'>

Once uploaded, you have to change the **.gitlab-ci.yml** file to build your new docker image and use it in your designated stage. For building your new custom image, you would need to insert the following code.

```json

include:
  - project: 'tmobile/templates'
    ref: tmo/master
    file: '/gitlab-ci/.tmo.global.common.gitlab-ci.yml'
  - local: '.tmo.function.docker.extended.gitlab-ci.yml'

stages:
    - package

docker_package:
  extends: .docker-build
  stage: package

```

Here you are leveraging the **.docker-build** function to build your custom image. Once done, your .gitlab-ci.yml file would look something like this

<img src='/content/jazz-features/media/build-agent/updated-gitlab-yml-file.png' width='800px'>

Now you can commit these changes, which would trigger the pipeline to build your custom build agent. Once your build is successful, you should be seeing your custom build agent in your project's container registry.

<img src='/content/jazz-features/media/build-agent/container_registry.png' width='800px'>

<br/>

<div id="override-variables"></div>

<br/>

## Override the variables so that Jazz can pick these up during the build

Once you have the new custom image ready to be used in the pipeline, you can update the **.gitlab-ci.yml** of your respective repository with the custom build agent path for your designated stage.

For example, if you have created the custom image with updated serverless version and you want to build your project with your new build agent, you can edit the **BUILD_IMAGE** docker variable with your custom image path as shown below.

<img src='/content/jazz-features/media/build-agent/updated-gitlab-yml-file.png' width='800px'>
<br/>

### Image naming convention

The path to your custom build agent will follow this naming convention

```json
<registry URL>/<namespace>/<project>/<environment>
```

<br/>

If your project is **gitlab.com/tmobile/jazz/shared/sample_service**, then your custom image must be named as **registry.gitlab.com/tmobile/jazz/shared/sample_service/${env}**

**Note**: The **$env** variable will be the name of your branch in which you are commiting your changes. Also it is advisable to append the **latest** tag to your custom image path so that it picks up always the latest build

<br/>

<div id="verification"></div>

<br/>

## Validate that the new image is being used

You can validate if your new custom build agent is being used by looking at the start of respective stage's pipeline logs. For example, in the image below it is shown that it is using the custom build agent **registry.gitlab.com/tmobile/jazz/shared/sample_service/dev:latest** created in above step

<img src='/content/jazz-features/media/build-agent/validated-log.png' width='800px'>
<br/>
