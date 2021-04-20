
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Understanding development life cycle for your service"
author = "Deepu Sundaresan"
weight = 8

[menu.main]
Name = "Understanding development life cycle for your service"
parent = "start-here"

pre = "This document details the life cycle of code for serverless services from development through various deployment stages and environments. "

+++
<!-- Add a short description in the pre field inside menu

Understanding development life cycle for your service
==================================================== -->

This document details the life cycle of code for serverless services from development through various deployment stages and environments.  

<div id='id1'></div>

## Overview

Here is an overview of user interaction with Jazz during service creation & deployment -

<img src='/content/jazz-features/media/cicd/deployment-workflow.png' width='800px'>

A step-by-step overview below:

- Create your service using the self-service portal. You will receive a Gitlab repository pre-loaded with the appropriate service template.

- Login into Gitlab and clone the repository to your local workspace.

- You are now ready to make changes to your code.

  - `handler` file has the service (application) logic

  - `deployment-env.yml` has the service & deployment related configurations
  
  - `swagger.json` has the API specifications (for service type - `api`)

  - `app` folder has the static content (for service type - `website`)

  - `serverless.yml` file for your application definition (for service type - `custom`)

- Once you are ready with your code changes, commit to a `feature` or a `bugfix` branch. Note that the code changes made to master branch directly will be rejected.

- These commits will trigger build & deployment pipeline job (leveraging CDP's Gitlab) automatically in a development environment that gets created for you on the fly. Jazz creates an isolated development environment per each git branch.

- The Gitlab pipeline job has multiple stages - Pre-build Validation, Sanity tests, Build, Unit Tests, Code Quality, Automated Code Scan & Deployment.

- Predefined validation rules are run against the code to check any anomalies with configurations, API specification and other related deployment artifacts.

- In the Sanity test stage, the committed code will be tested for syntactical errors. If the Sanity tests are passed, the job progresses to the other stages.

- In the Deploy phase, the code is deployed to the contextual environment (changes to non-master branches will be deployed to respective development environments while changes to master will be deployed to Staging & Production environments)

- Working endpoint will be published in Jazz UI against each environment. Once the developer confirms that the service endpoint is working as per the requirements, he/she will raise a merge/pull request in Gitlab against the master branch and add appropriate code reviewers (approvers).

- Approvers can review and merge the PR to the master branch. Merge event to the `master` branch will trigger similar CI/CD pipeline job that deploys the code to staging environment & eventually to production environment after  approval by approvers. Manual approval is required for production deployment. Approvers would be notified through email when there is a pending deployment waiting for him/her to approve.

