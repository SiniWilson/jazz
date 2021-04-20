
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Understanding service types and templates"
author = "Deepu Sundaresan"
weight = 7

[menu.main]
Name = "Understanding service types and templates"
parent = "start-here"

pre = "Based on the type of service being developed the developer will be provided with one of the following templates as listed below."

+++
<!--
Understanding Service Types and Templates
========================================= 
-->

- [Developing an API (Service type - API)](#id1)
  - [Defining the API specification](#id1)
  - [Deployment configuration file](#id5)
  - [Adding external dependencies](#id6)
  - [Implementing business logic](#id7)
  - [Components](#id8)
  - [Test Driven Development](#id9)
  - [Logging](#id10)
- [Developing a Function (Service type - Function)](#id11)
- [Developing Static Websites (Service type - Static Website)](#id12)

Based on the type of service, developer will be provided with one of the pre populated code templates. Each template will have a marker file at the root of the project (it would come as part of the template) which would help CI/CD system to identify a change & trigger appropriate workflows based on service type.

|**Service Type** |**Supported Runtimes**|
| --------------- |:-------------------:|
|API              | NodeJs, Python, Java, Go |
|Function         | NodeJs, Python, Java, Go |
|Static Website   | NA                  |
|Serverless Application   | NodeJs, Python, Java, Go |

<div id='id1'></div>

<br/>

## Developing an API (Service type - API)

<br/>

### Defining the API specification

Developing an API starts with defining an API specification using swagger. The template provides a working version of the specification to start with, which defines all basic model definitions, mappings required for handling success and failure scenarios, request, response mapping templates, CORS header definitions for allowing cross-site origin calls etc. Swagger spec can be developed in multiple formats like JSON or YAML, currently the templates provides one in JSON format.

A sample version of the specification is available under 'swagger' folder in your code.

Swagger provides an editor to visualize and make changes to swagger files, there is a hosted version available to use at [Swagger Editor](http://editor.cloud-api.corporate.t-mobile.com/#/). Developer can load the swagger specification file from a local folder or import from a uri. There are options to auto generate client code in multiple platforms/runtimes too. Check out [Swagger documentation](http://swagger.io/getting-started/) to learn more about swagger.Go build your API specification using the editor!

<div id='id5'></div>

<br/>

### Deployment configuration file

The deployment configurations are stored in the deployment-env.yml file in the template. This file contains the service id, security settings that you may need to override etc. There will be few configurations specific to the type or runtime for your service. Following are list of configuration fields that might be used in deployment-env.yml.

|Key|Sample value|Description|
|:------:|:------:|:------:|
|**service_id**|d12e6dfe-f3c2-40ba-a62d-7105b32ba530|Identifier for your service. This should not be modified!|
|**iamRoleARN**|arn:aws:iam::1234567890:role/my_app_role | Custom IAM role which should have all the required permissions to execute  your service|
|**securityGroupIds**|sg-abc0123 | Comma separated values for AWS security groups that your service needs when  deployed in a specific VPC|
|**subnetIds**|subnet-abc0123, subnet-abc0456, subnet-abc0789 | Comma separated values for AWS subnets that your service needs when deployed in a specific VPC. Atleast 3 subnets are required to ensure high availability|
| **authorizer_arn**|arn:aws:lambda:region:account-id:function:function-name|ARN for the custom authorizer for your API deployed in AWS API Gateway|

**NOTE**: If you want to use different deployment configurations for development, staging and production environments, please make sure to include specific deployment-env.yml file as per respective environment as shown below

|File|Environment|
|:------:|:------:|
|**deployment-env.yml**|Development|
|**deployment-env.stg.yml**|Staging|
|**deployment-env.prod.yml**|Production|

<img src='/content/getting-started/media/deployment-file.png' width='700px'>

<div id='id6'></div>

<br/>

### Adding external packages/dependencies

Based on the runtime, there are different configuration files to specify the compile/build dependencies, unit test configurations, deployment
artifacts etc.

- package.json - (For nodejs runtime) Check out
<https://docs.npmjs.com/files/package.json> for more info on NPM package management

- pom.xml - (For Java runtime) Check out
<https://maven.apache.org/pom.html> for more info in Maven build configuration

- requirements.txt - (For python runtime) Check out <https://pip.pypa.io/en/stable/user_guide/> for more info on python projects and packaging

- Gopkg.toml - (For go runtime) dep is used as the dependency management tool. Check out https://github.com/golang/dep for more details on dep.

<div id='id7'></div>

<br/>

### Implementing business logic

Handler is the main function or method that would get invoked by AWS when the API is called. This is the place to put in your business logic. Code inside this method will be executed with each request made to the API. Handler is defined in several ways based on the runtime.

- index.js - NodeJs - Put your business logic in handler function.

- index.py - Python - Put your business logic in handler function.

- Handler.java - Java - Put your business logic in execute method

- main.go - Go - Put your business logic in Handler method

Developers are free to add additional code/ code files as appropriate
for implementing their business logic. The templates provides a sample
implementation of the handler function for each runtime with code
explaining how to handle different scenarios like success and error
conditions.

<div id='id8'></div>

<br/>

### **Components**

The templates might have few built-in components which abstract common utilities like response formatting, error handling, simple logger etc. These modules are located in the components folder in the templates. Use them as appropriate. Of course, you are open to customize them or replace them with your own ones.

<div id='id9'></div>

<br/>

### **Test Driven Development (TDD)**

Templates of both API and function service types comes with few sample unit test cases (available in the `test` folder). Developers are free to extend it and add more unit test cases based on their specific business scenarios. NodeJs uses `mocha` with `Chai` as the unit testing framework.  Java uses JUnit and python `pytest`.

All unit test cases will be run when the Gitlab CI/CD pipeline executes and errors are thrown when there are any failures in test cases. The build logs can be accessed from the Jazz UI.

<div id='id10'></div>

<br/>

### Application Logging

The service or the function can contain logs for troubleshooting and further analysis. It can contain errors, informational logs or warnings etc. For AWS, Lambda service writes these logs to AWS Cloudwatch. 

Make sure that you can use the default logger that comes with the template or the use your own logger that prints logs in the format below. Jazz parses these logs and pushes them to Splunk, not sticking to this structure will result in logs not being pushed to Splunk.

Format:  **{LOG_LEVEL} {CLASS/FUNCTION:LINENUMBER} {LOG_MESSAGE}**

Example: **INFO RequestHandler:31 Hello! you have logged this message**

<div id='id11'></div>

<br/>

## **Developing a Function service (Service type - Function)**


Functions have the same deployment process and template structure as an API except that the service doesn't do the deployment of swagger for API integration layer. Template will not be having the `swagger.json` file in it.  All other configurations and process explained above will be applicable for this service type as well.

After successful deployment, Gitlab logs will contain the `ARN` of the AWS Lambda function that gets deployed to AWS. Same will be available in the environment overview for the service.

<div id='id12'></div>

<br/>

## **Developing Static Websites (Service type -- Static website)**

The static website service provides a way to develop static contents in html, deploy it to AWS through Gitlab CI/CD process. The workflow takes care of uploading the files/resources to S3 buckets, configure CloudFront distribution and sets other related configurations. Template will contain the `app` folder where all the static content should get in. This becomes the root of your application. You can now create static apps using Angular, React, Hugo, Mkdocs, Vue and other frameworks and deploy them to AWS using Jazz in minutes!