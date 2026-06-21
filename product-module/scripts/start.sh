#!/bin/bash
set -e

source /home/ec2-user/deploy/deploy.env

aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin $ECR_URI

docker pull $ECR_URI/donworry/product-module:$IMAGE_TAG

docker run -d \
  --name product-module \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=$DB_URL \
  -e SPRING_DATASOURCE_USERNAME=$DB_USER \
  -e SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD \
  $ECR_URI/donworry/product-module:$IMAGE_TAG

echo "product-module started: $IMAGE_TAG"