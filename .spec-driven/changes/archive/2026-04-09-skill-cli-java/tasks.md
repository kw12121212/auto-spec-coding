# Tasks: skill-cli-java

## Implementation

- [x] Create a Java CLI entrypoint and command dispatcher for the 12-command spec-driven workflow surface
- [x] Implement filesystem-scaffolding and change-lifecycle commands: `init`, `propose`, `modify`, `list`, `cancel`, and `archive`
- [x] Implement validation and roadmap-reporting commands: `verify`, `verify-roadmap`, `roadmap-status`, `apply`, `run-maintenance`, and `migrate`, preserving the existing repo-local artifact contracts and expected console/JSON outputs
- [x] Add unit tests covering command parsing, usage failures, scaffold generation, active-vs-archived change listing, and roadmap JSON reporting
- [x] Update repo-local documentation or invocation wiring so the Java CLI can be run without Node.js

## Testing

- [x] Run validation command `mvn compile -q`
- [x] Run unit test command `mvn test -q -Dsurefire.useFile=false`

## Verification

- [x] Compare representative Java CLI results against the current helper behavior for `init`, `propose`, `list`, `verify-roadmap`, and `roadmap-status`
- [x] Verify implementation matches proposal and stays within the `skill-cli-java` milestone scope
