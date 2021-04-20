
+++
date = "2020-07-15T09:00:00-07:00"
draft = false
title = "New Application in Jazz"
author = "Saurav Dutta"
weight = 10

[menu.main]
Name = "New Application in Jazz"
parent = "start-here"
pre = "Steps to follow when you want a new application in Jazz"
+++

New to Jazz? Don't have your Application on-boarded onto Cloud yet? You can follow these steps to create your first (or next) service in Jazz while you wait to get your Application on-boarded to Cloud. Your new service will be available for 2 weeks during which you can work with Cloud Intake team to get Application on-boarded. Remember to come back here after your onboarding so tag your service with your new Application name so that the new service doesn't get cleaned up!

- [Using Jazz UI](#using-jazz)
- [Using the 2 week trial](#trial)
- [Cloud Intake](#cloud-intake)
- [Updating Application Name](#update-app)
- [Cleanup](#cleanup)

<br/>
<div id='using-jazz'></div>

# Using Jazz UI

Follow the instructions [here]({{< ref "getting-started.md" >}}) to create a new service in Jazz. When you do this, you will be asked to select your cloud application from a pre-populated list. It's possible that you don't see your application here yet (since you haven't on-boarded!). You should see an empty list (or your previous cloud applications) as shown below.

<br/>
<img src='/content/utilities/using-jazz.png' width='700px'>

<br/>
<div id='trial'></div>

# Using the two week trial

Since you don't see your application yet, go ahead and follow these instructions in order to get your service created and continue to use Jazz for now.

  - Click on the `I don't see my application` here checkbox as shown below
    <br/>
    <img src='/content/utilities/trial-1.png' width='700px'>
    
  - On clicking the checkbox, you will see a popup indicating that your service will be created under a temporary **test** Application and will be available for a period of **2 weeks**.
    <br/>
    <img src='/content/utilities/trial-2.png' width='700px'>
    
  - On clicking OK, you can go ahead and create your service, write your code and deploy it on Jazz. Instructions on how to use Jazz to develop and deploy to AWS are available [here]({{< ref "getting-started.md" >}}).

<br/>
<div id='cloud-intake'></div>

# Cloud Intake

You are all set for now. However, if you want your serverless application that you have just created to live for more than 2 weeks, you will need to complete the cloud intake process (hey, someone should pay for hosting your app in cloud! :))!

Follow the [cloud intake](https://ccoe.docs.t-mobile.com/getting_started/intake_and_onboarding/overview/) process so that you can move your service from `test` application to your actual application before the 2 week trial ends.

<br/>
<div id='update-app'></div>

# Updating Application Name

Once the cloud intake process is complete and have your new Application recorded in the intake system, go to your service in Jazz and follow these steps to update the Application name for your service.

**Important** - In order for you (or your team members) to see the new application in Jazz, you should be a part of the AWS Active Directory (AD) groups that get created as part of the intake process. Jazz cannot identify the applications that you are a part of if this information is missing in your user profile (How this works? - Adding yourself to those groups will update your AD user profile which is used by Jazz to know about you). Also, clear your browser cache or reload the Jazz application if the AD groups are updated just now.

  - In the Service Overview, click on the general tab to update your Application - see below:
    <br/>
    <img src='/content/utilities/update-1.png' width='700px'>

  - Click on the pencil icon to go to the edit mode. Remove the test Application from the App Name field and find your Application and select it from the dropdown as shown below:
    <br/>
    <img src='/content/utilities/update-2.png' width='700px'>

<br/>
<div id='cleanup'></div>

# What happens if I do not update the Application?

If you fail to update your Application name for your service within the trial (2 weeks), your service created under the test Application will be deleted. You will receive a reminder from Jazz before the service is permanently deleted.

<br/>
