
+++
date = "2019-09-23T14:00:00-07:00"
draft = false
title = "What is Serverless?"
author = "Satish Malireddi"
weight = 1

[menu.main]
Name = "What is Serverless?"
parent = "start-here"

pre = "What is serverless and why is it important?"

+++
<!-- Add a short description in the pre field inside menu

What is serverless and why is it important?
================================================== -->

Serverless refers to cloud native architecture that provides abstraction of servers, infrastructure and operating systems from application developers. When you build serverless applications, you do not need to provision or manage any servers; therefore, you can take your mind off of infrastructure concerns and focus on your code and business logic. So, no server management, no patching, no capacity planning and no scaling hiccups!

<br/>

## Why should I use serverless?

Serverless enables you to build modern applications with increased agility and lower total cost of ownership. Building serverless applications means that the developers can focus on their core product instead of worrying about managing infrastructure. This reduced overhead lets developers reclaim time and energy that can be spent on developing great products that are reliable and can scale from day 1.

Few **advantages** of serverless -

* **Zero Server Management**

* **Inbuilt Auto Scaling**

* **Automated High Availability**

* **Decreased Time to Market and Faster Software Release**

* **Pay-per-use (Do not pay for idle resources!)**

<br/>


## Serverless Use Cases

The following are few example use cases for which you can leverage serverless technology today -

* Static Websites

* Single Page Applications (AngularJS, React, Vue.js, etc.)

* Scalable APIs with unpredictable/inconsistent traffic patterns

* Scheduled jobs (Timer based jobs)

* Event driven applications

* Data streaming applications

* IoT based applications

* Virtual Assistants/ChatBots

* Computing @Edge

More use cases are published [here](https://serverless.com/learn/use-cases/) along with some working [code](https://serverless.com/examples/)!

<br/>

## Why should I consider serverless for my applications?

While the advantages of serverless architecture patterns are described in this article, there are plenty of resources available in the community in the form of business cases, white papers, case studies and example use-cases. Here is a list to begin your learning journey - 

- https://www.trek10.com/blog/business-case-for-serverless/

- https://serverless.com/learn/case-studies/

- https://aws.amazon.com/serverless/resources/

- https://github.com/cncf/wg-serverless/blob/master/whitepapers/serverless-overview/cncf_serverless_whitepaper_v1.0.pdf

- https://d0.awsstatic.com/whitepapers/optimizing-enterprise-economics-serverless-architectures.pdf

<br/>

## Is Function-as-a-Service (FaaS) same as serverless?

Many have the misconception of considering functions (AWS Lambda for example) alone as serverless! It is incorrect - functions are only a part of the serverless ecosystem! 

Function-as-a-Service or FaaS is a form of serverless "computing", and only one of the categories under serverless. It deals with functions - pieces of code that a developer uploads to the cloud, that get executed whenever there are actual requests or in other words - event triggers. FaaS does not require the server process to be constantly running. Cloud providers typically fire up resources as the requests come in and terminate them after the request is served. Due to this model, developers only pay for function execution time (and no process idle time), resulting in lower costs and with higher scalability. 

Serverless comprises of other forms - serverless storage services, serverless data stores, serverless messaging services etc.

<br/>

## What are other forms of serverless offerings?

FaaS (services like AWS Lambda, Azure functions, Google functions) is just one category under this new paradigm. Serverless comprises of other forms - serverless storage services (AWS S3, Azure Blob Storage etc.), serverless data stores (AWS DynamoDB, AWS Aurora Serverless, Azure cosmos DB), serverless messaging services (AWS SQS, AWS EventBridge, AWS SNS, Azure Service Bus) etc.

<br/>