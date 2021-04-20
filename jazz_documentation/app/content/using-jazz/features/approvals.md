+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Approvals"
weight = 17

[menu.main]
Name = "Approvals"
parent = "features"

pre = "Jazz now has a dedicated panel for all your Approvals"

+++

# Summary

In Jazz, every Production deployment requires an approval from the service approvers before it can proceed forward. However, all the approval notifications are sent to the respective service approvers/owners via email, which tend to get lost in the array of numerous mails. Developers face hard time finding the updates to their approval request.

Now Jazz provides a dedicated Approval panel, where the service approvers can see the list of all the approvals requested to them and the current status for each one of them respectively. They can now reach out to the developer for more information. Following are the detailed description of every aspect of the Approval feature.

<br/>

## Where to get the list of approvals

<img src='/content/jazz-features/media/approval/Approval-panel.png' width='1000px'>
<br/>

As you can see from the image above, you can simply click on the notification icon on the upper right hand side, which will reveal the list of approval requests waiting on you. This list shows all the approvals **(pending/expired/rejected/approved)** for the past one week. Once you click on **View Details**, it will reveal all the detailed information for that particular approval request.

<br/>

<img src='/content/jazz-features/media/approval/Approval-details.png' width='1000px'>

Once all the information is verified, you can either **deny** or **approve** the deployment. Once an action is taken, it will show the appropriate message.

<br/>

<img src='/content/jazz-features/media/approval/Approval-success.png' width='1000px'>

<br/>

**NOTE**: Jazz will continue to send the approval requests via the email. Once you click on either **Reject** or **Approve** in the email, it will redirect you to this same approval notification page and show the message accordingly.
