#!/bin/sh

out_dir=/tmp
if [ ! -z "$STORAGE_DIR" ]; then
    out_dir="$STORAGE_DIR"
elif [ -d "$HOME/.aion" ]; then
    out_dir="$HOME/.aion"
fi

nohup $* > "$out_dir/aion-kernel-output" &
echo $! 
