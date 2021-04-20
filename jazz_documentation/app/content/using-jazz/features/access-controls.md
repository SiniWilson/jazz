
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Managing permissions for your Jazz service"
weight = 12

[menu.main]
Name = "Access Controls"
parent = "features"

pre = "Jazz provides controls to manage who gets access to what within your service"

+++
<!-- Add a short description in the pre field inside menu

Setting Access Controls
================================================== -->

## Summary

Developers can now share access to their services with other developers who are collaborating in a team, application or project setup. As a service administrator, you can assign permissions to other users so that they can perform various developer activities like view the service in Jazz UI, manage it, have access to the code, view build & deployment logs, trigger deployments etc. all without leaving Jazz! To keep it simple, we have used minimum set of roles for you to use while managing these permissions.

*Note:* In order for you to add your team members, ensure that they have cloud accounts. If they don't have one already, they can sign-up using instructions provided [here]({{< ref "login.md" >}}). Ensure that they follow these instructions so that Jazz can discover them and assign appropriate permissions. This step has to be completed to proceed forward.

<br/>

* [Categories, Permissions & Roles](#roles-categories)
* [Manage Permissions in Jazz UI](#manage-access)
* [Current Limitations](#limitations)

<br/>

<div id="roles-categories">
</div>

## Categories, Permissions & Roles

Following table explains different permission categories & roles under each category

| Category        | Role           | Description  | Supported Operations | Who should be added to this role? |
| ------------- |-------------| :-----: | :-------------: |:-------------:|
| Manage | read | Read-only permissions on the service in Jazz UI | Read access to code, view service details, environments, existing users and permissions, deployments, logs, metrics, code quality reports etc. | All developers |
| Manage | admin | Administer the service in Jazz UI. Users in this role will have ALL permissions on this service| All the above plus manage user permissions, delete service and manage dns/certificate settings - So, everything! | Administrators, Service creator, Service owner, Project owner |
| Code | read |  Users with this role get read-only permissions to the code | Read-only access to code | Developers who need read-only access to code |
| Code | write |  Users with this role get read-write permissions to the code | Read/Write access to code, create/merge merge/pull requests | All developers|
| Deploy | read |  Users with this role get read-only access to deployment logs for this service | View deployment logs | Team members with DevOps role |
| Deploy | write | Users with this role get access to trigger deployments to this service in Jazz UI | View deployment logs, Rebuild (trigger deployments) from UI | Team members with DevOps role|

Notes - 

* Only the users with _**admin**_ role under _**management**_ category can administer the service. They can also modify access controls for the service (add/remove users to various roles), manage/delete the service etc. Use caution while assigning this role to the users.

* Users when assigned with _**read**_ role under _**management**_ category will get read access to everything related to the service including read access to code (automatically). This will be the most common role for your team members by default. If you need to assign write access to the code to the user, you can add them to _**write**_ role under _**code**_ category.

* Gitlab access - Service owners/admins will only get Read/Write permissions for their repository in Gitlab. In order to allow other developers to contribute to the code, simply add them under _**code**_ category in Jazz UI. This will automatically assign relevant permissions (read/write) to your Gitlab repository - easy peasy!

* Adding users to other two categories _(**code** & **deploy**)_ will automatically assign _**read**_ role under _**management**_ category to the service.

<br/>

<div id="manage-access">
</div>

## Manage Permissions in Jazz UI

From the _Service Dashboard_ page, click on the service which will take you to the _Service Overview_ page. You can find _Access Control_ section which will be used to manage permissions for your service. Only users with admin access to the service can modify the permissions.

- *View Existing Permissions*

    <img src='/content/jazz-features/media/access-controls/view-permissions.png' width='800px'>

<br/>

- *Add new users to admin role*

    <img src='/content/jazz-features/media/access-controls/select-add-users.png' width='800px'>

<br/>

- *New users are added to admin role. Note that the user got added to other categories automatically.*

    <img src='/content/jazz-features/media/access-controls/added-users-admin-role.png' width='800px'>

<br/>

- *Users with read role will not have access to manage permissions or delete the service (Note that the buttons are disabled for the logged in user)*

    <img src='/content/jazz-features/media/access-controls/added-users-read-role.png' width='800px'>

<div id="limitations">
</div>

## Current Limitations

* You can only discover and add users from Corporate Active Directory (Cloud AD) to these roles. As of now, you cannot discover or add AD groups directly.

* If a user is added to one of these roles under _**code**_ category and if they cannot login or do not have access to the repository in Gitlab yet, they haven't on-boarded onto Gitlab correctly. The user needs to be onboarded to Gitlab with their t-mobile email address i.e. XXX@t-mobile.com Refer CDP Gitlab [Getting Started](https://t-mo.co/360F1Uu) & [FAQ](https://bit.ly/2Mxfnz0)