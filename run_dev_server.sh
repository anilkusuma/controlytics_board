#!/bin/bash
#
# Copyright © 2016-2024 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# ThingsBoard Development Server Script
# This script starts both backend and frontend servers with hot reload capabilities

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BACKEND_LOG="/tmp/thingsboard-backend.log"
FRONTEND_LOG="/tmp/thingsboard-frontend.log"

# Process IDs
BACKEND_PID=""
FRONTEND_PID=""

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_message "$BLUE" "Checking prerequisites..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_message "$RED" "❌ Java is not installed"
        exit 1
    fi
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$java_version" -lt 11 ] && [ "$java_version" -gt 1 ]; then
        print_message "$RED" "❌ Java 11 or higher is required"
        exit 1
    fi
    print_message "$GREEN" "✓ Java $(java -version 2>&1 | head -n 1)"
    
    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_message "$RED" "❌ Maven is not installed"
        exit 1
    fi
    print_message "$GREEN" "✓ Maven $(mvn -version | head -n 1)"
    
    # Check Node
    if ! command -v node &> /dev/null; then
        print_message "$RED" "❌ Node.js is not installed"
        exit 1
    fi
    print_message "$GREEN" "✓ Node $(node --version)"
    
    # Check Yarn
    if ! command -v yarn &> /dev/null; then
        print_message "$YELLOW" "⚠️  Yarn is not installed globally, checking local installation..."
        if [ ! -f "$SCRIPT_DIR/ui-ngx/node_modules/.bin/yarn" ]; then
            print_message "$RED" "❌ Yarn is not installed"
            exit 1
        fi
    fi
    print_message "$GREEN" "✓ Yarn installed"
}

# Function to kill processes on exit
cleanup() {
    print_message "$YELLOW" "\nShutting down servers..."
    
    if [ ! -z "$BACKEND_PID" ]; then
        print_message "$YELLOW" "Stopping backend server (PID: $BACKEND_PID)..."
        kill -TERM $BACKEND_PID 2>/dev/null
        wait $BACKEND_PID 2>/dev/null
    fi
    
    if [ ! -z "$FRONTEND_PID" ]; then
        print_message "$YELLOW" "Stopping frontend server (PID: $FRONTEND_PID)..."
        kill -TERM $FRONTEND_PID 2>/dev/null
        wait $FRONTEND_PID 2>/dev/null
    fi
    
    # Also kill any remaining Java processes running ThingsBoard
    pkill -f "ThingsboardServerApplication" 2>/dev/null
    
    # Kill any remaining ng serve processes
    pkill -f "ng serve" 2>/dev/null
    
    print_message "$GREEN" "✓ Servers stopped"
    exit 0
}

# Set up trap to cleanup on script exit
trap cleanup EXIT INT TERM

# Function to compile backend
compile_backend() {
    print_message "$BLUE" "Compiling backend modules..."
    cd "$SCRIPT_DIR"
    
    # First ensure UI is built if needed
    if [ ! -d "ui-ngx/target" ]; then
        print_message "$YELLOW" "Building UI module first..."
        cd "$SCRIPT_DIR/ui-ngx"
        yarn install
        yarn build:prod
        cd "$SCRIPT_DIR"
    fi
    
    # Build only the application and its dependencies
    mvn install -pl application -am -DskipTests
    
    if [ $? -ne 0 ]; then
        print_message "$RED" "❌ Backend compilation failed"
        exit 1
    fi
    print_message "$GREEN" "✓ Backend compiled successfully"
}

# Function to start backend server
start_backend() {
    print_message "$BLUE" "Starting backend server..."
    cd "$SCRIPT_DIR/application"
    
    # Check if the application JAR exists
    if [ ! -f "target/application-3.7.0.jar" ]; then
        print_message "$YELLOW" "Application JAR not found. Building application module..."
        cd "$SCRIPT_DIR"
        mvn clean install -pl application -am -DskipTests
        if [ $? -ne 0 ]; then
            print_message "$RED" "❌ Failed to build application"
            exit 1
        fi
        cd "$SCRIPT_DIR/application"
    fi
    
    # Start the backend using java -jar for better reliability
    java -Xmx2048m -Xms1024m \
        -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 \
        -Dspring.profiles.active=dev \
        -jar target/application-3.7.0.jar > "$BACKEND_LOG" 2>&1 &
    
    BACKEND_PID=$!
    
    # Wait for backend to start
    print_message "$YELLOW" "Waiting for backend to start (checking log: $BACKEND_LOG)..."
    
    for i in {1..60}; do
        if grep -q "Started ThingsBoard" "$BACKEND_LOG" 2>/dev/null; then
            print_message "$GREEN" "✓ Backend server started (PID: $BACKEND_PID)"
            print_message "$GREEN" "  Access backend at: http://localhost:8080"
            print_message "$GREEN" "  Debug port: 5005"
            return 0
        fi
        sleep 2
        echo -n "."
    done
    
    print_message "$RED" "❌ Backend failed to start in 120 seconds"
    print_message "$RED" "Check log: $BACKEND_LOG"
    tail -20 "$BACKEND_LOG"
    exit 1
}

# Function to start frontend server
start_frontend() {
    print_message "$BLUE" "Starting frontend server..."
    cd "$SCRIPT_DIR/ui-ngx"
    
    # Install dependencies if needed
    if [ ! -d "node_modules" ]; then
        print_message "$YELLOW" "Installing frontend dependencies..."
        yarn install
    fi
    
    # Start the frontend development server
    yarn start > "$FRONTEND_LOG" 2>&1 &
    FRONTEND_PID=$!
    
    # Wait for frontend to start
    print_message "$YELLOW" "Waiting for frontend to start..."
    
    for i in {1..30}; do
        if grep -q "Angular Live Development Server" "$FRONTEND_LOG" 2>/dev/null || \
           grep -q "webpack compiled" "$FRONTEND_LOG" 2>/dev/null; then
            print_message "$GREEN" "✓ Frontend server started (PID: $FRONTEND_PID)"
            print_message "$GREEN" "  Access frontend at: http://localhost:4200"
            return 0
        fi
        sleep 2
        echo -n "."
    done
    
    print_message "$RED" "❌ Frontend failed to start in 60 seconds"
    print_message "$RED" "Check log: $FRONTEND_LOG"
    tail -20 "$FRONTEND_LOG"
    exit 1
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --quick, -q       Quick start without recompiling (fastest)"
    echo "  --compile, -c     Compile changes before starting (default)"
    echo "  --clean, -C       Clean build before starting (slowest)"
    echo "  --backend-only    Start only the backend server"
    echo "  --frontend-only   Start only the frontend server"
    echo "  --help, -h        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0              # Start with compilation"
    echo "  $0 --quick      # Quick start for minor changes"
    echo "  $0 --clean      # Full rebuild and start"
}

# Parse command line arguments
MODE="compile"
START_BACKEND=true
START_FRONTEND=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --quick|-q)
            MODE="quick"
            shift
            ;;
        --compile|-c)
            MODE="compile"
            shift
            ;;
        --clean|-C)
            MODE="clean"
            shift
            ;;
        --backend-only)
            START_FRONTEND=false
            shift
            ;;
        --frontend-only)
            START_BACKEND=false
            shift
            ;;
        --help|-h)
            show_usage
            exit 0
            ;;
        *)
            print_message "$RED" "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
print_message "$GREEN" "========================================="
print_message "$GREEN" "   ThingsBoard Development Server"
print_message "$GREEN" "========================================="

# Check prerequisites
check_prerequisites

# Change to project directory
cd "$SCRIPT_DIR"

# Execute based on mode
case $MODE in
    quick)
        print_message "$YELLOW" "Mode: Quick start (no compilation)"
        ;;
    compile)
        print_message "$YELLOW" "Mode: Compile and start"
        if [ "$START_BACKEND" = true ]; then
            compile_backend
        fi
        ;;
    clean)
        print_message "$YELLOW" "Mode: Clean build and start"
        if [ "$START_BACKEND" = true ]; then
            print_message "$BLUE" "Performing clean build..."
            mvn clean install -DskipTests
            if [ $? -ne 0 ]; then
                print_message "$RED" "❌ Clean build failed"
                exit 1
            fi
            print_message "$GREEN" "✓ Clean build completed"
        fi
        ;;
esac

# Start servers
if [ "$START_BACKEND" = true ]; then
    start_backend
fi

if [ "$START_FRONTEND" = true ]; then
    start_frontend
fi

# Show status
print_message "$GREEN" "========================================="
print_message "$GREEN" "✓ All servers started successfully!"
print_message "$GREEN" "========================================="
if [ "$START_BACKEND" = true ]; then
    print_message "$BLUE" "Backend:  http://localhost:8080"
    print_message "$BLUE" "          Debug port: 5005"
    print_message "$BLUE" "          Log: $BACKEND_LOG"
fi
if [ "$START_FRONTEND" = true ]; then
    print_message "$BLUE" "Frontend: http://localhost:4200"
    print_message "$BLUE" "          Log: $FRONTEND_LOG"
fi
print_message "$GREEN" "========================================="
print_message "$YELLOW" "Press Ctrl+C to stop all servers"
print_message "$GREEN" "========================================="

# Monitor logs
print_message "$YELLOW" "\nShowing combined logs (Backend in BLUE, Frontend in GREEN):"
print_message "$YELLOW" "========================================="

# Keep the script running and show logs
while true; do
    # Show last lines from backend log
    if [ "$START_BACKEND" = true ] && [ -f "$BACKEND_LOG" ]; then
        tail -f "$BACKEND_LOG" 2>/dev/null | sed "s/^/[BACKEND] /" &
    fi
    
    # Show last lines from frontend log  
    if [ "$START_FRONTEND" = true ] && [ -f "$FRONTEND_LOG" ]; then
        tail -f "$FRONTEND_LOG" 2>/dev/null | sed "s/^/[FRONTEND] /" &
    fi
    
    # Wait for interrupt
    wait
done