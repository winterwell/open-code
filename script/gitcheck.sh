echo "Have we done our end of the day git commit and push?"
DEVS="Daniel Roscoe Vera Wing Aidan"

gitall log --all --since=4-hours-ago > /tmp/glog.txt

for DEV in $DEVS
do
	echo ""
	echo "$DEV"
	cat /tmp/glog.txt | grep -i -A3 $DEV
done

