#!/bin/sh
# Example setenv.sh for Tomcat deployments (copy to $CATALINA_BASE/bin/setenv.sh and make executable)

JAVA_OPTS="-Xms512m -Xmx1g \
  -Dspring.profiles.active=prod \
  -Dreloader.use-h2-external=true \
  -Dexternal.db.allow-writes=true \
  -Dexternal.h2.path=/opt/reloader/external-h2-db"
export JAVA_OPTS

CATALINA_OPTS="-Djava.security.egd=file:/dev/./urandom"
export CATALINA_OPTS
