
+++
date = "2020-04-09T09:00:00-07:00"
draft = false
title = "Update Settings for your Environment"
author = "Prakash Raghothamachar"
weight = 42

[menu.main]
Name = "Update Settings for your Environment"
parent = "utilities"
pre = "Guide to update Environment Settings"
+++

- [Update Account & Region](#update-account-region)
- [Update Environment Name](#update-environment-name)
- [Update Preferred Settings](#update-preferred-settings)
- [Using Preferred Settings](#using-preferred-settings)
- [FAQs](#faqs)

<br/>

Whenever a service is created, Jazz creates two environments automatically - **prod** & **stg**, the production and the staging environments! These environments are not ready for deployment until we configure the target account and region for these environments. For each new branch in the code, Jazz creates a dedicated new environment. For a new environment, you will need to set environment name (example - DEV01, QAT, PLAB etc.) along with the account and region. This document explains how to set these settings in Jazz UI so that you can deploy to these environments successfully.

You can update these settings at a the service level (by clicking on each environment), configurations panel by clicking the gear icon to the right or at the individual environment level.

<br/>

<div id='update-account-region'></div>

#### How do I update account/region for my environment?

- In the Service Overview or using the Configuration panel, <br/>
  <img class="border" src='/content/utilities/update-account-card.png' width='500px'>

- In the Environment Overview, <br/>
  <img class="border" src='/content/utilities/update-account-env-overview.png' width='500px'>

<br/>

<div id='update-environment-name'></div>

#### How do I set Environment Name for my an environment?

- Generate a new random name <br/>
  <img class="border" src='/content/utilities/env-name-generate.png' width='500px'> <br/>

- Clicking on the generate button generates a 14 character random environment name. *example: fqjnmit4qg-dev* <br/>
  <img class="border" src='/content/utilities/env-name-post-generate.png' width='500px'>

- You can also type the name of your choice (example: DEV, QAT, PLAB etc.) <br/>

- Availability Check <br/>
  <img class="border" src='/content/utilities/env-name-check.png' width='500px'> <br/>

- Clicking on the Check button checks the availability of the environment name for the service <br/>
  - When an Environment Name is available: <br/>
  <img class="border" src='/content/utilities/env-name-check-avl.png' width='500px'>

  - When an Environment Name is used/unavailable: <br/>
  <img class="border" src='/content/utilities/env-name-check-avl-fail.png' width='500px'>

<br/><div id='update-preferred-settings'></div>

#### How do I update Preferred Settings for my environment?

- Click on the gear icon to the right in Service Overview page to open the settings as a side panel <br/>
  <img class="border" src='/content/utilities/sidebar-preferred-setting-edit.png' width='500px'>

<br/><div id='using-preferred-settings'></div>

#### Using Preferred settings to easily select account/region for an environment!

- In the Service Overview, <br/>
  <img class="border" src='/content/utilities/preferred-settings-card.png' width='500px'>

- Using the Configuration panel, <br/>
  <img class="border" src='/content/utilities/preferred-settings-side-panel.png' width='500px'>

- In the Environment Overview, <br/>
  <img class="border" src='/content/utilities/preferred-settings-env-overview.png' width='500px'>

<br/>

## FAQs

- **Why don't I see any AWS accounts (or the AWS account that I actually have access to) when I attempt to configure my environment?**

    When you login into Jazz, your user profile will have information related to accounts and applications that you have access to. If you don't see the account that your team member sees, it is possible that you are not part of AD groups that give you access to these AWS accounts. Talk to your application owner(s) and request them to add you to the respective AD groups. Your app owner can add you to groups with naming convention - **r\_aws\_$accountNumber\_$appId\_$role** through [cloud access portal](https://access.t-mobile.com/browse/groups).

    Also, Jazz is connected to a specific set of T-Mobile AWS accounts. It is likely that Jazz isn't connected to your account yet. Contact #serverless if you would like to connect Jazz with your AWS account so that you can start deploying to it!

- **My deployment always fails with a message - 'The environment name is not set for the branch: foobar, please set them by logging into Jazz UI'. What should I do?**

    This error suggests that your new environment is ready but Jazz doesn't know what to call it as. Go to Jazz UI and set an environment name (ex: DEV01, QAT, UAT etc.) by following instructions in this page and trigger a new deployment. You can make a commit to your branch which should kick off a new deployment without any issues now.

- **My deployment always fails with a message - 'Deployment account information is not set for the environment..'. What should I do?**

    This error suggests that your new environment is ready but Jazz doesn't know which account/region to deploy this environment to. Go to Jazz UI and set an account/region by following instructions in this page and trigger a new deployment. You can make a commit to your branch which should kick off a new deployment without any issues now.

- **While saving deployment account details, I see a message - 'Do you want to trigger deployment along with saving these details?'. What happens if I skip triggering a deployment now?**

    Jazz doesn't deploy to an environment until the account/region/environment name is set. Once you set these details, you can trigger a new deployment from Jazz UI. If you don't want to deploy, you can simply save the details and trigger deployments at a later point of time.

- **I don't have access to configure deployment accounts for my environment(s). What should I do?**

    You will need to be an administrator to the service to configure these settings. Your service administrator can do this for you or you can request your service administrator can add you as another admin to the service by following the instructions [here]({{< ref "access-controls.md" >}}).