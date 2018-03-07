# append the git revision into the kernel version number
GITVER=$(git log -1 --format="%h")
TOKEN="public static final String KERNEL_VERSION ="
sed -i -r "/$TOKEN/ s/.{2}$//" src/org/aion/zero/impl/Version.java
sed -i "/$TOKEN/ s/$/.$GITVER\";/" src/org/aion/zero/impl/Version.java

TOKEN2="public static final boolean FORK ="
REMOTE_URL=$(git remote -v | tail -n 1  | awk '{print $2}')
AION_REPO="https://github.com/aionnetwork/aion"
if [ "$REMOTE_URL" != "$AION_REPO" ]; then
    sed -i -r "/$TOKEN2/ s/false/true/g" src/org/aion/zero/impl/Version.java
fi
