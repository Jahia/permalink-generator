#!/bin/bash

source ./set-env.sh

echo " == Printing the most important environment variables"
echo " MANIFEST: ${MANIFEST}"
echo " TESTS_IMAGE: ${TESTS_IMAGE}"
echo " JAHIA_IMAGE: ${JAHIA_IMAGE}"
echo " JAHIA_CLUSTER_ENABLED: ${JAHIA_CLUSTER_ENABLED}"
echo " MODULE_ID: ${MODULE_ID}"
echo " JAHIA_URL: ${JAHIA_URL}"
echo " SUPER_USER_PASSWORD: ${SUPER_USER_PASSWORD}"

# docker-compose bind-mounts ./assets into the jahia container, which reads it as its own user.
# On a host whose umask removes read access for "other" (e.g. 0027), the checkout leaves these
# files 640: Jahia cannot install empty-templates-1.0.0.jar, the test sites are never created and
# most specs fail. Make the folder world-readable before the stack starts.
chmod -R o+rX ./assets

version=$(node -p "require('./package.json').devDependencies['@jahia/cypress']")
echo Using @jahia/cypress@$version...
npx --yes --package @jahia/cypress@$version ci.startup
