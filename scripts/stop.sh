#!/bin/bash
# ================================================================
# MLEAProxy - Stop Script
# ================================================================
# Stops running MLEAProxy instance
# ================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0;m'

# Check if PID file exists
if [ -f "mleaproxy.pid" ]; then
    PID=$(cat mleaproxy.pid)

    # Check if process is actually running
    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${YELLOW}Stopping MLEAProxy (PID: $PID)...${NC}"
        kill $PID

        # Wait for shutdown (max 10 seconds)
        for i in {1..10}; do
            if ! ps -p $PID > /dev/null 2>&1; then
                echo -e "${GREEN}MLEAProxy stopped successfully${NC}"
                rm -f mleaproxy.pid
                exit 0
            fi
            sleep 1
        done

        # Force kill if still running
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "${YELLOW}Process not responding, forcing shutdown...${NC}"
            kill -9 $PID
            rm -f mleaproxy.pid
            echo -e "${GREEN}MLEAProxy force stopped${NC}"
        fi
    else
        echo -e "${RED}PID file exists but process is not running${NC}"
        rm -f mleaproxy.pid
    fi
else
    # Try to find by process name
    PID=$(pgrep -f "mlesproxy.*jar")

    if [ -n "$PID" ]; then
        echo -e "${YELLOW}Found MLEAProxy process (PID: $PID)${NC}"
        kill $PID
        sleep 2

        if ! ps -p $PID > /dev/null 2>&1; then
            echo -e "${GREEN}MLEAProxy stopped successfully${NC}"
        else
            kill -9 $PID
            echo -e "${GREEN}MLEAProxy force stopped${NC}"
        fi
    else
        echo -e "${RED}MLEAProxy is not running${NC}"
        exit 1
    fi
fi
