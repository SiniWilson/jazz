
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "DNS"
weight = 17

[menu.main]
Name = "DNS"
parent = "features"

pre = "Jazz uses AWS Route53 service to provision custom domain names for Jazz endpoints"

+++
<!-- Add a short description in the pre field inside menu

Custom Domain Names for Jazz endpoints
================================================== -->

# Summary

Jazz uses AWS Route53 service to register and manage domain names for Jazz endpoints. [Certificates](https://docs.jazz.corporate.t-mobile.com/using-jazz/features/certificates/) are also provisioned using AWS Certificate Manager service as part custom domain requests. The complexity of creating and managing custom domain names and corresponding certificates for endpoints under Jazz applications is abstracted away from the developers. Self-Service is enabled so that developers can request new custom domains all by themselves and the complete request takes about 15 minutes to 1 hour (subject to request approval). All custom domains will be registered under **jazz.t-mobile.com** sub-domain (for example, your endpoint might look like this - **myapp.jazz.t-mobile.com**)

- [DNS overview](#dns-overview)
- [Permissions](#dns-permissions)
- [Request a new custom domain](#new-dns)
- [Update an existing custom domain](#edit-dns)
- [Certificates](#certificates)
- [DNS Management Internals](#dns-workflow)
  - [Workflow](#dns-workflow)
  - [Approvals](#approvals)
  - [Failure scenarios](#failure-scenarios)

<br/>
<div id="dns-overview"></div>

## DNS section in Jazz UI

You can now manage custom domain names for your service endpoints (website or API endpoints) using Jazz UI. Only AWS endpoints are supported as this point. Information related to existing custom domain names, ability to request a new one or update an existing domain name is available for authorized users in DNS section under each environment in Jazz UI.

<img src='/content/jazz-features/media/dns/001_dns_cert_web_dns_tab.png' width='800px'>

<br/>
<div id="dns-permissions"></div>

## Permissions

Only users who are administrators to the service will be able to manage custom domains for the service. Existing service administrators will be able to add others through 'Access Control' section in Jazz UI. Please follow instructions [here]({{< ref "access-controls.md" >}}) to manage permissions on your service.

<br/>
<div id="new-dns"></div>

## Request a new custom domain

Navigate to the environment where you have the endpoint for which you want a custom domain. Click on the DNS section and follow the steps below to request a new custom domain for your endpoint!

- Click 'Request DNS' button to initiate a request
    
    <img src='/content/jazz-features/media/dns/002_dns_cert_api_dns_tab.png' width='800px'>

- Check availability of the new domain

    <img src='/content/jazz-features/media/dns/003_dns_cert_request_dns_check_availability.png' width='400px'>

- If available, submit the request. You can track the status of your request in the same page. Once approved, your custom domain will be available to use.
    
    <img src='/content/jazz-features/media/dns/004_dns_cert_request_dns_submit.png' width='400px'>

<br/>
<div id="edit-dns"></div>

## Update existing domain

Once a DNS has been registered, you have the option to change it if desired using the 'Edit DNS' button available in the same page. You will be taken through similar steps as requesting a new custom domain.

<img src='/content/jazz-features/media/dns/009_dns_cert_edit_dns.png' width='800px'>

<br/>
<div id="certificates"></div>

## Certificates

Jazz completely manages certificates on your behalf. If your service is deployed in Jazz and has endpoint(s), https will be enabled by default and SSL certificates will be requested & applied to the appropriate endpoint(s). Not just that, certificate management is all taken care by Jazz and as a developer, you would never need to request, manage or worry about the certificates ever again! Read more [here]({{< ref "certificates.md" >}}) to understand how we do this under the hood.

<br/>
<div id="dns-workflow"></div>

## DNS workflow

Typical custom domain request goes through a workflow with these steps below - 

- **Request Validation**: Basic request validation steps like authorization, checking for naming conventions etc.

- **Certificate Approval**: Certificate goes through an approval process where the approver has about an hour to approve the Certificate (Approvers are typically members of Jazz platform team. Other teams (like DSO) will be added to the list of approvers internally if required). Request will need at least one approval to proceed forward.

- **Certificate Creation**: Once approved, certificate gets issued and is available for use during the DNS registration process.

- **DNS Approval**: Once the certificate is available, workflow goes through another approval step for DNS record creation.

- **DNS Creation**: Once DNS request is approved, the DNS record gets created, few updates are made to your endpoint which is when the new custom domain is ready to use. 

Note - On *.cloudfront.net based endpoints (typically websites), it usually takes 30-45 minutes for the DNS updates to propagate. It should be available in all locations once the change gets propagated completely.

<br/>

### Here is how the request workflow looks in Jazz UI -

- Pending Request Validation

    <img src='/content/jazz-features/media/dns/005_dns_cert_on_request.png' width='800px'>

- Pending Certificate Approval

    <img src='/content/jazz-features/media/dns/006_dns_cert_on_stage2.png' width='800px'>

- Pending DNS Approval
    
    <img src='/content/jazz-features/media/dns/007_dns_cert_awaiting_dns_approval.png' width='800px'>

- Request Completed
    
    <img src='/content/jazz-features/media/dns/008_dns_cert_completed_workflow.png' width='800px'>

<br/>
<div id="approvals"></div>

## Approvals

Every DNS change requires approval from various parties before it becomes available to use. However, all the underlying complexity is abstracted from developers. If the request is denied for any reason, developers can find additional information about who denied the request and the reason for the denial. Developers can reach out to the approver for more information. Optionally, if the request is denied/rejected, user can create a new request with a new domain name using the Edit/Request DNS option in the DNS section. In case the request times out, the user has the option to retry the same request. Once the request is approved, the new custom domain becomes active & ready to use!

<br/>
<div id="failure-scenarios"></div>

## Failure scenarios

The following failure scenarios can occur with your DNS request - 

- **REJECTED** - If the approver denies the request, the **only** option is to make another DNS request. Tool tip should show who rejected the request with timestamp details. You can always reach out to the approver for more details about the request denial.
    
    <img src='/content/jazz-features/media/dns/020_dns_cert_cert_rejected.png' width='800px'>

- **FAILED/TIMED_OUT** - If the approver fails to approve/deny the request within the stipulated time (1 hour) or if the request fails for any internal reason, user can either **retry** the request or create an entirely new request.

    <img src='/content/jazz-features/media/dns/021_dns_cert_request_timed_out.png' width='800px'>
