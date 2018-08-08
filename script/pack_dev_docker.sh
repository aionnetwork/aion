#!/bin/bash

# The dev build requires the binary to be build with ant. If you need to remote debug, add the `-Dcompile.debug=true` flag.
ant pack_build -Dcompile.debug=true

docker-compose -f supporting-services.yml build

