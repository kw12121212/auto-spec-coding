# Tasks: standalone-runtime-jar

## Implementation

- [x] Update the release packaging spec delta for the standalone packaged-runtime jar behavior
- [x] Add Maven packaging changes for a self-contained executable runtime jar
- [x] Add packaged-resource fallback for bundled Sandlock runtime assets when repository-relative files are absent
- [x] Update README packaged-runtime instructions to use the standalone jar

## Testing

- [x] Run validation command `mvn -q -DskipBuiltinToolsDownload=true -Dtest=LealonePlatformTest test`
- [x] Run unit test command `mvn -q -DskipBuiltinToolsDownload=true test`

## Verification

- [x] Run `mvn -q -DskipBuiltinToolsDownload=true -DskipTests package`
- [x] Verify `java -jar target/*-standalone.jar list` succeeds without an external classpath
