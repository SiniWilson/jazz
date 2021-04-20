+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Serverless Framework Support"
author = "Satish Malireddi"
weight = 19

[menu.main]
Name = "Serverless Applications"
parent = "features"

pre = "Jazz supports developers creating applications using open source frameworks like serverless"

+++
<!-- Add a short description in the pre field inside menu

Serverless Framework Support
================================================== -->

## Overview

Open source [serverless framework](https://github.com/serverless/serverless) is a great tool in serverless ecosystem that developers can use to design and deploy their serverless applications to public cloud services like AWS. Developers can pick and choose services from a plethora of serverless offerings from AWS (& others) to design their applications. Also, they can use its rich plugin ecosystem to locally test their serverless applications (if they wish)! If this sounds interesting, checkout some of the examples published [here](https://github.com/serverless/examples).

Jazz now supports serverless framework! Developers can create their application definitions (or simply serverless.yml files!) using Jazz UI and deploy their applications to cloud. Or, they can simply commit them to SCM (Gitlab) along with the code and Jazz will take care of deploying the service to the cloud. Along with deployment, Jazz will take care of all the operational readiness aspects of your application (like CI/CD, logs, metrics, tracking deployments, assets, cost etc.) to make your application production ready from day 1!

This document provides step-by-step instructions on how to leverage Jazz & serverless framework to create and manage your serverless applications.

* [Creating a service](#create-service)
* [The application definition a.k.a serverless.yml](#application-definition)
* [Tracking Assets](#tracking-assets)
* [Deployment Workflow](#deployment-workflow)
* [Supported AWS Resources](#supported-aws-resources)
* [Supported Event Sources](#supported-event-sources)
* [Supported Serverless Plugins](#supported-serverless-plugins)
* [Sample Application Definition](#sample-application-definition)
* [Using AWS Secret Management Service](#secrets-management)
* [Additional Notes](#additional-notes)

## Create Service

Head over to create service screen in Jazz UI. Along with the existing service types, you should see "**custom**" as a new service type. Click "custom" to begin with creating serverless framework based service in Jazz. Provide other details like target cloud provider, service name, namespace etc. You can also provide your application definition (more on this below) during service creation. Click other details and hit "Submit" to create your serverless service!

<img src='/content/jazz-features/media/serverless-framework/custom-service-type.png' width='800px'>

<br/>

## Application definition

You'll need a way to design your application when you use serverless framework. This specification (or the application definition!) is simply the serverless.yml file content that you would typically use with your serverless framework based services. If you have it already, you can provide it during service creation so that Jazz will create a serverless.yml file along with the service template and push it to your code repository. Now that you have the file in your repository, you can then make changes and have them deployed to respective jazz environments based on jazz development workflow. It is important to note that Jazz platform will update this serverless.yml file by applying several best practices like adding tags, use naming conventions for assets etc. You can view the final updated serverless.yml file in Jazz UI under environment view.

__Here is how you can specify the application definition during service creation:__

<img src='/content/jazz-features/media/serverless-framework/create-app-def.png' width='800px'>

__View application definition for a specific environment in the environment view:__

<img src='/content/jazz-features/media/serverless-framework/view-app-def.png' width='800px'>

<br/>

## Tracking Assets

You can track all the assets created using Jazz using Jazz UI. You can filter them using the environment, asset type etc. All the assets will be tagged based on the metadata that Jazz captures during service creation. To track the assets outside Jazz (for example, directly in AWS console), it is important to specify the correct application name during service creation.

__View assets for the serverless application in the environment view with asset type filter:__

<img src='/content/jazz-features/media/serverless-framework/assets.png' width='800px'>

<br/>

## Deployment Workflow

Nothing really changes with how developers interact with the platform to manage their serverless application. As previously explained [here]({{< ref "understand-dev-lifecycle.md" >}}), developers can simply focus on code to deliver business value and leave the infrastructure configuration and management to Jazz.

<img src='/content/jazz-features/media/cicd/deployment-workflow.png' width='800px'>

<br/>

## Supported Resources

For compliance purposes, we have enabled (or rather, started with) a list of resources that you can directly use in your application definition. This is only a soft check and we are open to add more resource types depending upon your architecture. Please reach out to us if you do not see your resource type in this list.

### Supported AWS Resources

* DynamoDB

* API Gateway

* Lambda

* S3

* CloudFront

* Lambda@Edge

* SQS

* Kinesis

* IAM

* Cloud Watch Events

### Supported Event Sources

* Schedule - Cron

* Schedule - Rate

* S3

* SQS

* Kinesis Streams

* DynamoDB Streams

* Http endpoints

### Supported Serverless Plugins

* serverless-webpack

* serverless-appsync-plugin

* serverless-step-functions

* serverless-plugin-warmup

* serverless-plugin-typescript

* serverless-plugin-canary-deployments

* serverless-mocha-plugin

* serverless-kms-secrets

* serverless-iam-roles-per-function

* serverless-plugin-cloudfront-lambda-edge

<br/>

## Sample Application Definition

Here is an example that demonstrates how to setup a RESTful APIs allowing you to create, list, get, update and delete Todos. DynamoDB is used to store the data.

```yml
service: serverless-rest-api-with-dynamodb

frameworkVersion: ">=1.1.0 <2.0.0"

provider:
  name: aws
  runtime: python2.7
  environment:
    DYNAMODB_TABLE: ${self:service}-${opt:stage, self:provider.stage}
  iamRoleStatements:
    - Effect: Allow
      Action:
        - dynamodb:Query
        - dynamodb:Scan
        - dynamodb:GetItem
        - dynamodb:PutItem
        - dynamodb:UpdateItem
        - dynamodb:DeleteItem
      Resource: "arn:aws:dynamodb:${opt:region, self:provider.region}:*:table/${self:provider.environment.DYNAMODB_TABLE}"

functions:
  create:
    handler: todos/create.create
    events:
      - http:
          path: todos
          method: post
          cors: true

  list:
    handler: todos/list.list
    events:
      - http:
          path: todos
          method: get
          cors: true

  get:
    handler: todos/get.get
    events:
      - http:
          path: todos/{id}
          method: get
          cors: true

  update:
    handler: todos/update.update
    events:
      - http:
          path: todos/{id}
          method: put
          cors: true

  delete:
    handler: todos/delete.delete
    events:
      - http:
          path: todos/{id}
          method: delete
          cors: true

resources:
  Resources:
    TodosDynamoDbTable:
      Type: 'AWS::DynamoDB::Table'
      DeletionPolicy: Retain
      Properties:
        AttributeDefinitions:
          -
            AttributeName: id
            AttributeType: S
        KeySchema:
          -
            AttributeName: id
            KeyType: HASH
        ProvisionedThroughput:
          ReadCapacityUnits: 1
          WriteCapacityUnits: 1
        TableName: ${self:provider.environment.DYNAMODB_TABLE}

```

<br/>

<div id="secrets-management"></div>

## Using AWS Secret Management Service

Developers can use [AWS Secrets Management Service](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html) in their serverless applications to store & retrieve secrets. Please follow these steps in order to use AWS Secrets Manager to manage your secrets.

### Update permissions in serverless.yml file

In the application definition (serverless.yml) file in the code, developers need to include **iamRoleStatements** along with **Action** on the secret and **Resource ARN** of the secret in the provider as shown in the image below. This will ensure that the function runs with a role that has permissions to get the secret from the AWS service.

```yml

provider:
name: aws
runtime: nodejs12.x
iamRoleStatements:
    - Effect: "Allow"
    Action:
        - "secretsmanager:GetSecretValue"
    Resource: "arn:aws:secretsmanager:[region]:[account-number]:secret:/userService/userSecret"

```

<img src='/content/jazz-features/media/serverless-framework/serverlessfile.png' width='800px'>

<br/>

### Write code to get the secrets during runtime!

Following are snippets of code that you can use in your Nodejs service to interact with AWS Secret Management Service and get the secrets.

<img src='/content/jazz-features/media/serverless-framework/secrets.png' width='800px'>

<br/>

```nodejs

const secretHandler = require("../../components/secret-handler.js")();

var secret = "";

await getSecrets();

async function getSecrets() {
  if (!secret) {
    secret = await secretHandler.getSecretData();
  } else {
    logger.info(`Doing nothing, secret is already available!`);
  }
}

```

For the complete example, please visit the serverless application example - https://gitlab.com/tmobile/jazz/serverless-examples/aws-nodejs-secrets

**Note** - The example service is for Nodejs runtime. Developers can create serverless applications for other runtimes (like Go, Python and Java) using the runtime specific AWS SDKs.

<br/>

## Additional Notes

* Make sure that your application definition conforms to the supported resources in the whitelist published above. Deployment workflow will fail during validation step if unsupported resources are used in the application definition. Contact us if you need additional resources to be whitelisted.

* Check [this](https://serverless.com/examples/) out for more examples for application definitions

* Some of the parameters in the serverless.yml file will always be overridden by Jazz for compliance and governance purposes. You should see the final version applied to an environment in Jazz UI under each environment.
