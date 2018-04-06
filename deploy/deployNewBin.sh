NODE_LIST=$1;
DEPLOY_BIN=$2;

IP=""
USER=""
PASSWORD=""

SERVERLIST=""

arrayIP=""
arrayUser=""
arrayPw=""
IDX=0

while IFS=$',' read -r ip ; do 
  arrayIP[$IDX]="$ip"

  echo "Deploy the server $IDX: ${arrayIP[$IDX]}"

  if [ "$SERVERLIST" == "" ]; then
      SERVERLIST="${arrayIP[$IDX]}"
  else
      SERVERLIST="${SERVERLIST},${arrayIP[$IDX]}"
  fi

#  echo "Copy bin  to the server $IDX: ${arrayIP[$IDX]}"
  scp $DEPLOY_BIN* "aion@${arrayIP[$IDX]}:/Aion/aionBuild/Kernel/" 
  
  echo "update tar on server $IDX: ${arrayIP[$IDX]}"
  printf -v __ %q $DEPLOY_BIN
  sshpass ssh "aion@${arrayIP[$IDX]}" "cd /Aion/aionBuild && ./Scripts/UpdateKernel.sh $__"
  
  ((IDX++))
done < $NODE_LIST
