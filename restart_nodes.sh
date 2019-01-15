nodes="138.91.123.106 23.100.52.181 13.91.127.35"
avm_repo="../AVM/"

aion_repo="$(pwd)" \
&& echo "Checking out the latest version of kernel avmtestnet-build branch ..." \
&& git checkout avmtestnet-build \
&& git pull origin avmtestnet-build \
&& git submodule update --init --recursive \
&& cd $avm_repo \
&& echo "Checking out latest version of Avm master branch ..." \
&& git checkout master \
&& git pull origin master \
&& echo "Building latest Avm jar ..." \
&& ant \
&& cd "$aion_repo"/lib/ \
&& rm org-aion-avm-*.jar \
&& rm avm.jar \
&& cp ../"$avm_repo"build/main/org-aion-avm-*.jar . \
&& cp ../"$avm_repo"dist/avm.jar . \
&& echo "Building the avm-integrated kernel release ..." \
&& cd .. \
&& ./gradlew clean \
&& ./gradlew pack \
&& cd pack

# First clean all the nodes...
echo " "
echo "1. Cleaning all nodes ..."
for node in $nodes
do
	echo "Cleaning node: $node ..." \
	&& ssh aion@"$node" "./clean_node.sh"
done

echo " "
echo "2. Updating all nodes with newest kernel build ..."
for node in $nodes
do
	echo "Sending the newly built kernel release to node: $node ..." \
	&& scp aion-v0.3.2*.tar.bz2 aion@"$node":/home/aion/
done

echo " "
echo "3. Re-starting all nodes ..."
number=1000
for node in $nodes
do
	echo "Setting up the node and launching the kernel ..." \
	&& ssh aion@"$node" "./start_node.sh "$number"" \
	&& echo "Successfully cleaned and re-started node: $node!"
	number=$((number + 1000))
done

