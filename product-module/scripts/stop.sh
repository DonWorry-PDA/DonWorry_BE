#!/bin/bash
set -e
docker stop product-module 2>/dev/null || true
docker rm product-module 2>/dev/null || true
echo "product-module container stopped"