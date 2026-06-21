#!/bin/bash
set -e

source /home/ec2-user/deploy/deploy.env

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin $ECR_URI

# git SHA 태그로 이미지 pull
docker pull $ECR_URI/donworry/user-module:$IMAGE_TAG

# 컨테이너 실행
docker run -d \
  --name user-module \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e "SPRING_DATASOURCE_URL=$DB_URL" \
  -e "SPRING_DATASOURCE_USERNAME=$DB_USER" \
  -e "SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD" \
  -e "JWT_SECRET=$JWT_SECRET" \
  "$ECR_URI/donworry/user-module:$IMAGE_TAG"

echo "user-module started: $IMAGE_TAG"