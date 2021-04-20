
+++
date = "2017-11-27T14:00:00-07:00"
draft = false
title = "Certificates"
weight = 18

[menu.main]
Name = "Certificates"
parent = "features"

pre = "Jazz uses AWS ACM service to provision and manage certificates for Jazz endpoints"

+++
<!-- Add a short description in the pre field inside menu

SSL certificates for Jazz endpoints
================================================== -->

## Overview

Jazz uses AWS ACM service to provision and manage certificates for Jazz endpoints that end with _.jazz.t-mobile.com_. Since ACM is a complete managed service offering from AWS, the complexity of creating and managing public SSL/TLS certificates for the websites & applications is abstracted away and is taken care by AWS completely. Since ACM offers public certificates for free in addition to zero operations cost, it reduces lot of overhead on cloud support/infrastructure teams in T-Mobile. Also, this makes ACM the most cost-effective solution for managing public certificates that are applied to AWS endpoints like Cloudfront & API Gateway services.

<br/>

- [Scope of services](#scope)
- [Request a new certificate](#new-certificate)
- [Revoke an existing certificate](#revoke-certificate)
- [Renewing certificates](#renew-certificates)
- [Certificate Management - Internals](#certificate-internals)
  - [Domain Validation](#domain-validation)
  - [Monitoring and Governance](#governance)
  - [Support](#support)
- [FAQs](#faqs)

<br/>

<div id="scope"></div>

## Scope of services

AWS ACM currently supports only few AWS managed services - AWS CloudFront, AWS API Gateway & Elastic Load Balancing. This implies that ACM certificates **cannot** be applied to

- AWS services other than the three supported services (listed above)

- Endpoints hosted outside AWS (On-premises, other cloud providers etc.)

- Endpoints in AWS that have custom domain names NOT provisioned using AWS Route 53

<br/>

<div id="new-certificate"></div>

## New certificate creation

Jazz platform has automated the certificate creation process using AWS APIs. When users request custom domains for endpoints in Jazz, new certificates will be created & applied to the endpoints automatically. To make this simple, custom domain request & certificate request is combined into one request and gets kicked off when user request a new custom domain through Jazz UI. This request triggers a new automated workflow with the following stages -

- Checks if the endpoint is created using Jazz & is available

- Goes though approval step so that appropriate stakeholders (Jazz support, cloud support teams) approve this request

- Creates a new certificate using AWS ACM APIs

- Completes domain validation through AWS Route53 APIs

- Applies the certificate to the endpoint

- Notifies the user about the status of the request

<br/>

<div id="revoke-certificate"></div>

## Revoke an existing certificate

Jazz platform has automated the certificate removal process using AWS APIs. Since Jazz manages underlying endpoints, it detects when an endpoint is deleted and removes the certificate attached to it & deletes it immediately.

<br/>

<div id="renew-certificates"></div>

## Renewing certificates

ACM provides managed renewal feature for the Amazon-issued public SSL/TLS certificates. ACM renews these certificates automatically with no action required from us.

<br/>

<div id="certificate-internals"></div>

## Certificate Management - Internals

This section covers how Jazz leverages AWS tool set to provision, manage and monitor certificates that were provisioned using AWS ACM.

<div id="domain-validation"></div>


### Domain Validation

Before the Amazon certificate authority (CA) can issue a certificate for Jazz endpoints, AWS Certificate Manager (ACM) must verify that we own or control all of the domain names that we specified in the request. Jazz uses DNS validation process to complete this step when a new certificate is created.

In the DNS validation process, ACM provides a CNAME record to insert into the DNS database (AWS Route 53). Since Jazz leverages AWS Route 53 service for managing Jazz DNS endpoints, these records are added automatically to complete this validation. ACM automatically renews your certificate as long as the certificate is in use and this CNAME record remains in place.

<br/>

<div id="governance"></div>

### Monitoring & Governance

Jazz platform provisions certificates in ACM & tags them with appropriate AWS tags to facilitate asset discovery & easier management. AWS exposes set of management APIs for getting all the required information about each certificate in ACM. PacBot will treat certificates as assets, collects all the asset information & runs policy checks against each of these certificate assets to meet various compliance policies related to validity, ownership etc. Any violation would be alerted & notified to the appropriate stakeholders - cloud support, Jazz platform team, application owner etc. Since certificates will be tagged, different stakeholders can access information related to these certificates directly in [PacBot](https://pacbot.t-mobile.com/pl/compliance/certificate-compliance?ag=jazz) if required.

<br/>

<div id="support"></div>

### Support

Only Jazz platform team & cloud support team will have access to AWS ACM & Route 53 services. Outside Jazz's automated system, every request related to these certificates or DNS records will go through the existing change management practices that cloud support team follows.

Please contact #cloud-support if you need help with certificates issued by Jazz.

<br/>

<div id="faqs"></div>

## Frequently Asked Questions

<br/>

- Question: Why can't we use certificates issued by T-Mobile internal private CA for internal Jazz endpoints?
  
  Jazz leverages managed services like AWS CloudFront &AWS API Gateway under the hood. Starting 4/9/2019, AWS added additional security features that prevented us from installing/applying certificates issued by internal T-Mobile root CA. This forced us to use certificates that come from a publicly trusted Certificate Authority like AWS ACM. Read more about this [here](https://aws.amazon.com/about-aws/whats-new/2019/04/amazon-cloudfront-enhances-the-security-for-adding-alternate-domain-names-to-a-distribution/)!

- Question: Why can't we use Entrust signed certificates if we need public certificates issued by publicly trusted Certificate Authority?

  We can definitely use T-Mobile approved Entrust signed certificates in Jazz. However, they can be expensive at scale (100s of endpoints) & difficult to manage (manual overhead of monitoring, renewal with the risk of service outages if not renewed on time). On the other hand, AWS ACM is completely managed with no cost or management overhead.
  