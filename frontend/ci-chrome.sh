#!/usr/bin/env bash
# Wrapper to launch Chromium with flags suitable for headless CI environments
exec "$(node -e "console.log(require('puppeteer').executablePath())")" --no-sandbox --disable-dev-shm-usage --headless=new --remote-debugging-port=0 "$@"
