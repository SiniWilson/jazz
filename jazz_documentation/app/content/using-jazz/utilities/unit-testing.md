+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Unit Testing Services"
author = "Deepak Babu"
weight = 9

[menu.main]
Name = "Unit Testing Services"
parent = "utilities"

pre = "Getting Started with Service Development using Jazz Supported Services Request for Service Creation Development workflow Monitoring Build & Deployment status for the service Locating your service endpoints Supported Services You can create "


+++
<!-- Unit Testing Services
===================== -->

-   [Technologies and
    Libraries](#id1)
    -   [Technologies Used:](#id2)
    -   [Sinon Overview:](#id3)
        -   [Spies](#id4)
        -   [Stubs](#id5)
        -   [aws-sdk-mock](#id6)

<div id='id1'></div>

Technologies and Libraries
==========================

<div id='id1'></div>

Technologies Used:
------------------

-   Mocha v3.0.0
-   Chai v3.5.0
-   Sinon v1.17.7 \*
-   aws-lambda-mock-context v3.1.1
-   aws-sdk-mock v1.7.0
-   Istanbul/nyc 

\*later versions of sinon have varied calls and uses for their stub
methods; functionality discussed in this document relate solely to
v1.17.7

<div id='id3'></div>

Sinon Overview:
---------------

Sinon is a mocking framework that provides a few useful wrapper options
for functions; in order from least specific to most specific they are :
spies, stubs, and mocks. Utilization of any combination of these can
achieve most of your desired mocked needs (it's largely based on your
style of testing); however, it is best to stray away from the more
invasive/specific mocking functions as they lead to brittle testing
scripts that can break easily given any minor/harmless change (over
specificity breaks tests). 

For the current test scripts, spies and stubs are the only function
wrappers needed.

<div id='id4'></div>

### Spies

Spies are the simplest wrappers provided by Sinon.js. When an object's
reference and method are passed into a spy, the spy listens for any
event where that method is triggered and signals that the function has
been called. 

Spy definitions are as simple as:

  > var spy = sinon.spy(object, "method");

Spies have a flag boolean: **spy.called** (default false) that will be
assigned *true* when the above method is triggered. When spies are
created in the manner above, they have an additional properties:
**spy.args **and **spy.returnValues** which are arrays of the input
parameters and returned values from each function call respectively. 

More information can be found at : [Sinon Spy
Documentation](http://sinonjs.org/releases/v1.17.7/spies/)

<div id='id5'></div>

### Stubs

With spies, the wrapper listens for the method to be called and records
the input parameters and return values. Spies are unable to directly
change the logic within the method, and if the method contains
asyncronous or time/data sensitive statements, it could pose a problem
within your test. Stubs, on the other hand, do not have this limitation
and can do everything a spy can do as well as simulate a method logic,
inputs, and outputs. 

You can setup a stub in a few ways: 

  > var stub = sinon.stub(object, "method", spyObject);

  > var stub = sinon.stub(object, "method", (params) => {statements});

Given the first setup, you can inject a sinon spy into the stub and have
that spy listen to the specific method. The initial method is replaced
by the stub, and doesn't execute its statements. It is important to
call a stub.restore() after.

Given the second setup, you can define a new function to replace the
targeted method. Stub objects have the same functionality as spies as
well and you can rely on the same properties (ie stub.called). With this
you can define what params to accept and what return values to produce
whenever the function is called in the source code. 

More information can be found at : [Sinon Stub
Documentation](http://sinonjs.org/releases/v1.17.7/stubs/)

<div id='id6'></div>

### aws-sdk-mock

A library that uses sinon to mock the aws sdk. This is useful as aws
services are heavily abstacted within the sdk and it is a very difficult
task to mock them directly.

You can setup aws-sdk-mock in the same way you can setup stubs. In this
case, the object will be the AWS service (and any desired nested
objects). An example:

If the user wants to check if the "*update"* command from *"DocumentClient"* is called, they'll do something like:

  > AWS.mock("DynamoDB.DocumentClient", "update", spy);

*note how DynamoDB had to be specified because DocumentClient is a nested service.

When restoring functionality to these services, it is important to do it
in order of decreasing specificity → first you'll want to do
an AWS.restore("DynamoDB.DocumentClient"); and
then AWS.restore("DynamoDB");

<https://github.com/dwyl/aws-sdk-mock>
