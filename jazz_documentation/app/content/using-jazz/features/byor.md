
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "BYOR - Bring Your Own Repository"
weight = 17

[menu.main]
Name = "Bring Your Own Repository"
parent = "features"

pre = "Jazz now brings to you BYOR feature, empowering you to use your own repository"

+++
<!-- Add a short description in the pre field inside menu

================================================== -->

## Overview

By default, when you create a service in Jazz, we create a repository for you in a managed location in Gitlab and provide the url for you to commit code. Oftentimes, we have seen developers requesting for the ability to make Jazz work with an existing repository living in their own project space in Gitlab. This feature brings you exactly that! You can now create a service in Jazz and **connect** it with your own repository in a location where you maintain code for your other applications/projects. This allows you to manage permissions for your repositories at a single location and have all your code in a location that you manage and control!

Note that, at this point, this feature is only supported for **new** services created in Jazz. We'll extend this feature to older/existing Jazz services very soon.

Following are the detailed steps for integrating your repositories to Jazz -

- Repositories hosted in [Gitlab](#gitlab)
- Repository structure
  - [Serverless Application](#serverless-repo)
  - [Website](#website-repo)
  - [Function](#function-repo)
  - [API](#api-repo)

<div id="gitlab"></div>

## Connect your existing Gitlab project to Jazz

- [Adding Repository Link](#adding-repository-link-gitlab)
- [Service Overview (before connection)](#service-overview-pre-gitlab)
- [Adding Webhook](#adding-webhook-gitlab)
- [Adding .gitlab-ci.yml](#adding-gitlab-marker)
- [Giving Read Access to Jazz](#jazz-gitlab-read-access)
- [Testing Webhook](#testing-webhook-gitlab)
- [Service Overview (after successful connection)](#service-overview-post-gitlab)

<br/>
<div id="adding-repository-link-gitlab"></div>

### Adding Repository Link

During service creation, Select **Yes, I would like to connect my own repository**. Select **Gitlab** and add your repository link. We use this url to pull your code during the build process, so ensure that we can successfully **git clone** the repository. Example for valid Gitlab repository link - https://gitlab.com/your-group-or-subgroups/your-project.git

**Note** - Only repositories hosted under T-Mobile's Gitlab service are currently supported. Reach out to us if you need Jazz to support repositories from other Gitlab instances.

<br/>
<img src='/content/jazz-features/media/byor/addGitlabRepository.png' width='800px'>

<br/>
<div id="service-overview-pre-gitlab"></div>

### Service Overview (before adding the webhook)

Once your service is created, you will get the detailed instructions (described below as well) on how you can connect your repository in service overview page.

<br/>
<img src='/content/jazz-features/media/byor/gitlabServiceOverviewPreIntegration.png' width='800px'>

<br/>
<div id="adding-webhook-gitlab"></div>

### Adding Webhook

Once you are in your Gitlab project, go to the **Settings** page. Click on **Webhooks** in order to get started with adding a webhook.
    
Add **https://gitlab.events.jazz.t-mobile.com/api/webhook** as the webhook in the url section and make sure to check **Push events** in order to enable the webhook.

Check **Enable SSL verification**. Click **Add Webhook** to complete this integration.

<img src='/content/jazz-features/media/byor/gitlabAddWebhook.png' width='800px'> 


<br/>
<div id="adding-gitlab-marker"></div>

### Adding .gitlab-ci.yml file to the repository

All new services are expected to have **.gitlab-ci.yml** in the root of the repository and should contain the following Jazz specific stage. Without this file and stage within it, Jazz CI/CD will not work as expected. You can always add your own stages before/after Jazz specific stage based on your needs.

Following is an example for the Jazz stage that needs to be added to the **.gitlab-ci.yml** file for **website** services - 

```yml
stages:
  - jazz-deployment

# REQUIRED SECTION!
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
    project: tmobile/jazz/core/website-pipeline
    branch: master
    strategy: depend

```
Please find the links to the **.gitlab-ci.yml** files based on your service type - 

| Service Type  | Link to .gitlab-ci.yml   |
|:------------------:|:-------------:|
| Serverless      | [.gitlab-ci.yml](https://gitlab.com/tmobile/jazz/core/sls-app-template-nodejs/-/blob/master/gitlab-ci.yml) |
| Website      | [.gitlab-ci.yml](https://gitlab.com/tmobile/jazz/serverless-examples/static-website/-/blob/master/gitlab-ci.yml) |
| Function      | [.gitlab-ci.yml](https://gitlab.com/tmobile/jazz/core/function-template-nodejs/-/blob/master/gitlab-ci.yml) |
| API      | [.gitlab-ci.yml](https://gitlab.com/tmobile/jazz/core/api-template-nodejs/-/blob/master/gitlab-ci.yml) |

Note: Your old services can continue to work without this gitlab marker file. But, if you want to customize your CI/CD process, you can add this file to your repository and add Jazz specific stage as described above.


<br/>
<div id="jazz-gitlab-read-access"></div>

### Giving Read Access to Jazz

No action required for providing access to your projects, Jazz has read access to all projects in Gitlab (based on how T-Mobile's Gitlab service is setup). Not very common but if your project is marked as private, and if you need to connect it with Jazz, contact us for next steps.


<br/>
<div id="testing-webhook-gitlab"></div>

### Testing Webhook

Once the webhook is added, user should make commits to at least one of the branches (or all of them) & see environments pop-up in Jazz's service overview page. You should also see new deployments in this environment for each of the commits you make. This confirms that you have connected your repository with Jazz successfully.

<br/>
<div id="service-overview-post-gitlab"></div>

### Service Overview (after successful connection)

Once the Gitlab project is successfully connected, Jazz will create new environments (one per branch). You can see the environments in the service overview page. User can click on **View Code** button to directly access their project.

<img src='/content/jazz-features/media/byor/gitlabServiceOverviewPostIntegration.png' width='800px'>


<br/>

<div id="repository"></div>

## Repository Structure

Your repository should have few required files and a specific folder structure at the root level to successfully connect it to Jazz. Following documentation should help with understanding the repository structure for each service type.

<br/>

<div id="serverless-repo"></div>

### Serverless (custom) app

An example repository of service type - `Custom`:

<img src='/content/jazz-features/media/byor/custom-app-repo.png' width='800px'>

<br/>

#### Required files/folders:

-- **serverless.yml** - For custom apps, you are expected to define your application infrastructure using the very popular open source [serverless](https://github.com/serverless/serverless) framework. `serverless.yml` should describe the application and will be a required file for this service type.

-- **.gitlab-ci.yml** - To enable CI/CD, your repository must contain the gitlab marker file. This will also enable you to add your own jobs/stages to your pipeline. However, it is important to keep Jazz specific job/stage definition so that you can leverage Jazz CI/CD from the get-go. Use the [template](https://gitlab.com/tmobile/jazz/core/sls-app-template-nodejs/-/blob/master/gitlab-ci.yml) as-is or simply add the section specified to your gitlab-ci.yml file in your repository. And, remember to rename the file to `.gitlab-ci.yml`!

<br/>

Example for a serverless app repository - https://gitlab.com/tmobile/jazz/serverless-examples/scheduled-cron-nodejs

<br/>

<div id="website-repo"></div>

### Website

An example repository of service type - `Website`:

<img src='/content/jazz-features/media/byor/website-static.png' width='800px'>

<br/>

#### Required files/folders:

 -- **app** - For a website, whether you plan to use popular frameworks like `Angular`, `React`, `Vue.js`, `Hugo`, `Gatsby`, `Mkdocs` or even want to host static html documents, you can drop them all in the required `app` folder at the root level. You can provide your build commands in the `build.website` file described below.
 
 -- **.gitlab-ci.yml** - To enable CI/CD, your repository must contain the gitlab marker file. This will also enable you to add your own jobs/stages to your pipeline. However, it is important to keep Jazz specific job/stage definition so that you can leverage Jazz CI/CD from the get-go. Use the [template](https://gitlab.com/tmobile/jazz/serverless-examples/static-website/-/blob/master/gitlab-ci.yml) as-is or simply add the section specified to your gitlab-ci.yml file in your repository. And, remember to rename the file to `.gitlab-ci.yml`!

<br/>

#### Optional files:

-- **deployment-env.yml** - If you want to customize service settings (like Lambda@EdgeARN that you want to attach to your website), you will need to use this file to overwrite the default settings.

-- **build.website** - If you want to customize the build step (for example, you can include commands to build your Gatsby app), you can add them to `build.website` file to the root of your repository. Following is an example of how you can use `build.website` to customize the build workfow.

<img src='/content/jazz-features/media/byor/custom-build-commands.png' width='800px'>

<br/>

Example for a website repository - https://gitlab.com/tmobile/jazz/serverless-examples/static-website

<br/>

<div id="function-repo"></div>

### Function

An example repository of service type - `Function`:

<img src='/content/jazz-features/media/byor/function.png' width='800px'>

#### Required files/folders:

-- **index.js** - `index.js` is the entry point for your function execution (equivalent entry points for other runtimes, this is for Nodejs). For a service of type Function, this file is required.

-- **.gitlab-ci.yml** - To enable CI/CD, your repository must contain the gitlab marker file. This will also enable you to add your own jobs/stages to your pipeline. However, it is important to keep Jazz specific job/stage definition so that you can leverage Jazz CI/CD from the get-go. Use the [template](https://gitlab.com/tmobile/jazz/serverless-examples/function-nodejs/-/blob/master/gitlab-ci.yml) as-is or simply add the section specified to your gitlab-ci.yml file in your repository. And, remember to rename the file to `.gitlab-ci.yml`!

<br/>

#### Optional files:

-- **deployment-env.yml** - If you want to customize service settings like providerMemory, providerTimeout, etc., you will need to use this file to overwrite the default settings.

-- **test** - Drop your unit test files in this folder and Jazz will execute them as part of the deployment workflow.

<br/>

Example for a function repository - https://gitlab.com/tmobile/jazz/serverless-examples/function-nodejs

<br/>

<div id="api-repo"></div>

### API

An example repository of service type - `API`:

<img src='/content/jazz-features/media/byor/api-gateway.png' width='800px'>

#### Required files/folders:

-- **swagger** - Swagger is required for an API. Create a folder: `swagger` and drop the `swagger.json` that describes your API. This is being used to deploy to API gateway services like AWS API Gateway or Apigee.

-- **index.js** - `index.js` is the entry point for your HTTP events (equivalent entry points for other runtimes, this is for Nodejs). For a service of type API, this file is required.

-- **.gitlab-ci.yml** - To enable CI/CD, your repository must contain the gitlab marker file. This will also enable you to add your own jobs/stages to your pipeline. However, it is important to keep Jazz specific job/stage definition so that you can leverage Jazz CI/CD from the get-go. Use the [template](https://gitlab.com/tmobile/jazz/serverless-examples/api-gateway-lambda-nodejs/-/blob/master/gitlab-ci.yml) as-is or simply add the section specified to your gitlab-ci.yml file in your repository. And, remember to rename the file to `.gitlab-ci.yml`!

<br/>

#### Optional files:

-- **deployment-env.yml** - If you want to customize service settings like providerMemory, providerTimeout, etc., you will need to use this file to overwrite the default settings.

-- **test** - Drop your unit test files in this folder and Jazz will execute them as part of the deployment workflow.

<br/>

**Note** - In `swagger.json`, ensure that the basepath is same as the namespace and api path ios same as the service name that you have selected during service creation.

Example for an API service repository - https://gitlab.com/tmobile/jazz/serverless-examples/api-gateway-lambda-nodejs
