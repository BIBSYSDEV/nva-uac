# app-lambda

The application as an AWS Lambda function: the `assembly` behind the Amazon Lambda HTTP
extension, which turns API Gateway HTTP API (v2) events into ordinary requests for the HTTP
adapter. SnapStart is enabled so the JVM is restored from a snapshot instead of started.

`./gradlew :app-lambda:build` produces `build/function.zip`. The SAM function uses it with the
handler `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest` on the `java25`
runtime. Configuration that differs per environment, the OIDC issuer and the data source, is
supplied by the deployment as environment variables.

The test sends requests through the mock event server the extension provides in tests, so the
event conversion is exercised, against a PostgreSQL started by Dev Services.
