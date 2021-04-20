
+++
date = "2017-11-27T14:00:00-07:00"
draft = true
title = "Common error scenarios & How to deal with them"
author = "Deepak Babu"
weight = 10

[menu.main]
Name = "Common error scenarios & How to deal with them"
parent = "utilities"

pre = "Errors related to IAM roles during deployment. If you see an error during your deployment related to role not having enough permissions to perform few actions, it could be because of the IAM role that got assigned to your service."

+++
<!-- Add a short description in the pre field inside menu

Common error scenarios & How to deal with them
============================================== -->

-   [Errors related to IAM roles during
    deployment](#id1)
-   [503 - Service
    Unavailable](#id2)

<div id='id1'></div>

##### Errors related to IAM roles during deployment

 If you see an error during your deployment related to role not having
 enough permissions to perform few actions, it could be because of the
 IAM role that got assigned to your service. Since you have selected
 specific criteria while creation the service, the default role that we
 assign to the service is not good enough! You need to provide IAM role
 with all the permissions that your service needs and assign that role
 to your service. You can easily do that by overriding the defaults in
 *deployment-env.yml *file in your source code. 

 For example, If the lambda function has events subscription enabled,
 the IAM role needs to have sufficient role privileges for the chosen
 events. The IAM Role is defined in the *deployment-env.yml* in the
 workspace as shown below. The user needs to switch to custom role here
 which has the permissions required by your service. 


<div id='id2'></div>

##### 503 - Service Unavailable

The service responds with this status code when it is currently unable
to handle the request due to a bad code deployment, temporary
overloading or any resource maintenance on the AWS side. The implication
is that this is a temporary condition, which will be alleviated after
some delay. If the problem persists, please contact the service owner. 

The client who is consuming the service should handle the response as it
would for a 500 response. The 503 response format is the below

>{
>
>\"errorType\": \"Service Unavailable\",
>
>\"message\": \"Unknown internal error occurred\"
>
>}

