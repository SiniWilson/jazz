# Create cloud front access OAI
aws cloudfront create-cloud-front-origin-access-identity \
    --cloud-front-origin-access-identity-config CallerReference=jazzoai1a8bc488f1024fb5ba226b2b2d29fc39,Comment=jazzoai1a8bc488f1024fb5ba226b2b2d29fc39 \
    --profile $accountName

#create apigateway
aws apigateway create-rest-api \
    --name 'jazz.internal' \
    --profile $accountName \
    --region us-west-2 \
    --endpoint-configuration '{ "types": ["REGIONAL"] }' \
    --policy file://../policies/api-gateway-resource-policy.json
    
aws apigateway create-rest-api \
    --name 'dev.jazz.internal' \
    --profile $accountName \
    --region us-west-2 \
    --endpoint-configuration '{ "types": ["REGIONAL"] }' \
    --policy file://../policies/api-gateway-resource-policy.json

aws apigateway create-rest-api \
    --name 'stg.jazz.internal' \
    --profile $accountName \
    --region us-west-2 \
    --endpoint-configuration '{ "types": ["REGIONAL"] }' \
    --policy file://../policies/api-gateway-resource-policy.json

# If needed, change bucket names to include accountname/region - avoid conflicts between different accounts/regions
# create s3
aws s3api create-bucket \
    --bucket jazz-deployments-$accountName-uswest2-prod \
    --region us-west-2 \
    --create-bucket-configuration LocationConstraint=us-west-2 \
    --profile $accountName

aws s3api create-bucket \
    --bucket jazz-deployments-$accountName-uswest2-stg \
    --create-bucket-configuration LocationConstraint=us-west-2 \
    --profile $accountName

aws s3api create-bucket \
    --bucket jazz-deployments-$accountName-uswest2-dev \
    --create-bucket-configuration LocationConstraint=us-west-2 \
    --profile $accountName
    
# put bucket policy
aws s3api put-bucket-policy \
    --bucket jazz-deployments-$accountName-uswest2-prod \
    --policy file://put_bucket_policy_prod_$accountName.json \
    --profile $accountName

aws s3api put-bucket-policy \
    --bucket jazz-deployments-$accountName-uswest2-stg \
    --policy file://put_bucket_policy_stg_$accountName.json \
    --profile $accountName

aws s3api put-bucket-policy \
    --bucket jazz-deployments-$accountName-uswest2-dev \
    --policy file://put_bucket_policy_dev_$accountName.json \
    --profile $accountName

#put bucket cors policy
aws s3api put-bucket-cors \
	--bucket jazz-deployments-$accountName-uswest2-prod \
	--cors-configuration file://bucket_cors.json \
	--profile $accountName

aws s3api put-bucket-cors \
	--bucket jazz-deployments-$accountName-uswest2-stg \
	--cors-configuration file://bucket_cors.json \
	--profile $accountName

aws s3api put-bucket-cors \
	--bucket jazz-deployments-$accountName-uswest2-dev \
	--cors-configuration file://bucket_cors.json \
	--profile $accountName

#put tagging for buckets
aws s3api put-bucket-tagging \
	--bucket jazz-deployments-$accountName-uswest2-prod \
	--tagging file://bucket_tags.json \
	--profile $accountName

aws s3api put-bucket-tagging \
	--bucket jazz-deployments-$accountName-uswest2-stg \
	--tagging file://bucket_tags.json \
	--profile $accountName

aws s3api put-bucket-tagging \
	--bucket jazz-deployments-$accountName-uswest2-dev \
	--tagging file://bucket_tags.json \
	--profile $accountName
