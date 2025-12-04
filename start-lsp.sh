#!/bin/bash
java -Dxtext.disable.standalone.setup=true \
  -cp target/classes:$(cat classpath.txt) \
  org.eclipse.xtext.ide.server.ServerLauncher
