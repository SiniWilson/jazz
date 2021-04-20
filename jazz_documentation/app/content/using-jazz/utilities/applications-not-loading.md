+++
date = "2020-09-28T09:00:00-07:00"
draft = false
title = "Application list is not loading!"
author = "Prakash Raghothamachar"
weight = 40

[menu.main]
Name = "Application list is not loading!"
parent = "utilities"
pre = "Why are applications not loading?"
+++

<br/>

### Overview

When you create a new service or update details of an existing service, you can select an application to which you want to tag this service to. You might see that the application drop down is empty or you do not find the application that you intend to use in this context.

This usually happens when you are not a part of any AD groups for the specific application. Talk to your application owner and add yourself to one of the application specific AD groups.

User should be a part of at least one AD group for the application. These group names will look this - `r_aws_$accountId_$appId_$role`

Using cloud [access portal](https://access.t-mobile.com/my/groups/member), you can check the list of groups that you are already a part of. You can also request membership for a specific group using the same portal.

Here is an example - <br/>
    <img class="border" src='/content/utilities/jazz-access-group-name.png' width='700px'><br/>


Once you are added to the group(s), logout and login back again. You should now see your application in the application dropdown.
