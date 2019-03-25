#!/bin/bash

CONFIG_DIR='config/avmtestnet'
CONFIG=$CONFIG_DIR'/config.xml'
CONFIG_SEED1=$CONFIG_DIR'/config-seed1.xml'
CONFIG_SEED2=$CONFIG_DIR'/config-seed2.xml'
CONFIG_SEED3=$CONFIG_DIR'/config-seed3.xml'
GENESIS=$CONFIG_DIR'/genesis.json'

echo ' '
echo 'Getting the git commit hash of the latest commit...'
full_hash=$(git log --pretty=format:%H | head -n 1)

if [ $? -ne 0 ]
then
	echo 'Failed to get the commit hash. Are you sure this is a git repository?'
	exit 1
fi

echo ">> The latest commit hash is: $full_hash"
partial_hash=${full_hash:0:12}

echo 'Constructing the new node IDs...'

# First, only construct the ID itself
id1='10000000-0000-0000-0000-'$partial_hash
id2='20000000-0000-0000-0000-'$partial_hash
id3='30000000-0000-0000-0000-'$partial_hash

# Second, construct the 'full id': id plus IP & port
id_full1=$id1'@138.91.123.106:30303'
id_full2=$id2'@23.100.52.181:30303'
id_full3=$id3'@13.91.127.35:30303'

# Finally, construct an id with an id tag around it
id_tag1='<id>'$id1'</id>'
id_tag2='<id>'$id2'</id>'
id_tag3='<id>'$id3'</id>'

echo '>> Updating the config file with the new node IDs'
sed -i -e "s#10000000-.*30303#$id_full1#g" $CONFIG
sed -i -e "s#20000000-.*30303#$id_full2#g" $CONFIG
sed -i -e "s#30000000-.*30303#$id_full3#g" $CONFIG

echo '>> Updating the seed #1 config file with the new node IDs'
sed -i -e "s#10000000-.*30303#$id_full1#g" $CONFIG_SEED1
sed -i -e "s#20000000-.*30303#$id_full2#g" $CONFIG_SEED1
sed -i -e "s#30000000-.*30303#$id_full3#g" $CONFIG_SEED1
sed -i -e 's#<id>10000000-0000-0000-0000-000000000000</id>#'"$id_tag1#g" $CONFIG_SEED1

echo '>> Updating the seed #2 config file with the new node IDs'
sed -i -e "s#10000000-.*30303#$id_full1#g" $CONFIG_SEED2
sed -i -e "s#20000000-.*30303#$id_full2#g" $CONFIG_SEED2
sed -i -e "s#30000000-.*30303#$id_full3#g" $CONFIG_SEED2
sed -i -e 's#<id>20000000-0000-0000-0000-000000000000</id>#'"$id_tag2#g" $CONFIG_SEED2

echo '>> Updating the seed #3 config file with the new node IDs'
sed -i -e "s#10000000-.*30303#$id_full1#g" $CONFIG_SEED3
sed -i -e "s#20000000-.*30303#$id_full2#g" $CONFIG_SEED3
sed -i -e "s#30000000-.*30303#$id_full3#g" $CONFIG_SEED3
sed -i -e 's#<id>30000000-0000-0000-0000-000000000000</id>#'"$id_tag3#g" $CONFIG_SEED3

echo 'Constructing the new genesis parent block hash...'
parent='"parentHash": "0x'$full_hash'000000000000000000000000"'

echo '>> Updating the genesis block with the new parent block hash'
sed -i -e "s#"'"parentHash"'.*'"'"#$parent#g" $GENESIS

echo ' '
echo '######################## FIN ########################'
echo ' '
