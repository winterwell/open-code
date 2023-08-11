#!/bin/bash
ssh winterwell@baker.good-loop.com bash <<EOF 
/home/winterwell/config/build-scripts/builder.sh \
BUILD_TYPE="CI" \
PROJECT_NAME="datalogger" \
NAME_OF_SERVICE="lg" \
BRANCH_NAME="master" \
GIT_REPO_URL="github.com:good-loop/open-code" \
PROJECT_ROOT_ON_SERVER="/home/winterwell/open-code/winterwell.datalog" \
PROJECT_USES_BOB="yes" \
PROJECT_USES_NPM="yes" \
PROJECT_USES_WEBPACK="yes" \
PROJECT_USES_JERBIL="no" \
PROJECT_USES_WWAPPBASE_SYMLINK="yes"
EOF
