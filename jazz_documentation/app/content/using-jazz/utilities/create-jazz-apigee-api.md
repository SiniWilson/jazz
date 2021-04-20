
+++
date = "2018-10-27T09:00:00-07:00"
draft = false
title = "Create a Serverless API in Apigee"
author = "Abhishek Anand"
weight = 41

[menu.main]
Name = "Create serverless API in Apigee in few minutes"
parent = "utilities"
pre = "Guide to create serverless API in Apigee in few minutes"
+++


- [Getting Started](#getting-started)
- [Create Apigee Proxy for API Gateway](#apigee-proxy)
- [Support](#support)

<div id='getting-started'></div>

# Let's Get Started!

- Login into [Jazz](https://jazz.corporate.t-mobile.com/). If you are new to Jazz, click [here]({{< ref "login.md" >}}) for instructions on how to login into Jazz.

- Click `Create Service` button for creating a new service. Select `SERVERLESS` for service type and a specific runtime for your API. Selecting this service type instructs Jazz to create a dedicated API gateway resource for each of your enviroments which then enables you to create a dedicated apigee proxy that calls this API endpoint.

    <img class="border" src='/content/utilities/create-serverless.png' width='700px'>

- Provide service and namespace and other information as appropriate and hit `Submit` to create your API service. Follow our sample serverless examples for creating a serverless API service in Jazz [here](https://gitlab.com/tmobile/jazz/serverless-examples/aws-node-serverless-http-endpoint).

    <img class="border" src='/content/utilities/create-serverless-steps.png' width='700px'>

- After successful creation, click on the service that you just created. In few minutes, you should see

    - a shiny link to a Gitlab repository that contains your serverless API service boilerplate template code - How cool is that!

    - Staging & Production Environments automatically getting created for you
    
        <img class="border" src='/content/utilities/new-service-panel.png' width='700px'>

- You can configure the staging or production environment or create a brand new environment and update the serverless.yml file which will trigger a new deployment. Follow our example [here](https://gitlab.com/tmobile/jazz/serverless-examples/aws-node-serverless-http-endpoint/-/blob/master/serverless.yml) on how to write a serverless yml file to get an API Gateway endpoint.

    <img class="border" src='/content/utilities/configure-environment.png' width='700px'>

- Once the deployment is successful, click on `ASSETS` in your environment view to see the link to a working API Gateway endpoint that's ready to use immediately. You are all set to use this API endpoint! 
    
    <img class="border" src='/content/utilities/apigee-assets.png' width='700px'>

- Get started to write some code and make your API do more awesome things! Please refer to this [guide]({{< ref "understand-dev-lifecycle.md" >}}) & start writing some code!

<br/>
<div id='apigee-proxy'></div>

# Create Apigee Proxy for API Gateway

- Once you have a dedicated api gateway endpoint from Jazz, you are all set to use the Apigee ProxyGen tool.

- ProxyGen tool from [devcenter](https://devcenter.t-mobile.com/) simplifies the process of Apigee proxy creation. For a detailed documentation on how to create an apigee proxy for your API, please follow this documentation [here] (https://devcenter.t-mobile.com/documents/redirect?path=/sites/static/proxygen_wizard_gitlab_documentation)

<div id='support'></div>

# Contact our support team

  - Email: [ApigeeJazz@T-Mobile.com](mailto:ApigeeJazz@T-Mobile.com)

  - Slack: [#apigee](https://t-mo.slack.com/messages/C4SAFRARJ)

  - Support Request Form: [Here](https://forms.office.com/Pages/ResponsePage.aspx?id=C5gPvpndGUu9e7xxoJsCbGM4QIOGPfpKiLnkfhuWVCBUQVVXWlBJTk4wUldaSkVSSE5NQUtCMDBTVi4u)
