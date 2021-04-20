## How to rotate AWS credentials being used by Jazz


- Jazz uses two IAM users in the platform (both in the same aws account) 
    
    - **arn:aws:iam::302890901340:user/svc_oaf_prd**

    - **arn:aws:iam::302890901340:user/svc_apigee_jazz_exec**


- For Service deployments, Jazz uses “arn:aws:iam::302890901340:user/svc_oaf_prd”. This is the master account and we use these credentials to assume role in each of the accounts that Jazz supports before attempting to provision infrastructure in the other supported accounts
    
    - To rotate the credentials - 
        
        - Get a new pair of AWS access key and secret key.
        
        - Go to _https://gitlab.com/groups/tmobile/jazz/core/-/settings/ci_cd > Variables_.
        
        - Update **AWS_302890901340_ACCESS_KEY** & **AWS_302890901340_SECRET_KEY** with the new credentials.
        
        - Gitlab pipelines in this subproject uses these credentials to assume the role in the target account and gets the temporary credentials to provisions infrastructure in the target account.

        - Delete the older key after confirming that they are not being used anymore (check Last Activity in IAM console).

- For Deployments through Apigee SaaS, Jazz uses “arn:aws:iam::302890901340:user/svc_apigee_jazz_exec”. In fact, these user credentials are used by Apigee SaaS to trigger lambda functions in tmodevops account as part of the legacy integration between Apigee & Jazz.
    
    - To rotate the credentials -

        - Get a new pair of AWS access key and secret key.
    
        - Block time with Mudit.Purwar@T-Mobile.com (or someone from Apigee support) and get the credentials updated in Apigee.

        - Delete the older key after confirming that they are not being used anymore (check Last Activity in IAM console).

