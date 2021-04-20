
## Template - Serverless Application


### Summary
A sample serverless application with two functions ready to be deployed to AWS! Jazz uses an open source project called `serverless framework` that developers can leverage to define and manage their serverless applications. Read more about it [here](https://www.serverless.com/framework/docs/providers/aws/guide/intro/). This framework vastly simplifies how you can manage your AWS serverless infrastructure.


### What is there is this template?

With this template, you are given a simple `serverless.yml` that has two functions and is ready to be deployed to AWS without writing a single line of code. Get started with this and make changes to the `serverless.yml` and the code to develop your application.

### Configuration for different environments 

Read [this](https://www.serverless.com/framework/docs/providers/aws/guide/variables/) on how you can manage environment specific configurations. Jazz will assign environment name that you set using Jazz UI to variable `$stage` which you can use to configure environment specific key/value pairs. An example is provided below, different ways of doing this is described in the link above.

```yml

provider:
  name: aws
  runtime: nodejs10.x
  
custom:
  env: ${opt:stage, 'local'} # Jazz plugs in a value to the CLI option - `$stage` during the deployment, this will be same as the environment name that you set using Jazz UI!
  apiToken:
    local: 'foobar'
    dev01: 'dev-token'
    stg: 'staging-token'
    prod: 'prod-token'
  scheduleEnabled:
    dev01: false # disable the schedule in a particular development environment (name = dev01)
    qa: true # enable the schedule in QA environment
    stg: false # disable the schedule in Staging environment
    prod: true # enable the schedule in Production environment

functions:
  hello:
    handler: functions/index.handler
    environment:
        API_TOKEN: ${self:custom.apiToken.${self:custom.env}} # Environment specific API token gets injected into the function as an environment variable
    events:
      - schedule:
          rate: cron(0 16 * * ? *) # Run this function everyday at a specific point of time
          enabled: ${self:custom.scheduleEnabled.${self:custom.env}} # Turn the schedule on/off based on the current environment
```

### Complete serverless reference
A complete reference on the list of properties that are supported by serverless framework for AWS is provided [here](https://www.serverless.com/framework/docs/providers/aws/guide/serverless.yml/). Also, check [this](https://www.serverless.com/examples/) out for more serverless examples.

### Next steps
For next steps, you can follow step-by-step instructions provided [here](https://docs.jazz.corporate.t-mobile.com/using-jazz/features/serverless-applications/)!
