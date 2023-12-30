#!/bin/bash
# Utility script for starting a release
# Create a release/month branch and push it upstream to github
# for the standard set of repos
# Author: Daniel Winterstein <daniel@winterwell.com>

FORESTOFGITS=~/winterwell;

REPOSITORIES="code open-code adserver my-loop sogive-app wwappbase.js elasticsearch-java-client";
# echo "REPOSITORIES = $REPOSITORIES";
# REPOSITORIES="business code open-code sogive-app adserver companies config sodash www sodash.com jtwitter jgeoplanet juice pinpoint SJTest jshorten utr rfm digital-adaptations dns" 

# CONFIG=~/.winterwell/gsync
#
# if [ -f $CONFIG ]
# then
#     #echo Loading configuration from $CONFIG
#     . $CONFIG
# 	echo "Loaded config from $CONFIG. Repositories are $REPOSITORIES"
# fi

if [ ! -d $FORESTOFGITS ]
then
    echo Could not locate base directory. Please set \$FORESTOFGITS in $CONFIG.
    exit 1
fi

# Remember our current position to restore it at the end
HERE=`pwd`;

failures=""

MONTH=`date +%Y-%m`
BRANCH="release/$MONTH"

for REPO in $REPOSITORIES
do
	if [ -d $FORESTOFGITS/$REPO ];
	then
		echo "		***   $REPO $BRANCH   ***"
	    cd $FORESTOFGITS/$REPO
	    if git checkout -b $BRANCH
		then
            if git push --set-upstream origin $BRANCH
            then
                echo "...$REPO done :)"
            else
                failures+="push $REPO, "
            fi
		else
			failures+="checkout $REPO, "
		fi
	else
		echo "	skipping $REPO"
	fi
done

if [ ! -z "$failures" ]
then 
	echo "WARNING: command failed on: $failures"
fi

cd $HERE;
