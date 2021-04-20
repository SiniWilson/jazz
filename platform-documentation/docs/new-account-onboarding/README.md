## Add a new AWS account/region to Jazz

### Notes

- All of this can be automated!! :)

- Jazz can connect to new AWS accounts in two regions only - `us-west-2` & `us-east-1`

- You will need the following to start -
  
  - IAM credentials for the primary account (`arn:aws:iam::302890901340:user/svc_oaf_prd`). These will be available as `AWS_302890901340_ACCESS_KEY` & `AWS_302890901340_SECRET_KEY` in Variables section at https://gitlab.com/groups/tmobile/jazz/core/-/settings/ci_cd

  - Work with cloud support team on the following items - 
    
    - Three IAM roles that will be used by the platform (See roles in one of the existing non primary accounts for permissions)

      - `arn:aws:iam::$accountId:role/jazz-basic_services`

      - `arn:aws:iam::$accountId:role/jazz-platform_services`

      - `arn:aws:iam::$accountId:role/jazz-vpc_services`
    
    - Update the pre-existing policy in the primary account - `arn:aws:iam::302890901340:policy/svc_oaf_prd_sts_jazzroles` by adding `arn:aws:iam::$accountId:role/jazz-platform_services`. This allows the IAM user that Jazz uses in the primary account (`arn:aws:iam::302890901340:user/svc_oaf_prd`) to provision various resources in the new account.

    - For website services, following service linked roles are required to be provisioned (https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-edge-permissions.html#using-service-linked-roles)- 
       
       - AWSServiceRoleForLambdaReplicator (`aws iam create-service-linked-role --aws-service-name replicator.lambda.amazonaws.com`)

       - AWSServiceRoleForCloudFrontLogger (`aws iam create-service-linked-role --aws-service-name logger.cloudfront.amazonaws.com`)

- Use the scripts in the two folders (one per region) to set it up.

### Steps

- Setup AWS profile for the primary account (for example, `aws configure --profile tmodevops`).
- Get temporary credentials in the new account to provision resources -
  - `aws sts assume-role --role-arn arn:aws:iam::$accountId:role/jazz-platform_services --role-session-name jazz-onboarding-session --profile tmodevops`
  - Setup AWS profile for the new account credentials that we've got (`aws configure --profile $accountName`).
- For each region folder,
  - cd `./$region`
  - Within the folder (each file), replace $accountId, $accountName to match with the new account related information. Update file names too (if any). You can use something like
    - `mv put_bucket_policy_prod_\$accountName.json put_bucket_policy_prod_foobar.json`
    - `mv put_bucket_policy_dev_\$accountName.json put_bucket_policy_dev_foobar.json`
    - `mv put_bucket_policy_stg_\$accountName.json put_bucket_policy_stg_foobar.json`
    - `sed -i 's|$accountName|foobar|g' put_bucket_policy_*`
    - `sed -i 's|$accountName|foobar|g'  run-these-commands.sh`
    - `sed -i 's|$accountId|123123123123|g' put_bucket_policy_*`
    - `sed -i 's|$accountId|123123123123|g'  run-these-commands.sh`
    - Run the setup script: `./run-these-commands.sh` to create the shared resources one by one.
    - For logs to get streamed from the new account to the primary account, update the logs destination policy.
      - Get existing destination config - `aws logs describe-destinations --profile tmodevops --destination-name-prefix jazz-$region-kinesis --region $region`
      - Update destination config to include the new account number for each region - us-east-1/us-west-2 (DO NOT remove the old accountIds from the policy) - `aws logs put-destination-policy --destination-name jazz-$region-kinesis --access-policy file://policies/logs_destination_policy_tmodevops_$region.json --region $region --profile tmodevops`
- Update primary account platform role by adding the policy located at `/policies/permission_policy_tmodevops.json` to primary account role (i.e., `arn:aws:iam::302890901340:role/jazz_platform_services`)
- You now have the required information to fill in the account template that we need it Jazz config. Go ahead and replace the placeholders in `/templates/jazz-new-account-admin-config.json`. `CLOUDFRONT.LAMBDA_AT_EDGE` values can be updated again after lambda@edge functions are deployed to the new account. Run the commands below to replace accountId & accountName - 
  - `sed -i 's|$accountName|foobar|g' templates/jazz-new-account-admin-config.json`
  - `sed -i 's|$accountId|123123123123|g' templates/jazz-new-account-admin-config.json`
- Update Jazz admin configurations to add the new account information under `$.JAZZ.ACCOUNTS`
- Rebuild Jazz UI to see the new account information in accounts list
- Deploy lambda@edge functions to `us-east-1`
  - Follow the instructions using the links below to deploy the three lambda@edge authorizers to the new account. Grab the ARNs with versions once deployed.
    - https://gitlab.com/tmobile/jazz/core/jazz_ipauthidxfw#onboarding-new-account-in-jazz
    - https://gitlab.com/tmobile/jazz/core/jazz_ipauth#onboarding-new-account-in-jazz
    - https://gitlab.com/tmobile/jazz/core/jazz_idxfwd#onboarding-new-account-in-jazz
  - Update Jazz admin configurations by replacing the placeholders under `CLOUDFRONT.LAMBDA_AT_EDGE`.
- Deploy util service that deletes disabled cloudfront distributions to `us-west-2`. This is a cron job that gets triggered once each day.
  - Follow the instructions using the link below to deploy the service. Remember to update the role for this account in `serverless.yml`
    - https://gitlab.com/tmobile/jazz/shared/jazz_deldisbldcf#onboarding-new-account-in-jazz
- We're all set! Try deploying a service in the new account. Ensure that the service is deployed successfully with correct configurations.
- Also, add this accountId to the file: `/policies/logs_destination_policy_tmodevops.json/` so that next update uses the complete list of accountIds that Jazz supports.
- Enjoy!

### Support access to the new account
 - In order to support new teams who wil be using this new account, platform support team should have access to manage resources created by Jazz. To enable this, request a new AD group in the new account with the required permissions. `r_aws_$accountId_jazz_devops` is the expected group name. For permissions and other details, use one of the pre-existing groups (for example, `r_aws_484695107796_jazz_devops` as the reference). 

### AWS accounts that Jazz supports - 

- tmodevops (302890901340)
- shared-npe (484695107796)
- npe2 (700185120456)
- cde-prd (641049064253)
- sd-prd (678337661745)
- dcp-prod (632971917199)
- dcp-npe (048836254894)
- dd-prod (596182107118)
- dd-npe (714738285719)
- l3-prod (753932318323)
- l3-npe2 (742298342670)
- l3-npe (719951002453)
- tfb-npe (515379432062)
- tfb-prod (711516429993)
- vmas-prod (662026645853)
- vmas-npe (722794760258)


### Current set of IAM roles that Jazz uses for cross account access (update this with each new account onboarding) - 

- arn:aws:iam::302890901340:role/jazz_platform_services
- arn:aws:iam::641049064253:role/jazz-platform_services
- arn:aws:iam::048836254894:role/jazz-platform_services
- arn:aws:iam::632971917199:role/jazz-platform_services
- arn:aws:iam::714738285719:role/jazz-platform_services
- arn:aws:iam::596182107118:role/jazz-platform_services
- arn:aws:iam::809289188164:role/jazz-platform_services
- arn:aws:iam::719951002453:role/jazz-platform_services
- arn:aws:iam::742298342670:role/jazz-platform_services
- arn:aws:iam::753932318323:role/jazz-platform_services
- arn:aws:iam::678337661745:role/jazz-platform_services
- arn:aws:iam::114746631570:role/jazz-platform_services
- arn:aws:iam::484695107796:role/platform_services_role_jazz
- arn:aws:iam::700185120456:role/jazz-platform_services
- arn:aws:iam::515379432062:role/jazz-platform_services
- arn:aws:iam::711516429993:role/jazz-platform_services
- arn:aws:iam::722794760258:role/jazz-platform_services
- arn:aws:iam::662026645853:role/jazz-platform_services
