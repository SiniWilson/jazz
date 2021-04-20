
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Clearwater Integration"
author = "Deepu Sundaresan"
weight = 19

[menu.main]
Name = "Clearwater"
parent = "features"

pre = "Jazz provides Clearwater integration during API development."

+++
<!-- Add a short description in the pre field inside menu

Clearwater Integration
================================================== -->

## Summary

Jazz provides integration with T-Mobile Clearwater platform during API development. Jazz communicates with Clearwater APIs to evaluate swagger score and publish it within Jazz for later use. Developers can now view list of errors/warnings with their swagger files and have the ability to fix and re-evaluate their swaggers through Jazz UI. You can read [here](http://flows.corporate.t-mobile.com/) to know more about Clearwater project for developing better APIs.

<br/>

## Details

Clearwater integration provides following benefits to the developers -

* ### Swagger evaluation with each commit!

    * As part of fully automated CI/CD pipeline, Jazz evaluates swagger against the clearwater linter tool each time a developer makes a code commit to the repository ([gitlab](https://gitlab.com/tmobile/jazz/shared))

    * Developer will be able to [track](#score-details) the clearwater linter score & view the [score trend](#score-trend) over a period of time directly through Jazz UI

    * Developers can update their swaggers and [re-evaluate](#evaluate-swagger) them directly in Jazz UI!

<br/>

* ### Automatic [sequence diagram](#sequence-dgm) generation from PlantUML files with each commit!

    * Similar to swagger files, when Jazz detects PlantUML files in a specific location inside the code repository, it automatically generates sequence diagrams out of these files, pushes them to cloud and makes them available for developers to access them with a click of a button

    * No additional tooling required to generate sequence diagrams from PlantUML files

<br/>

* ### [Publish](#publish) swagger & sequence diagram to clearwater repository with a click of a button!

    * Developers will now be able to publish clearwater artifacts, swagger files & sequence diagrams, to clearwater repository with a click of a button - everything through Jazz UI!

    * Jazz sends email notifications when these assets get published successfully or when the publish process fails for any reason. For a successful publish scenario, developer should receive an email with a link to the merge/pull request to the clearwater repository

<br/>

* ### Swagger & PlantUML files are available with [API templates](#templates)

    * API template, during service creation, comes with well defined Swagger & PlantUML files

    * This will allow developers to start from well-built templates with all the best practices baked in instead of starting from scratch

<br/>

## Developer Journey

<div id='templates'></div>
- *Service repository comes with swagger & PlantUML files by default! Look for files in swagger/ & flows/ in your repository*

<img src='/content/jazz-features/media/clearwater/template-cw-artifacts.png' width='800px'>
<br/>

<div id='score-details'></div>
- *Clearwater score for the current version of swagger committed to Gitlab with more information related to each error/warning*

<img src='/content/jazz-features/media/clearwater/current_score_details.gif' width='800px'>
<br/>

<div id='score-trend'></div>
- *Clearwater score trend*

<img src='/content/jazz-features/media/clearwater/clearwater_trend.png' width='800px'>
<br/>

<div id='evaluate-swagger'></div>
- *Developer can update/re-evaluate swagger files on the fly!*

<img src='/content/jazz-features/media/clearwater/evaluate.gif' width='800px'>
<br/>

<div id='sequence-dgm'></div>
- *Access & share sequence diagram with others*

<img src='/content/jazz-features/media/clearwater/flow-s3.png' width='800px'>
<br/>

<div id='publish'></div>
- *And, publish swaggers to clearwater repository from Jazz UI*

<img src='/content/jazz-features/media/clearwater/publish-trigger.png' width='800px'>
<br/>

<img src='/content/jazz-features/media/clearwater/publish-in-progress.png' width='800px'>
<br/>

<img src='/content/jazz-features/media/clearwater/publish-complete.png' width='800px'>
<br/>

<img src='/content/jazz-features/media/clearwater/publish-email.png' width='800px'>
