# Development Server Guide

## Quick Start

```bash
# Start both backend and frontend with compilation
./run_dev_server.sh

# Quick start without recompilation (fastest for small changes)
./run_dev_server.sh --quick

# Full clean build and start (when dependencies change)
./run_dev_server.sh --clean

# Start only backend
./run_dev_server.sh --backend-only

# Start only frontend
./run_dev_server.sh --frontend-only
```

## Access Points

- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080
- **Debug Port**: 5005 (for IDE remote debugging)

## Default Credentials

- Username: `sysadmin@thingsboard.org`
- Password: `sysadmin`

## Hot Reload Behavior

### Frontend (Angular)
- **Automatic**: Changes to TypeScript, HTML, SCSS files reload immediately
- **No action needed**: Just save the file

### Backend (Spring Boot)
- **Without DevTools**: 
  - Run `mvn compile` in another terminal for the changed module
  - Or use `./run_dev_server.sh --compile` to restart with changes
  
- **With DevTools** (if added):
  - IDE auto-compiles → auto-restart in 5-10 seconds
  - Or run `mvn compile` → auto-restart

## Development Workflow

### For Frontend Changes:
1. Start servers: `./run_dev_server.sh --quick`
2. Edit files in `ui-ngx/src/`
3. Changes appear automatically in browser

### For Backend Changes:
1. Start servers: `./run_dev_server.sh`
2. Edit Java files
3. Either:
   - Let IDE compile (if DevTools added)
   - Run `mvn compile -pl <module>` in another terminal
   - Restart with `./run_dev_server.sh --compile`

### For Database Changes:
1. Make changes to entity classes
2. Restart with: `./run_dev_server.sh --compile`
3. Database schema updates automatically (if using dev profile)

## Faster Build Tips

### Instead of `mvn clean install -DskipTests` (slow):

```bash
# For specific module changes
mvn compile -pl common/data -am

# For application module only
mvn compile -pl application

# For installing without tests
mvn install -DskipTests -Dlicense.skip=true

# For really quick iterations (compile only changed files)
mvn compile -pl application -Dincremental=true
```

## Troubleshooting

### Port Already in Use:
```bash
# Kill existing processes
pkill -f ThingsboardServerApplication
pkill -f "ng serve"

# Or find and kill specific ports
lsof -i :8080  # Find backend process
lsof -i :4200  # Find frontend process
kill -9 <PID>
```

### Out of Memory:
```bash
# Increase memory for backend
export MAVEN_OPTS="-Xmx4096m -Xms2048m"

# Increase memory for frontend (already in package.json)
node --max_old_space_size=8048
```

### Logs Location:
- Backend: `/tmp/thingsboard-backend.log`
- Frontend: `/tmp/thingsboard-frontend.log`

### Clean Everything:
```bash
# Full clean
mvn clean
rm -rf ui-ngx/node_modules
rm -rf ui-ngx/target

# Reinstall
cd ui-ngx && yarn install
cd .. && mvn install -DskipTests
```

## IDE Remote Debugging

### IntelliJ IDEA:
1. Run → Edit Configurations
2. Add New → Remote JVM Debug
3. Port: 5005
4. Click Debug after starting dev server

### VS Code:
1. Add to `.vscode/launch.json`:
```json
{
  "type": "java",
  "name": "Attach to ThingsBoard",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

## Performance Tips

1. **Use --quick mode** for frontend-only changes
2. **Compile specific modules** instead of entire project
3. **Enable IDE auto-compile** for seamless backend updates
4. **Use Chrome DevTools** for frontend debugging
5. **Monitor logs** in separate terminal: `tail -f /tmp/thingsboard-*.log`

## Common Tasks

### Add New Dependency:
```bash
# Backend (add to appropriate pom.xml)
mvn clean install -DskipTests

# Frontend
cd ui-ngx
yarn add <package>
```

### Reset Database:
```bash
# Stop servers
./run_dev_server.sh  # Then Ctrl+C

# Drop and recreate (PostgreSQL example)
psql -U postgres -c "DROP DATABASE thingsboard;"
psql -U postgres -c "CREATE DATABASE thingsboard;"

# Restart with clean
./run_dev_server.sh --clean
```

### Switch Branches:
```bash
# Stop servers
# Stash or commit changes
git checkout <branch>

# Clean and rebuild
mvn clean
cd ui-ngx && yarn install
cd ..
./run_dev_server.sh --clean
```