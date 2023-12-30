echo "Git non-master/main non-release Branch Check"
# NB: `release$` is to screen out e.g. repo:website branch:release, but do show e.g. repo:code branch:release/2023-10
gitall branch --show-current | grep -B 2 -vP "^(master|main|release$|fatal| |\t|\.)"
