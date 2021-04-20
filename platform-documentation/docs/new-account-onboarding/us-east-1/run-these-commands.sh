#Adding support for $accountName (us-east-1)

#create apigateway
aws apigateway create-rest-api \
    --name 'jazz.internal' \
    --profile $accountName \
    --region us-east-1 \
    --endpoint-configuration '{ "types": ["REGIONAL"] }' \
    --policy file://../policies/api-gateway-resource-policy.json

aws apigateway create-rest-api \
    --name 'stg.jazz.internal' \
    --profile $accountName \
    --region us-east-1 \
    --endpoint-configuration '{ "types": ["REGIONAL"] }' \
    --policy file://../policies/api-gateway-resource-policy.json

aws apigateway create-rest-api \
    --name 'dev.jazz.internal' \
    --profile $accountName \
    --region us-east-1 \
    --endpoint-configuration '{ "types": ["REGIONAL"] }' \
    --policy file://../policies/api-gateway-resource-policy.json


# create s3
aws s3api create-bucket \
    --bucket jazz-deployments-$accountName-useast1-prod \
    --region us-east-1 \
    --profile $accountName
 
aws s3api create-bucket \
    --bucket jazz-deployments-$accountName-useast1-stg \
    --region us-east-1 \
    --profile $accountName

aws s3api create-bucket \
    --bucket jazz-deployments-$accountName-useast1-dev \
    --region us-east-1 \
    --profile $accountName

# put bucket policy
aws s3api put-bucket-policy \
    --bucket jazz-deployments-$accountName-useast1-prod \
    --policy file://put_bucket_policy_prod_$accountName.json \
    --profile $accountName
 
aws s3api put-bucket-policy \
    --bucket jazz-deployments-$accountName-useast1-stg \
    --policy file://put_bucket_policy_stg_$accountName.json \
    --profile $accountName
 
aws s3api put-bucket-policy \
    --bucket jazz-deployments-$accountName-useast1-dev \
    --policy file://put_bucket_policy_dev_$accountName.json \
    --profile $accountName

#put CORS policy for bucket
aws s3api put-bucket-cors \
	--bucket jazz-deployments-$accountName-useast1-prod \
	--cors-configuration file://bucket_cors.json \
	--profile $accountName \
	--region us-east-1

aws s3api put-bucket-cors \
	--bucket jazz-deployments-$accountName-useast1-stg \
	--cors-configuration file://bucket_cors.json \
	--profile $accountName \
	--region us-east-1

aws s3api put-bucket-cors \
	--bucket jazz-deployments-$accountName-useast1-dev \
	--cors-configuration file://bucket_cors.json \
	--profile $accountName \
	--region us-east-1

#put tags
aws s3api put-bucket-tagging \
	--bucket jazz-deployments-$accountName-useast1-prod \
	--tagging file://bucket_tags.json \
	--profile $accountName \
	--region us-east-1

aws s3api put-bucket-tagging \
	--bucket jazz-deployments-$accountName-useast1-stg \
	--tagging file://bucket_tags.json \
	--profile $accountName \
	--region us-east-1

aws s3api put-bucket-tagging \
	--bucket jazz-deployments-$accountName-useast1-dev \
	--tagging file://bucket_tags.json \
	--profile $accountName \
	--region us-east-1
