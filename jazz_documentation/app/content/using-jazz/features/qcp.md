
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "QCP Integration"
weight = 19

[menu.main]
Name = "QCP"
parent = "features"

pre = "Jazz sends various deployment specific events to T-Mobile's QCP for recording deployment"

+++

- [Overview](#overview)
- [QCP](#qcp)
- [Deployment Events](#deployment-events)
- [Selecting your Application](#select-application)

<div id="overview"></div>

## Overview

One of the primary goals of QCP, the Quality Compliance Platform, is to record deployments and make them visible across systems. QCP allows us to know what applications are deployed, their versions, the environment(s) it was deployed to and when. Jazz deployment pipeline seamlessly integrates with QCP and reports every deployment that happens within the platform along with the deployment status. All you need to do is make sure that you have selected your application correctly when you created a service. More details are available [here](#applications).

<div id="qcp"></div>

## QCP (Quality Control Platform)

QCP's primary goals are -

- Record applications, and their versions, as they deploy into different environments

- When possible, collect information about the quality of the code being deployed, such as, but not limited to:
  
  - Unit Test Coverage

  - Detected security vulnerabilities
  
  - Test results

- Provide access to this data in a centralized manner so it can be used at an Enterprise-wide level

Read more about QCP [here](http://tm/qcp) or [here](https://tmobileusa.sharepoint.com/teams/TEQ/PEAP/RE/SitePages/Introduction%20to%20QCP.aspx)

Once your jazz deployment is complete, you can view the deployment reports [here](https://qcp-reporting.corporate.t-mobile.com/?orgId=1) for your application (You need your application's AKMID for the search!)

<img src='/content/jazz-features/media/qcp/qcp-dashboard.png' width='800px'>

<div id="deployment-events"></div>

## Deployment Events

Jazz sends two events per deployment to QCP - one that indicates that a deployment just started and another with the final deployment status - success/failure. Along with the status, it sends your application details (AKMID), environment where you are deploying, software version (auto populated based on the build number), code branch (git branch name).

#### Start of the deployment

```json
{
    "type" : "pre",
    "environment" : "prod",
    "status" : "success",
    "applications" : {
        "MyApplication" : {
            "version" : "1.0.2",
            "akmid" : "012321",
            "branch" : "master"
    }
}​
```

#### End of the deployment (success)

```json
{
    "type" : "post",
    "environment" : "prod",
    "status" : "success",
    "applications" : {
        "MyApplication" : {
            "version" : "1.0.2",
            "akmid" : "012321",
            "branch" : "master"
    }
}​
```

#### End of the deployment (failure)

```json
{
    "type" : "post",
    "environment" : "prod",
    "status" : "failure",
    "applications" : {
        "MyApplication" : {
            "version" : "1.0.2",
            "akmid" : "012321",
            "branch" : "master"
    }
}​
```

<div id="select-application"></div>

## Tag your application correctly!

For successful data reporting, it is important for you to tag your Jazz service to your application correctly. You can select your application from the dropdown during service creation. Tagging incorrect application will result in incorrect reporting and you will not be able to track your deployments in QCP. Jazz will be able to locate the AKMID for your application and send it to QCP for successful reporting.

<img src='/content/jazz-features/media/qcp/qcp-select-application.png' width='800px'>
