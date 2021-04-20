
+++
date = "2019-09-23T14:00:00-07:00"
draft = false
title = "Let's talk about Jazz!"
author = "Satish Malireddi"
weight = 2

[menu.main]
Name = "Why Jazz?"
parent = "start-here"

pre = "What can Jazz do for me?"

+++
<!-- Add a short description in the pre field inside menu

What can Jazz do for me?
================================================== -->

Jazz is an [open source](https://github.com/tmobile/jazz) serverless development platform built by T-Mobile to help developers seamlessly create, deploy, manage, monitor and debug cloud native applications leveraging serverless technology. With Jazz, developers can create applications in minutes without worrying about servers and their management. Go Serverless!

<br/>

## Why should I use Jazz?

Jazz helps make serverless applications production-ready from Day 1! Our goal is to make development & managing applications easier than ever by taking away all the boring pieces out of it - setting up CI/CD, monitoring, log collection, metrics, monitoring, notifications, security, governance, compliance, etc. As a developer, you can focus on your application code and Jazz takes care of everything else. Moving to serverless already saves a lot of time and money by offloading the infrastructure management to cloud providers, Jazz helps you to save more time by setting things up for you to deploy your application and manage your applications easily!

<br/>

## Features

Here is what Jazz does under the hood for you when you create an application using this platform - 

- **CI/CD**: Jazz comes with CI/CD by default. It creates a code repository for each service, assigns appropriate permissions and adds a web hook to trigger build/deployment workflows whenever it sees a commit. We leverage Jenkins open source for the build process and Serverless Framework for deploying these services. You can read more about the steps inside each Jenkins workflow in the detailed documentation.
  
- **Code Repositories**: Each service gets its own private repository in Gitlab. You can control access to this repository through Jazz UI.

- **One-Click Development Environments**: Agility is an important ask from developers. To enable them go faster and avoid delays due to environment availability, Jazz creates a dedicated development environment per each branch in the service repository (available Gitlab). This means a new dedicated environment for every developer as they create a new branch. Jazz deletes this environment whenever the branch gets deleted.

- **Cost Overview**: Track the infrastructure cost for every service that you create in Jazz to avoid cost surprises at the end of the month!

- **Deploy to Multiple Accounts/Regions**: We work with cloud support teams and the customers very closely to understand their needs about connecting Jazz with new AWS accounts that are being created based on customer needs. Jazz has the ability to connect to all the accounts that cloud teams manage.

- **Deploy to Multiple Cloud Providers**: Jazz is built with a plug-n-play architecture so that we can add multiple cloud providers, accounts, regions, external integrations seamlessly. Today, Jazz can deploy to AWS and Azure support is coming very soon. We plan to deploy to Google Cloud and on-premises in the near future.

- **Code Quality**: As part of CI/CD workflow, Jazz runs code scans to find and report vulnerabilities with the code and its dependencies. Reports are generated in Sonar and can be accessed through Jazz UI.

- **Log Collection**: Logs from underlying cloud infrastructure is collected and sent to Elastisearch for short term usage and T-Mobile Splunk for long term storage and analysis.

- **Metrics**: Jazz integrates with cloud provider's default metric solutions like AWS CloudWatch to provide insights into your service/component on demand!

- **Access Control**: Developers can now share access to their services with other developers who are collaborating in a team, application or project setup. As a service admin, you can assign permissions to other users so that they can perform various developer activities like view the service in Jazz UI, manage it, have access to the code, view build & deployment logs, trigger deployments etc. all without leaving Jazz!

- **Custom DNS**: You can now request and manage custom domain names for your service endpoints (website or API endpoints) using Jazz UI. Information related to existing custom domain names, ability to request a new one or update an existing domain name is available for authorized users in DNS section under each environment in Jazz UI.

- **Certificates**: Jazz completely manages certificates on your behalf. If your service is deployed in Jazz and has endpoint(s), https will be enabled by default and SSL certificates will be requested & applied to the appropriate endpoint(s). Not just that, certificate management is all taken care by Jazz and as a developer, you would never need to request, manage or worry about the certificates ever again!

- **Security Controls**: Jazz allows administrators to define & apply security controls from a single place. Jazz admins can choose to enforce the controls on every service that gets created using Jazz allowing them to make them secure by default. Many best practices like applying the principle of least privilege, code (& dependency) scans during CI/CD, preventing default public access are available by default in Jazz.

- **Notifications**: Slack is integrated with Jazz and you will get notified about many service level alerts if enabled. Developers also receive email notifications based on alert type.

- **Clearwater Integration**: Jazz provides integration with T-Mobile Clearwater platform during API development. Jazz communicates with Clearwater APIs to evaluate swagger score and publish it within Jazz for later use. Developers can now view list of errors/warnings with their swagger files and have the ability to fix and re-evaluate their swaggers through Jazz UI.
  
- **QCP Integration**: Jazz deployment pipeline seamlessly integrates with QCP and reports every deployment that happens within the platform along with the deployment status. All you need to do is make sure that you have selected your application correctly when you created a service!

Click [here] for more in depth details on these features.