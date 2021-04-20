
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Definitions"
author = "Deepu Sundaresan"
weight = 4

[menu.main]
Name = "Definitions"
parent = "start-here"
pre = "Terminology used in Jazz"

+++
<!--

What is a Jazz Service, Namespace & Environment
================================================== -->

- [Service](#service)
- [Namespace](#namespace)
- [Account](#account)
- [Region](#region)
- [Environment](#environment)

<br/>
<div id='service'></div>

## Service
A service in Jazz is a serverless application that can be managed and deployed in cloud. There are 3 different types of services currently being supported in Jazz – API, Function and Static Website. Services in Jazz come with pre-loaded code templates based on your service type & runtime which helps you jump start your project with a working and ready-to-deploy version of code.

<div id='namespace'></div>

## Namespace
Namespace enables logical grouping of services so that they can be grouped, deployed and managed together. Developers can use their team, project or organization name as the namespace for their service(s). They are also used across several internal platform components such as optimizing provisioning logic, resources usage, cost and defining security rules, ACL etc.

<div id='account'></div>

## Account
Each environment can be configured to deploy the application (& the underlying serverless assets) to a specific account. For AWS, this is equivalent to AWS account and for Azure, this will be the subscription. Note that this information can be configured at an environment level enabling developers to use multiple accounts for their applications.

<div id='region'></div>

## Region
As of now, Jazz can deploy to one cloud region at a time. This is an environment setting along with the account. For AWS, supported regions are `us-west-2` & `us-east-1`.

<div id='environment'></div>

## Environment

Environment is where your code gets deployed & executed. Jazz takes an opinionated approach to create environments based on code branches in Gitlab (SCM). By default, Jazz will create two environments - 'staging' and 'production' when you create a new service. Code from main branch (typically master branch) of the project source code repository is deployed to these environments. Non-production environments of a service are created automatically from non-master code branches. For each non-master branch, Jazz ensures that a completely new environment is created for you with its own endpoints and resource settings which can be modified, tested, monitored and managed independently without impacting other environment's changes. It is required for developers to provide few details for each of these environments to activate them (or enable deployments). Unless this information is set, environment status will be inactive.

Types of environments that you will see in Jazz -

- ### Production! 
   Code from master branch is deployed into prod environment after manual approval.

- ### Staging
   Code from master branch is deployed into staging environment before getting promoted to production environment.

- ### Non-Production
   Code from a non-master branch is deployed to its corresponding non-production environment. Developers can set names to these non-production environments. eg. QAT, PLAB, DEV01, etc.

<br/>

### Environment properties that can be configured in Jazz -

- #### Account/Region
   You can configure an account/region per each environment. For example, point your production environment to production AWS account while the all the non-production environments to a separate dedicated AWS account.


- #### Preferred Settings for Account/Region    
   Since each environment has to be configured separately with account/region settings, Jazz allows to save a combination of account/region information as **preferred settings**. This makes it easy to use the preferred settings as the default settings for any new environment that pops up instead of choosing from a huge list of accounts/regions that you have access to. Just a time saving control that you might like!
   <br/>
   <img class="border" src='/content/getting-started/media/preferred-settings.png' width='500px'>

- #### Environment Name
   *Prod* and *Stg* are named environments created from master branch that cannot be customized. However, each non-production environment created from other branches can be configured with an *Environment Name* eg. QAT, PLAB, DEV01, etc. This makes configuration management easy for your serverless apps. You can manage environment configurations all at one place using these environment names that you can now set for your environments. See below on how you can use this setting for *serverless* apps.
   <br/>
   <img class="border" src='/content/getting-started/media/environment-name-configurations.png' width='500px'>
