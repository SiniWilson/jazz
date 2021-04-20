+++
date = "2018-07-27T00:27:06-07:00"
draft = false
title = "Stuff we need when you are creating a new service..."
weight = 6

[menu.main]
Name = "Stuff we need when you are creating a new service"
parent = "start-here"

pre = "The following document provides instructions on how to create a service in Jazz"

+++

#### When you are creating a new service using Jazz, following information is requested -

| Field              | Description   | Possible Values |
|:------------------:|:-------------:|:-------------:|
| Service Type       | Type of the service that you want to create | API, Function,  Website or Serverless |
| Deployment Target  | Where do you need your service to be deployed to? | AWS, Azure or Google |
| Runtime            | Runtime to write code!   | NodeJS, Python, Java, Go |
| Repository Link    | Link to your existing code repository. If selected, your code repository should follow certain folder structure. | T-Mobile Gitlab Repository |
| Service Name       | Give a name for your service |
| Namespace          | Give a namespace (or your group/organization name) to group services together |
| Application        | Select your application from the list. Select 'I don't see my application here' if applicable |
| Access Restrictions| Select this if your code needs access to internal T-Mobile resources like an on-premises database |
| Slack Channel      | If selected, you can specify a public channel (enterprise slack) where you will receive all your service notifications |
| Approvers          | List of service owners who will approve code changes, deployment activities etc. |
| Event Schedule     | Enabled for functions. You can specify the schedule for your function invocation |
| AWS Events         | Enabled for functions. Select appropriate AWS services that can trigger your function | S3, DynamoDB, Kinesis, SQS |

</br>

### Note:

* You might not see all these options or an older version here. Available options depend on type of the service that you select.
