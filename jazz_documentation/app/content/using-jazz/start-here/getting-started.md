+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Getting started with service development using Jazz"
author = "Deepak Babu"
weight = 3

[menu.main]
Name = "Getting started with service development using Jazz"
parent = "start-here"
pre = "Getting started with service development using Jazz"


+++
<!--

Getting Started with Service Development using Jazz
=================================================== -->

- [Supported service types](#supported-services)
- [Deployment targets](#deployment-targets)
- [Creating a service](#create-service)
- [Setting the deployment account](#setting-deployment-account)
- [Development workflow](#development-workflow)
- [Monitoring build & deployment status](#build-status)

</br>

<div id='supported-services'></div>

## Supported Service Types

You can create services of the following types using Jazz.

- RESTful **APIs**

- **Functions**

  - Triggered by scheduled events (cron)

  - Triggered by events from AWS services (S3, DynamoDB, SQS, Kinesis)

- **Static websites** and Single Page Apps (SPA)

- **Serverless**: You can now use [serverless framework](https://github.com/serverless/serverless) to design your applications and use Jazz to deploy them to cloud instantly!


</br>

<div id='deployment-targets'></div>

## Deployment Targets

Today, Jazz can deploy your services to AWS cloud! Jazz can help you build serverless applications using services including AWS API Gateway, AWS Lambda, S3, Cloudfront, Kinesis, DynamoDB, SQS, etc. You can now create APIs & integarte them with T-Mobile's APIGEE platform. Read [below]({{< ref "create-jazz-apigee-api.md" >}}) to know more.

</br>

<div id='create-service'></div>

## Creating a service in Jazz

- Let's get started! Go to [Jazz](https://jazz.corporate.t-mobile.com/) UI.

- Click 'Login' at the top right corner. Jazz is GSM1900 integrated, use GSM 1900 credentials to login into Jazz.

- Use 'Create Service' button & provide the required details. Click [here]({{< ref "creating-a-new-service.md" >}}) to learn more about the information that Jazz needs.

- Click 'Submit' and on successful service creation, you should see your new service on the service dashboard page. You just created your service in Jazz. Hooray!
</br></br>
<img src='/content/getting-started/media/service-dashboard.png' width='700px'>
</br>
- If you click on your service, it should take you to the service overview page and you can see that Jazz is getting things ready for you. Once the status bar is green, your service creation is complete! If you don't see any status bar, service status should say 'creation completed'. All set!

</br>

<div id='adding-deployment-account'></div>

## Adding deployment account

- You need to have access to one or more AWS accounts in order to deploy your service. Based on your profile, the appropriate accounts will show in your list of available acounts.
</br></br>
<img src='/content/getting-started/media/env-no-deployment-account-open.png' width='500px'>

- You can add a deployment account to your profile by following the steps [here]({{< ref "update-environment-settings.md#faqs" >}}). 

</br>

<div id='setting-deployment-account'></div>

## Setting the deployment account

- When you create a service the environments are created without a deployment account (AWS account and region).
</br></br>
<img src='/content/getting-started/media/env-no-deployment-account-open.png' width='500px'>

- You can set the deployment account for your environments by following the steps [here]({{< ref "update-environment-settings.md#update-account-region" >}}). 

</br>

<div id='setting-environment-name'></div>

## Setting the environment name

- When you create a branch the environment is created without an environment name (qa, uat).
</br></br>
<img src='/content/getting-started/media/dev-env-no-deployment-account-open.png' width='500px'>

- You can set the environment name for your environments by following the steps [here]({{< ref "update-environment-settings.md#update-environment-name" >}}). 

</br>

<div id='development-workflow'></div>

## Development workflow
    
- Now that your service is created and your environments configured, you are ready to write some code! Click on `Code` on your service overview page to go to your gitlab repository.

- Clone the repository locally. The repository is expected to have a code structure based on the service type that you've selected. Please click [here]({{< ref "byor#repository-structure" >}}) for the expected repository structure. You can also let Jazz create a repository for you, and in this case, repository structure should already be taken care of!

- Start working on your code to implement your business logic and push the changes to a feature branch. Remember that you should always submit your changes to a feature branch; pushing changes to master branch directly will fail. 

- On pushing your changes to a feature branch, a deployment workflow kicks in automatically (Yes, CI/CD is all hooked up automatically).

- Creating a new branch will create a new environment in Jazz automatically. Go ahead and configure this new environment as discussed above. 

- Any new commits to this new feature branch should now be deployed automaticaly to the new environment (let's call it as our dev environment) that you've just configured. Monitor the status of new deployment through your repository's pipelines or in Jazz UI under Deployments section.

- Once you see expected results for your service on this new dev environment, you will have to submit a PR/MR (Merge/Pull Request) to merge the changes to the master branch to deploy your changes to production. Read [how to create a PR/MR](https://docs.gitlab.com/ee/user/project/merge_requests/creating_merge_requests.html) for more information.

- Approvers (you would have added them during service creation or while creating your PR) can review the code, approve and merge the changes to master which kicks off a staging/production deployment workflow. You can then monitor your deployment status in Jazz UI against staging enviroment and eventually in production environment.

- Production deployment workflow contains a *manual approval* stage which requires the approver to approve the deployment. Typically, the approver would get an email notification containing links to easily approve/reject the deployment. This link is will be active for a specific period of time (less than 24 hours) configured for your service in Jazz UI. If the deployment is not approved, pipeline will get stopped after staging deployment and changes will not be pushed to production environment. You may can re-deploy from your repository's pipelines section in Gitlab.

- An overview of this process is shown below - 
  
  <img src='/content/jazz-features/media/cicd/deployment-workflow.png' width='800px'>

</br>
<div id='build-status'></div>

## Monitoring build & deployment status for the service

- You can find the status of each of the deployments for a specific environment by navigating to the deployments tab within the environment detail page. You can click on prod, stg or one of the dev environments in service overview page to see the environment detail page.
</br></br>
<img src='/content/getting-started/media/deployment-status.png' width='700px'>

- You should find one deployment entry per each git commit. Each time you make a commit to your git branch, Jazz automatically triggers a deployment to the target environment. If you need to re-trigger a deployment (may be after a failed deployment), you can simply click the 'Build Now' button if enabled. 

- Once the build is successful, navigate to the overview tab within the environment detail page to find the endpoint and other asset details that got created as part of the deployment.
