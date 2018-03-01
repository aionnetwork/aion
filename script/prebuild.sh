# append the git revision into the kernel version number
GITVER=$(git log -1 --format="%h")
TOKEN="public static final String VERSION ="
sed -i -r "/$TOKEN/ s/.{2}$//" ./modAionImpl/src/org/aion/zero/impl/AionHub.java
sed -i "/$TOKEN/ s/$/.$GITVER\";/" ./modAionImpl/src/org/aion/zero/impl/AionHub.java
