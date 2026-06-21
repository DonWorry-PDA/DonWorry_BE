#!/bin/bash
set -e
docker stop user-module 2>/dev/null || true
docker rm user-module 2>/dev/null || true
echo "user-module container stopped"