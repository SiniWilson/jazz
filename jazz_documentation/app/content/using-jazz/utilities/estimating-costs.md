
+++
date = "2019-04-11T09:00:00-07:00"
draft = true
title = "Estimating AWS serverless costs"
author = "Satish Malireddi"
weight = 12

[menu.main]
Name = "How do I estimate cost of my service?"
parent = "utilities"
pre = "Guide to update service runtime for existing services"
+++

To change service **runtime** for an existing service, follow the steps below:

- Login into [Jazz](https://jazz.corporate.t-mobile.com/) and select a service from the list.

- In the overview tab, click on the 'Edit' Button in the top left.  
<img class="no-border" src='/content/utilities/runtime-edit.png' width='500px'>

- Select the target runtime from the list of available runtimes.  
<img class="no-border" src='/content/utilities/runtime-select.png' width='500px'>

- Click on 'Save' button. A confirmation pop-up will appear. Upon confirmation, the service runtime will be updated in the catalog.
<img class="no-border" src='/content/utilities/runtime-save.png' width='500px'>

All future deployments to your service will be using the updated runtime.

*Note: If you need your service to get updated immediately, you'll need to manually trigger a deployment for each environment. Simply, go to the 'Deployments' tab for an environment and click 'Build Now' button to trigger a new deployment!*
