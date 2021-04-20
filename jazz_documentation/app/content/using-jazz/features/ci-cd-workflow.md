
+++
date = "2020-10-21T14:00:00-07:00"
draft = false
title = "CI/CD Workflow"
author = ""
weight = 11

[menu.main]
Name = "CI/CD Workflow"
parent = "features"

pre = "In Jazz, most of the heavy lifting is happening through Gitlab pipeline jobs running in the background"

+++
<!-- Add a short description in the pre field inside menu

What is a Jazz Service, Namespaces & Environments
================================================== -->

In Jazz, most of the heavy lifting happens through Gitlab pipeline jobs running in the background. We currently leverage CDP Gitlab infrastructure to deploy Jazz pipelines. This document explains how to access the pipeline, enable them for your Jazz services, etc.

- [Overview](#overview)
- [Enable Gitlab Pipelines for your service](#enable-cicd)
- [Accessing Gitlab Pipelines](#view-pipeline-gitlab)
- [Triggering Gitlab Pipeline from Gitlab UI directly](#trigger-pipeline-gitlab)
- [Customize Build Step](#customize-build-step)
  - [Usage](#usage)
  - [Dynamic Injection of Environment Name](#dynamic-injection-of-environment-name)
  - [Examples](#examples)
- [FAQs](#faqs)


<div id="overview"></div>

All new services created using Jazz will contain deployment-ready **.gitlab-ci.yml** in the code repositories. If you have connected your own repository (instructions [here]({{< ref "byor.md" >}})) to the service, remember to add the **.gitlab-ci.yml** to enable CI/CD. Older services without the marker file would still continue to work as before.

Each pipeline has multiple stages configured - Pre-build Validation, Build, Test, Static Code Analysis, Deployment to AWS etc. Each stage should be successful in order for it to move to the next stage in the pipeline. Any failures in any of the stages would fail the workflow and developer gets notified through Jazz UI/Slack/Email.

Jazz UI provides a link to each of these Gitlab pipelines under Deployments view under each environment (as shown below).

<img class="border" src='/content/jazz-features/media/cicd/deployments.png' width='700px'>

<br>

For services that have pipeline definition (**.gitlab-ci.yml**) in the user repository, you can view the pipeline status within your Gitlab repository under CI/CD >> Pipelines (as shown below).

<img class="border" src='/content/jazz-features/media/cicd/user-repo-cicd.png' width='700px'>

<br>

### Jazz Gitlab pipeline for a Non-Production Environment

<img class="border" src='/content/jazz-features/media/cicd/jazz-cicd-non-prod.png' width='700px'>

<br>

### Jazz Gitlab pipeline for a Staging/Production Environment

<img class="border" src='/content/jazz-features/media/cicd/jazz-cicd-prod.png' width='700px'>

<br>


<div id="enable-cicd"></div>

## Enable Gitlab Pipeline jobs for your service/repository

For any new services created, Jazz adds **.gitlab-ci.yml** file to the repository which enables developers to customize the behavior of the pipeline by including new stages/jobs. For older services  created using Jazz, you can get the same feature by simply adding **.gitlab-ci.yml** to your repository and start making commits!  It is important to keep Jazz specific job/stage definition intact so that you can leverage Jazz CI/CD from the get-go. Use the [template](https://gitlab.com/tmobile/jazz/core/sls-app-template-nodejs/-/blob/master/gitlab-ci.yml) as-is or simply add the section specified to your gitlab-ci.yml file in your repository. And, remember to rename the file to `.gitlab-ci.yml`!


<br>

<div id="view-pipeline-gitlab"></div>

## How to view Jazz Pipelines from User Pipelines?

Developers can view the  pipeline from their repository under CI/CD >> Pipeline section. Users can view their stages along with Jazz default stages. Following is a sample pipeline view:

<img class="border" src='/content/jazz-features/media/cicd/pipeline-stages.png' width='700px'>

<br>

<div id="trigger-pipeline-gitlab"></div>

## How to trigger the pipeline since it can't be done from Jazz UI?

Once you add a **.gitlab-ci.yml** to your repository, pipelines get triggered automatically when you make a commit. Jazz will not have the ability to trigger a build (since it might have permissions to do so if the repository is outside Jazz managed Gitlab projects). To trigger a new build manually(instead of pushing a new commit), you can trigger the pipeline by clicking the **Run Pipeline** under Gitlab pipeline view in the Gitlab UI of the user's repository. This might not be necessary since we expect the developers to trigger the pipelines mostly through the git commits.

<img class="border" src='/content/jazz-features/media/cicd/run-pipeline.png' width='700px'>


Click the **Run Pipeline** button after providing the build parameters below. Not sending the parameters (as shown below, you can send them as-is!) can make Jazz pipelines fail since it needs these build parameters.


| Build Parameter Key| Value|
|:------------------:|:-------:|
| REPO_URL | ${CI_PROJECT_URL}.git |
| REPO_BRANCH  | $CI_COMMIT_REF_NAME |
| COMMIT_SHA | $CI_COMMIT_SHA |
| REPO_NAME |  $CI_PROJECT_NAME |
| USER_CI_PIPELINE_ID |  $CI_PIPELINE_ID |
| USER_CI_PIPELINE_URL |  $CI_PIPELINE_URL |

An example below - 

<img class="border" src='/content/jazz-features/media/cicd/run-values.png' width='700px'>

<br>

<div id="customize-build-step"></div>

## Customizing build step in the workflow

Jazz allows developers to customize or override the default build step in the CI/CD workflow. This provides some flexibility with respect to how the application gets built. For example, application might need dependencies which are available only in an internal T-Mobile package manager while the default build step attempts to run standard build commands to fetch dependencies from internet.

This feature is available only for two service types - `website` & `serverless`.

<br/>
<div id="usage"></div>

### Usage

Jazz relies on additional files in your repository -  **build.website** (for service type: website) or **build.slsapp** (for service type: serverless) at the root of your project to execute your custom build commands. This file should contain all the commands that you need Jazz to run during the build step. It is important to note that the default build commands from Jazz will **not** be executed which means that it is now your responsibility to implement the build step of the workflow.

During the deployment, Jazz checks if these files are present and if it finds any, skips its default build steps and executes your custom script.

<br/>
<div id="how-to-modify"></div>

### How to modify

- For serverless apps, the **build.slsapp** file comes pre-bundled with the Jazz template during service creation. If you have connected your own repository to your service, simply add this file at the root of the project. During the development of your service, you can put your custom scripts here to modify your build.

- For websites, the **build.website** file comes pre-bundled with the Jazz template during service creation. If you have connected your own repository to your service, simply add this file at the root of the project. During the development of your service, you can put your custom scripts here to modify your build. Ensure that the final build artifacts (static assets) are available in `app` folder in the root of the project which eventually gets deployed to AWS. Following commands will work for a simple angular website with custom build commands -

    <img class="border" src='/content/jazz-features/media/cicd/build-website.png' width='700px'>

- You are free to add additional commands in this file. For example, developers have used this to perform linting on their code as part of their build step. See below for a simple example -

    ```bash
      npm run eslint
      npm install $package
    ```

<br/>

<div id="dynamic-injection-of-environment-name">
</div>

### Dynamic Injection of Environment Name

<br/>

Do you know that you can also customize the **build.website** or **build.slsapp** file to get your app built for a specific environment?

Instead of changing the environment name in this helper file to customize your build, you can use **${env}** for environment name and Jazz injects this value during build time. This will help your application pick up environment specific configurations during build time.

<br/>
For example, **build.website** would look like something like this when building an angular application -

```bash
    ng build --configuration=${env} --output-path=app
```

Jazz will dynamically inject the environment name to your **build.website** file and get it built for that specific branch/environment. For example, if you have made commits to a branch with environment name: **qa**, during the build time, your command would look like this -

```bash
    ng build --configuration=qa --output-path=app
```

<br/>

<div id="examples"></div>

### Examples

We already have an example application set up for you to explore. You can find it [here](https://gitlab.com/tmobile/jazz/serverless-examples/angular-website). Feel free to play around with **build.website** file. Happy Coding!

<br/>

<div id="faqs"></div>

## Frequently Asked Questions

<br/>

### Why are deployments are not showing up in Jazz UI even after I add .gitlab-ci.yml file?

While customizing the **.gitlab-ci.yml**, make sure that your pipeline has the following stage/job that triggers the Jazz Pipeline. This has to be included within the **.gitlab-ci.yml** so that Jazz pipeline gets triggered before/after your custom stages (if any). Path to the project varies by the service type that you have chosen. Make sure that the correct path is provided.

| Service Type              | Path to Jazz Pipeline Project|
|:------------------:|:----------------------:|
| serverless | tmobile/jazz/core/serverless-pipeline |
| website  | tmobile/jazz/core/website-pipeline|
| function | tmobile/jazz/core/function-pipeline |
| api |  tmobile/jazz/core/api-pipeline |

<br>

Here is an example for a service of type **serverless** -

```yml
  stages:
    - jazz-deployments

  jazz-deployment-pipeline:
    stage: jazz-deployment 
    variables:
      REPO_URL: ${CI_PROJECT_URL}.git
      REPO_BRANCH: $CI_COMMIT_REF_NAME
      COMMIT_SHA: $CI_COMMIT_SHA
      REPO_NAME: $CI_PROJECT_NAME
      USER_CI_PIPELINE_ID: $CI_PIPELINE_ID
      USER_CI_PIPELINE_URL: $CI_PIPELINE_URL
      
    trigger:
      project: tmobile/jazz/core/serverless-pipeline # varies by pipeline
      branch: master
      strategy: depend

```

<br/>

### Why does it take a lot of time to my new deployment entry to show up in Jazz UI?

New deployments get registered with Jazz only after Jazz pipeline gets triggered. If your pipeline has several stages/jobs before triggering the Jazz Pipeline, there will some delay before this new deployment show up in Jazz UI as your new stages/jobs need to be done before the Jazz Pipeline is triggered.

<br/>
