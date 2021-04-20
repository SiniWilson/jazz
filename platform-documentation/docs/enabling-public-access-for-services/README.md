## How to allow public access for websites or API endpoints?

This is not an automated process at this point. Jazz admins will need to update **is_public_endpoint** to **true** at the environment level upon approval from the application owner for a specific service.

You might follow this process - 

- Have application team engage DSO and get the required approval
- Have application team send an email to _ServerlessDev@T-Mobile.com_ explaing the use case and why public access is needed. Application owner should approve this request and acknowledge that APIs/Websites exposed to internet are prone to commonly known security threats and it is important to ensure that no sensitive information is being exposed through these APIs. If these do expose any sensitive information (by design), we recommend adding Apigee proxy layer for APIs & WAF layer for Websites.
- Once approved by application owner, update **is_public_endpoint** at the environment level.
    - Go to environment database and add/update **is_public_endpoint** = true

