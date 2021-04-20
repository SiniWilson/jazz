
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Security"
author = "Deepu Sundaresan"
weight = 16

[menu.main]
Name = "Security"
parent = "features"

pre = "Jazz allows deployment of services to a specific VPC for security and enforcing more restrictions"

+++
<!-- Add a short description in the pre field inside menu

Security, VPC Access, public/private access.
================================================== -->

- [VPC support](#vpc-support)
- [Public/Private access](#public-private-access)
- [IAM roles](#iam-roles)

</br>

<div id='vpc-support'></div>

## VPC support & connectivity to on-premise environments

Jazz allows deployment of their services to T-Mobile VPCs (Virtual Private Cloud) for enhanced security and ability to connect to services hosted in T-Mobile On-Premises environments. Developers can select this feature during service creation or by updating service metadata through Jazz UI.

- *Enabling your service to access other services hosted in internal T-Mobile network*

    <img src='/content/jazz-features/media/security/vpc-access.png' width='800px'>

<br/>

<div id='public-private-access'></div>

## Public/Internal-only access to your service (endpoints)

Jazz provides the ability to apply IP based access control to their service (APIs & Websites). This security feature allows creation of public and private endpoints (for example, APIs that can be made available only within the enterprise network) for your services. Please talk to Jazz team to enable this setting on your APIs. If you are developing websites, you can do this yourself using Jazz UI.

**Caution:** Enabling this setting on your website will make it publicly accessible, i.e., anyone can access the content on your website from internet. It is possible that you may expose sensitive data (as static content) on your website.
Think twice before you enable this setting!

- *Making your website private or internal-only*

    <img src='/content/jazz-features/media/security/save-as-internal-endpoint.png' width='800px'>
<br/>

- *Making your website publicly accessible (internet)*

    <img src='/content/jazz-features/media/security/save-as-public-endpoint.png' width='800px'>
<br/>

- *Warning for website endpoints that are publicly accessible*

    <img src='/content/jazz-features/media/security/public-endpoint-warning.png' width='800px'>

<br/>

<div id='iam-roles'></div>

## IAM roles for your services

Jazz provisions an IAM role with absolute minimum permissions required for your service (following principle of least privilege). When complex serverless apps are built using Jazz, for example - function that needs permissions to more than one type of AWS resources, Jazz will create the role with required permissions and attach the role to your service/function. This will make developer's life more easier as there is one less thing to worry about!

### Custom IAM role

Above will only work if Jazz manages provisioning of all the resources for your service. If your service requires permissions to resources that are created outside Jazz, it is your responsibility to get a role created in the same account as your Jazz service is provisioned (ex: tmonpe) & add the role to deployment-env.yml file in your code. You can get a new role provisioned for your service by creating a ticket with [cloud support](http://tm/csr) team.

Ensure that this role has the following permissions (in addition to access to your resources that are created outside Jazz) -

- If your service needs access to services in T-Mobile internal network, [AWSLambdaVPCAccessExecutionRole](https://console.aws.amazon.com/iam/home#policies/arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole) is required to be added to your role
    
- If your service doesn't need access to services in T-Mobile internal network,  [AWSLambdaBasicExecutionRole](https://console.aws.amazon.com/iam/home#policies/arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole) is required to be added to your role

<br/>
