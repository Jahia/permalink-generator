#!/bin/bash
source ./set-env.sh

# Debug runs on the host — override container-internal URLs to localhost
export JAHIA_URL=http://localhost:8080
export JAHIA_PROCESSING_URL=http://localhost:8080

version=$(node -p "require('./package.json').devDependencies['@jahia/cypress']")
echo Using @jahia/cypress@$version...
npx --yes --package @jahia/cypress@$version env.debug
