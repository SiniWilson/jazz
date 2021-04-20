
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Logs"
author = "Deepu Sundaresan"
weight = 14

[menu.main]
Name = "Logs"
parent = "features"

pre = "Jazz pushes logs from services into Splunk for easy retrieval"

+++
<!-- Add a short description in the pre field inside menu

What is a Jazz Service, Namespaces & Environments
================================================== -->

## Overview - Logs

Jazz pushes logs from your service components into a centralized log engine. All the logs are streamed to Splunk for long term storage, retrieval, analysis & troubleshooting.

<br/>

- [Access logs through Splunk](#splunk)
- [Access logs through Jazz UI](#jazz-ui)

<br/>

<div id="splunk"></div>

### How do I access logs in Splunk?

If you don't have access to Splunk, follow the instructions [here](#splunk-onboarding) to get the required access. Once done, go to [Splunk](https://splunk-ss.t-mobile.com/en-US/app/search/) console, type below (after replacing the placeholders) and hit enter

> index=jazz "message.namespace"=$your-namespace "message.service"=$your-service

> Example: index=jazz "message.namespace"=payments "message.service"=process-payment

<img src='/content/jazz-features/media/logs/splunk.png' width='800px'>

<br/>

<div id="jazz-ui"></div>

### How do I access logs through Jazz UI?

Developer can use Jazz application UI to see the logs from their services by different log levels.

Go to *Services > $YourService > $YourEnvironment > Logs*

<img src='/content/jazz-features/media/logs/jazz-ui.png' width='800px'>

<br/>

<div id="splunk-onboarding"></div>

## Splunk Onboarding

T-Mobile Splunk team enforced policies requiring users to be a part of a group to gain access to logs as part of SOX compliance requirements. Jazz logs are streamed to Splunk using index - "jazz". If you need access to the logs for your service in Splunk, please follow the instructions below -
 
- Check if you can login into [Splunk](https://splunk-ss.t-mobile.com) with your T-Mobile NTID/password.

- If you can’t login, you might not have access to Splunk. Raise a [request](#splunk-request) to add yourself to group - *SplunkView_AllUsers*

- For accessing Jazz logs, you will have to raise a request to add yourself to group  - *SplunkView_Jazz*

- There are two approvals involved with this ServiceNow request

  - Your manager should first approve this request

  - Once approved, request gets forwarded to Jazz group. Owner of this group gets notified & approves the request. You should receive email notifications when the request is approved.

- Now that you have access to Splunk & 'jazz' index in Splunk, go to [Splunk](https://splunk-ss.t-mobile.com/en-US/app/search/) console, type below (after replacing the placeholders) and hit enter
    
    > index=jazz "message.namespace"=$your-namespace "message.service"=$your-service

    > Example: index=jazz "message.namespace"=payments "message.service"=process-payment

<br/>

<div id="splunk-request"> </div>

### How to raise a request in Splunk to add yourself to a group –

- Login into [ServiceNow](https://tmus.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsysparm_article%3DKB0012340) & click 'Request Group Access' link

- Under ‘Group’, start typing a group name. Select the desired ‘Group’ and Click ‘Order Now’. Click ‘Checkout’ to complete the request.

- Once the Group owner approves the request, you will be added to the group.

- Note: Requests to ‘SplunkView_AllUsers’ will be automatically approved but requests to ‘SplunkView_Jazz’ should be approved manually as per the compliance requirements. You should receive an email notification when the request is approved. 

<br/>
