
+++
date = "2021-22-03T04:20:00-07:00"
draft = false
title = "Template Library"
author = ""
weight = 20

[menu.main]
Name = "Template Library"
parent = "features"

pre = "Jazz now has a template library containing commonly used serverless application boilerplate code."

+++
<!-- Add a short description in the pre field inside menu

What is a Template Library, Browsing, Searching & Creating a new service
========================================================================= -->

With template library, developers can now discover commonly used serverless patterns and start creating their new services from the all-inclusive well crafted templates. You can treat them as Quick Starts. 

- [Overview](#overview)
- [Browse/Search for Templates](#browse-templates)
- [Create a New Service Using a Template](#create-service)
- [Custom Service](#create-custom-service)
- [FAQs](#faqs)

<br>

<div id="overview"></div>

To create new services in Jazz, users will navigate to the template library. The template library contains a collection of commmonly used service use cases & boilerplate code with all the utilities. These are available in different categories for quick discovery and ease of use. In case the existing templates do not fulfill your requirements, you can create a custom service from scratch.

A screenshot of the Template Library page is shown below.

<img class="border" src='/content/jazz-features/media/templates/template-library-overview.png' width='700px'>

<br>

Clicking on the "View Detail" button on a template card will take you to the template details page where you can view details about the service template, it's application architecture, AWS services used, and also comments by other developers. You can also click on the "View Gitlab Code" button to see the git repository of the template.

An example of a template detail page is shown below.

<img class="border" src='/content/jazz-features/media/templates/template-library-detail.png' width='700px'>

<br>


<div id="browse-templates"></div>

## Find The Right Template For You

The Template Library page has various filters that you can use to show templates relevent to your use case.

You can:

* Use the search bar to filter using use-case keywords, such as, API, website, Angular, Serverless, etc

* Filter by programming language. Java, Python, Node.js, and Go are currently supported.

* Filter by deployment platform. Currently limited to AWS.



<img class="border" src='/content/jazz-features/media/templates/template-library-search.png' width='700px'>

<br>

<div id="create-service"></div>

## Create a New Service Using a Template

Creating a new service once you've chosen a template that suits your need is pretty straightforward.

Clicking on the "Create Service" button on the template card or from the template detail page will start the three step process to create and deploy your service.
<br>

* The first step shows the deployment platform and the service runtime. This cannot be changed if you are using an existing template. While creating a custom service, you can select the deployment platform and runtime.

&nbsp; <img class="border" src='/content/jazz-features/media/templates/template-library-create-1.png' width='700px'>
<br>

* In the second step, you have to enter the service name, and the namespace. The combination of service name and namespace must be unique. You can also add a description for the service being created in this step.  
Here, you can also connect your existing empty Gitlab code repository. Make sure that your repository structure matches with the expected sample repository (Jazz provides a link to it when you select this option).

&nbsp; <img class="border" src='/content/jazz-features/media/templates/template-library-create-2.png' width='700px'>
<br>

* In the third step, you have to choose a cloud workload, and add any approvers for service deployments.

&nbsp; <img class="border" src='/content/jazz-features/media/templates/template-library-create-3.png' width='700px'>
<br>

Clicking "Launch Application" will trigger the creation of your service on Jazz, and you will be notified after the service is successfully created.

<br>

<div id="create-custom-service"></div>

## Creating a Custom Service

The Template Library also allows you to create a custom service. This provides flexibility with respect to the runtime, and AWS resources that are available to the application from the get-go.

To do so, click on the "Create Custom Service" button on the top right corner of the Template Library page.

You will be taken through the same steps as while creating a service from an existing template, but you will have be able to choose the deployment platform and runtime.

Before deploying your service, remember to configure the serverless.yml file to add any relevent AWS resources and configurations that your service needs. You can refer to some examples [here](https://github.com/serverless/examples).

<br/>

<div id="faqs"></div>

## Frequently Asked Questions

- **I've used api and function services in the past. I don't see those options anymore. What do I do?**

    We have purposefully removed these two options. They were the first set of service types that Jazz supported and use shared components & sometimes shared infrastruture. We want applications to be independent and do not want any shared infrastructure components as we ran into many issues like resource limits, API throttling etc.

    Templates Library should cover the api & function use cases. You can search with api or function keywords and select the appropriate template. This will ensure that you use dedicated AWS infrastructure and making changes to your service doesn't affect others. You can also use serverless framework & its rich plugin eco-system to build more powerful serverless apps than before!

- **I like the templates and idea of sharing work with other developers. How do I publish a new template?**

    Great, we encourage developers to collaborate and share best practices with others. Self-Service is not currently available for publishing new templates but we are happy to help registering your template with the library. Reach out to us at #serverless or ServerlessDev@T-Mobile.com.



<br/>
