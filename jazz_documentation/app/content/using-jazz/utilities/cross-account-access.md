
+++
date = "2017-11-27T14:00:00-07:00"
draft = true
title = "Cross-Account Access"
author = "Deepak Babu"
weight = 12

[menu.main]
Name = "Cross-Account Access"
parent = "utilities"

pre = "The Lambda function backing the API will have access to almost everything in the Devops account that it is running in. If the function needs to access AWS resources located in another account, it will have to get STS credentials and use the those credentials to create the API client in your language."

+++
<!-- Add a short description in the pre field inside menu

Cross-Account Access
==================== -->

The Lambda function backing the API will have access to almost
everything in the Devops account that it is running in. If the function
needs to access AWS resources located in another account, it will have
to get STS credentials and use the those credentials to create the API
client in your language.

### Setup IAM Roles and Policies

1.  Open up the IAM Console in the Devops Account.

2.  Edit the lambda\_basic\_execution Role, this is the default role
    used by Lambda functions

3.  Edit the sts\_assumerole policy that is attached to the role.

4.  Add the following:

    1. 

        > #### **New Statement**

        > -----

        >   {

        >        \"Effect\": \"Allow\",

        >       \"Action\": \"sts:AssumeRole\",

        >       \"Resource\": \"arn:aws:iam::\<Account Number to Access\>:role/lambda\_sts\"

        >   }

    2.  This will allow the Lambda function to assume another role in the
        account specified.



5.  Open up the IAM Console for the account you want to access

6.  Create a role with the name *lambda\_sts*

    1.  Add this as a trusted entity: 

        >**Trusted Entities**

        >------------------------------------------------------------------------------

        >The identity provider(s) [lambda.amazonaws.com](http://lambda.amazonaws.com)
        >arn:aws:iam::302890901340:role/lambda\_basic\_execution

    2.  Attach the following policies:

        1.  AmazonEC2FullAccess

        2.  AmazonS3FullAccess

        3.  CloudWatchFullAccess

        4.  AmazonDynamoDBFullAccess

        5.  AWSCloudTrailFullAccess

### Call the getSTScreds Lambda function

See [Microservice -
createSTSFunction](file:///C:\wiki\spaces\CLP\pages\111903066\Microservice+-+createSTSFunction) for
complete documentation and background

1.  Create an Lambda client through the AWS SDK in your language.

    1.  Must connect to the \'us-west-2\' region to access the
        getSTScreds Lambda function

2.  Invoke the function
