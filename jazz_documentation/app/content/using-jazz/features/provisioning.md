
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Provisioning and Management of Cloud Resources"
author = "Deepu Sundaresan"
weight = 19

[menu.main]
Name = "Managing Cloud Resources"
parent = "features"

pre = "AWS resources will be provisioned, updated or deleted as part of deploying or cleaning up services through Jazz"

+++
<!-- Add a short description in the pre field inside menu

What is a Jazz Service, Namespaces & Environments
================================================== -->

## Provisioning

Jazz provisions & manages serverless cloud components (infrastructure) on your behalf. Based on the service type that you choose, Jazz creates all the underlying cloud components, integrates them & exposes the endpoint for you to use. As a developer, these components are completely abstracted away from you so that you can focus on your code and not think of the infrastructure.

## Resource Management

Jazz takes care of deploying your code to the serverless components, updates the components if required and deletes them after the service/environment is deleted.  You will also have the ability to view the list of components that Jazz creates and manages for each service/environment through Jazz UI.

## Tagging

Jazz tags your resources with appropriate metadata to meet the enterprise's tagging compliance standards. An effective and consistent tagging strategy is important to us for discovery, governance, costing, automation etc.