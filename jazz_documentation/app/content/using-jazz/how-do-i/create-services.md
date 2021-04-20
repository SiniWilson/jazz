
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Services"
author = "Abhishek Anand"
weight = 33

[menu.main]
Name = "Create a service"
parent = "how-do-i"
pre = "Get started with this guide to create services in Jazz"

+++

On a successful login, the application redirects to show the list of services for the logged-in user.

<img class="no-border" src='/content/how-do-i/services-1.png' width='500px'> 

The default view shows a list of **ALL** Services with ability to quickly filter between the different types of services (**API**, **FUNCTION**, **WEBSITE**). You can further filter the services based on service name, namespace or status values. A search functionality is also provided to perform free text search on the services.

<img class="no-border" src='/content/how-do-i/services-2.png' width='500px'>


To create a new service in Jazz, click on the **Create Service** button on the home page. A panel will appear with a form to provide the details for the service.Provide the details for your service and click on the Submit button. A confirmation screen will appear upon triggering a successful service creation request.

<img class="no-border" src='/content/how-do-i/services-3.png' width='500px'>

<img class="no-border" src='/content/how-do-i/services-4.png' width='500px'>

## Create Service Guide

Following are the list of fields that are displayed in create service form during service creation.

#### Service Type

- To create a service, select appropriate option for *Choose the type of service you want to create*. What type of service are you creating today?

#### Platform

- *Available for all service types*

- Select the target platform for deployment of the service under "*Choose what platform you would like to use*" option. Currently only AWS is supported.

#### Runtime

- *Available for service types - API, Function*

- Select the runtime option for your code for "*Choose your runtime*" option.

#### Service Name

- *API, Function, Website*  

- Provide the Name of service that you want to create. This is a required field.

#### Namespace

- *Available for all service types*

- Namespace enables logical grouping of services so that they can be grouped, deployed and managed together. Developers can use their team, project or organization name as the namespace for their service(s).

#### Application

- *Available for all service types*

- Select an application related to the service from the list. If your application doesn't appear in the list, select *"I don\'t see my application here"* field and provide the application name in text field.

#### Service Description

- *Available for all service types*

- Provide a short description for the service.

#### AWS Account/Region

- *Available for all service types*

- Select where you want your serverless application to be deployed. You will see all the combinations of supported AWS accounts & regions. This list will grow as we add support to more accounts/regions that Jazz can deploy to.

#### Accessibility

- *Available for service types - Website*

- Select this option if you want your service to be publicly accessible (internet). Use caution when selecting this option.

#### Access Restrictions

- *Available for service types - API, Function*

- Select this option if your service needs access to internal T-Mobile network and resources.

#### Slack Channel Integration

- *Available for all service types*

- Use this section to push the service notifications to an existing or new slack channel. Select the option "*Do you want us to notify you through \#Slack?*", enter the slack channel name and tab out. A wizard will validate if the channel exits and allows to subscribe to existing channel or create a new channel. To create a new channel, click on the *"Create channel"* label and a form will appear. Provide details for name, purpose and channel members and click on create button to create and integrate this service with the channel.

    <img class="no-border" src='/content/how-do-i/services-5.png' width='500px'>

#### Approvers

- *Available for all service types*

- Select user(s) who are required to approve code changes, deployment activities etc.

#### Event Schedule

- *Available for service types - Function*

- This option enables creation of Scheduled functions in Jazz. Functions can be scheduled in following ways:

  - Rate Expression ("Fixed Rate of") -- Select the value and unit for schedule.

  - Cron Expression -- A valid CRON expression is needed to schedule the function. The inbuilt validator checks the expression.  

- You can learn more about CloudWatch Event schedules [here](https://docs.aws.amazon.com/AmazonCloudWatch/latest/events/ScheduledEvents.html).

    <img class="no-border" src='/content/how-do-i/services-6.png' width='500px'>

#### AWS Events

- *Available for service types - Function*

- Select appropriate AWS events that you want to subscribe to - DynamoDB, Kinesis, SQS and S3.

#### CDN configuration

- *Available for service types - Function*

- If this option is selected, Jazz configures a CDN (Content Delivery Network) to service the website content. It is advised to keep this true (default value) so that other features like DNS names, certificates would work seamlessly.