+++
date = "2021-03-22T09:00:00-07:00"
draft = false
title = "Resolving Pacbot Compliance Issues"
author = "Satish Malireddi"
weight = 40

[menu.main]
Name = "Resolving Pacbot Compliance Issues"
parent = "utilities"
pre = "Guide to resolve compliance issues reported by Pacbot"
+++
<br/>

This document should help you with fixing any violations or compliance issues reported by Pacbot on any of your assets that got created through Jazz platform.

<br/>


### Policy - S3 should have network restrictions

<br/>

- **Policy Details:**

    S3 buckets have become the go to service for collecting or temporarily staging data for applications running on AWS. Today we have all types of sensitive data on S3 buckets and these needs to be secured appropriately. Restricting the locations from where S3 bucket data can be read or modified provides an additional layer of security. AWS S3 bucket policy can be leveraged to apply network restrictions on buckets. Every S3 bucket should have a bucket policy explicitly allowing known services and IPs only.

- **Why is it an issue when using Jazz?**

    If your website is hosted through Jazz, we create an S3 bucket to host the static content. AWS CloudFront service is also used in this context and serves as CDN. For public or internal websites, users will never interact with S3 directly while accessing the content, instead they hit the CloudFront endpoint which inturn pulls the content from S3 & caches it before returning the content back to the user. Only CloudFront will have access to S3. However, malicious users can still access content directly from S3 if they get access to AWS credentials or if the S3 bucket is misconfigured by developers (although a rare event). To avoid this, it is important to secure S3 buckets by applying a bucket policy.  Remember that existing bucket policy that gives CloudFront access to S3 bucket should NOT be removed. Simply, append the existing policy with the new policy. See below for the policy that we recommend using for websites.

    If you are creating S3 buckets as part of your serverless service, you have to define a bucket policy that suits your use case and apply it. If do not know which bucket policy to apply, start with one that restricts the access to T-Mobile & Sprint IPs. See below for the policy that you can start with.

- **How do I fix this?**

    Apply the policy below to give users permisssions to read/write when they try to access S3 bucket directly from the internal T-Mobile & Sprint network. For websites hosted through Jazz, remember NOT to remove the existing CloudFront based policy. 

    You can also follow [this](https://ccoe.docs.t-mobile.com/aws/how-to_guides/S3_Bucket_Policy_Guidance/) for more examples and explanation.

    ```json
    {
        "Version": "2012-10-17",
        "Id": "SourceIP",
        "Statement": [
            {
                "Sid": "SourceIP",
                "Effect": "Allow",
                "Principal": "*",
                "Action": [
                    "s3:DeleteObject",
                    "s3:DeleteObjectTagging",
                    "s3:DeleteObjectVersion",
                    "s3:DeleteObjectVersionTagging",
                    "s3:GetObject",
                    "s3:GetObjectAcl",
                    "s3:GetObjectLegalHold",
                    "s3:GetObjectRetention",
                    "s3:GetObjectTagging",
                    "s3:GetObjectTorrent",
                    "s3:GetObjectVersion",
                    "s3:GetObjectVersionAcl",
                    "s3:GetObjectVersionForReplication",
                    "s3:GetObjectVersionTagging",
                    "s3:GetObjectVersionTorrent",
                    "s3:ListMultipartUploadParts",
                    "s3:ObjectOwnerOverrideToBucketOwner",
                    "s3:PutObject",
                    "s3:PutObjectAcl",
                    "s3:PutObjectLegalHold",
                    "s3:PutObjectRetention",
                    "s3:PutObjectTagging",
                    "s3:PutObjectVersionAcl",
                    "s3:PutObjectVersionTagging",
                    "s3:ReplicateDelete",
                    "s3:ReplicateObject",
                    "s3:ReplicateTags",
                    "s3:RestoreObject",
                    "s3:PutBucketPublicAccessBlock",
                    "s3:PutBucketAcl",
                    "s3:PutBucketPolicy",
                    "s3:DeleteBucketPolicy"
                ],
                "Resource": [
                    "arn:aws:s3:::<BUCKET_NAME>",
                    "arn:aws:s3:::<BUCKET_NAME>/*"
                ],
                "Condition": {
                    "IpAddress": {
                    "aws:SourceIp": [
                        "208.54.0.0/17",
                        "206.29.160.0/19",
                        "208.54.147.0/27",
                        "208.54.128.0/20",
                        "208.54.144.0/20",
                        "66.94.0.196/32",
                        "66.94.0.197/32",
                        "66.94.0.198/32"
                    ]
                    }
                }
            }
        ]
    }
    ```

