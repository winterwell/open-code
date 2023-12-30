#!/bin/bash
# Utility script for local checkout of a github PR
# Author: Daniel Winterstein <daniel@winterwell.com>

PR_ID="$1"
STABLE="stable-closed-code"

if [ ! $PR_ID ]
then
    echo "gitpr.sh: Checkout a github PR to local";
    echo "Usage: gitpr.sh [PR_ID]";
	echo "This will pull e.g. PR #123, creating a local branch called PR/123, and checkout the branch."
    exit 1
fi

START=`pwd`;

git fetch origin pull/$PR_ID/head:PR/$PR_ID

git checkout PR/$PR_ID
