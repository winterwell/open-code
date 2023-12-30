#!/bin/bash
# Utility script for a feature branch + PR
# 1. Create a feature/month/name branch
# 2. Commit local edits, 
# 3. Push it upstream to github, 
# 4. Open the PR page
# Author: Daniel Winterstein <daniel@winterwell.com>

NAME="$1"
NAME=${NAME// /-} # spaces break below, so remove them

if [ ! $NAME ]
then
    echo "git-branch-pr.sh -- missing feature name";
	echo "";
    echo "Usage: git-branch-pr.sh FEATURE_NAME";
    echo "Example: git-branch-pr.sh my-new-widget";
    exit 1;
fi

failures=""

MONTH=`date +%Y-%m`
BRANCH="feature/$MONTH/$NAME"
PWD=`pwd`
REPO=`basename $PWD`

# HACK handle our multi-project repos, open-code and code
if [[ $PWD =~ "open-code" ]]; then
	REPO="open-code"
elif [[ $PWD =~ "winterwell/code/" ]]; then
	REPO="code"
fi

echo "Repo: $REPO"
echo "	git checkout -b $BRANCH"
if git checkout -b $BRANCH
then	
	echo "	git commit -a -m $BRANCH"
	git commit -a -m "$BRANCH"
	echo "	git push --set-upstream origin $BRANCH"
	git push --set-upstream origin $BRANCH	
	xdg-open "https://github.com/good-loop/$REPO/pull/new/$BRANCH"
	echo "...done"	
else
	failures+="checkout"
fi

if [ ! -z "$failures" ]
then 
	echo "WARNING: command failed on: $failures"
fi
