# infrastructure

The AWS deployment, as two stacks per account.

- `data-tier.yaml`, in this directory, is deployed once per account and rarely changes: the
  security groups, the Aurora PostgreSQL Serverless v2 cluster with a writer and a reader, and
  RDS Proxy with a read-only endpoint. Storage and the master secret are encrypted with the
  AWS-managed keys. It publishes what the application needs under `/NVA/UAC/...` in SSM,
  following the house convention. The VPC and subnets are inputs (D14).

  Fixed at the first deployment, because a change replaces the cluster and starts from an empty
  database: `DatabaseName`, `MasterUsername`, the cluster identifier derived from `Environment`,
  and the subnet group. `UpdateReplacePolicy: Snapshot` keeps a snapshot of the old cluster if
  that happens by mistake, but the data has to be restored by hand.

  The proxy requires IAM authentication. Every caller, the application and the migration step
  alike, connects with an IAM authentication token, never with the master password.
  The data tier publishes a managed policy granting `rds-db:connect` on the proxy under
  `/NVA/UAC/Database/MigrationConnectPolicyArn`, which the migration project's role attaches.

- `template.yaml`, at the repository root where the pipeline and the shared workflow expect it,
  is the SAM application stack: the Lambda function, the HTTP API with its JWT authoriser, the
  API mapping on the shared domain, the alarms, and the CodeBuild projects that migrate and
  rehearse inside the VPC. The error-rate alarm counts the 5xx responses clients receive from
  the API, since the Quarkus adapter turns an exception into a 500 response that Lambda counts
  as a successful invocation. The latency alarm reads the API's latency for the same reason: the
  function's duration leaves out SnapStart restores and the gateway itself.

The shared domain is an API Gateway custom domain defined in `nva-common-resources`, and it is
edge-optimized. An HTTP API can only be mapped onto a Regional custom domain, so `ApiMapping`
cannot attach to the shared domain as it stands (PLAN.md section 11); AWS WAF does not support
HTTP APIs either, should this API ever need it. How to settle it, a REST API, a domain of its own
or CloudFront in front, is decided before the first deploy and recorded as an ADR.

`./gradlew lintInfrastructure` runs `sam validate --lint` on both, as the pipeline does; it
needs the SAM CLI, which the shared GitHub workflow does not have, so it is not part of
`check`. The shared workflow lints `template.yaml` with cfn-lint itself.

The function logs in to the proxy with an IAM authentication token from the Quarkiverse Amazon
RDS credentials provider, configured in `app-lambda`. Known follow-up before Phase 1: the function
and the migration connect as the master user, which must become least-privilege database users.

## Pipeline

Delivery follows the other NVA services (ADR 0005). The pipeline itself is created by
`NVA-infrastructure`, once per account, from `pipelines_master_template.yml`; registering this
repository there is the step outside this repository. Its stages are fixed:

1. **Source** pulls `main`.
2. **Build** runs `buildspec.yaml` in a CodeBuild project with Docker, because the tests start
   PostgreSQL: the Gradle build with every gate, the template lints, `sam package`, and an upload
   of the sources to the artefact bucket for the projects below, under a key of their own per
   commit, since the bucket expires overwritten versions after a day. The artefact is
   `packaged.yaml`, with the bucket and that key substituted in.
3. **Deploy** updates the stack with CloudFormation, after a manual approval where the account
   asks for one. The `Migration` custom resource starts the migration project with the uploaded
   sources, and the function waits for it, so the schema is current before a new version takes
   traffic. Deleting a branch stack drops its schema the same way; the `public` schema is never
   dropped.
4. **CopyDocs** copies OpenAPI documents from `docs/`; this repository keeps its contract in
   `adapter-http` for now.

The projects the stack defines, both inside the data tier's VPC and connecting as the master
user with an IAM token through the proxy:

- `<stack>-migration` runs `buildspec.migrate.yml`: `flywayMigrate`, or `flywayClean` of a
  branch schema when `DROP_SCHEMA` is `true`. The build answers CloudFormation itself when the
  custom resource started it.
- `<stack>-rehearsal` runs `buildspec.rehearse.yml` on demand, after the extract has filled the
  staging schema, and keeps `rehearsal-units.md` as its artefact under `nva-uac/rehearsals/` in
  the artefact bucket:

  ```
  aws codebuild start-build --project-name <stack>-rehearsal
  ```

Before the first deploy in an account, the deploy role in `NVA-infrastructure` must be allowed to
create, update, delete and read CodeBuild projects; today it may only manage report groups.

`.github/workflows/build.yml` runs the shared BIBSYSDEV Java workflow on pull requests and
`main`, with Java 25; the OpenAPI lint is left to the Gradle build, since the contract does not
live in `docs/`. Its cfn-lint job lints the templates listed in `.cfnlintrc.yaml`, which is also
what a plain `cfn-lint` in the repository root does.

## Branch stacks

A change that needs to be seen running before review is deployed by hand to the dev account,
against the shared data tier, with a schema of its own. Upload the sources as the Build stage
does, then deploy with the schema named after the branch, prefixed so it can never be `public`:

```
zip --quiet --recurse-paths migration-source.zip . --exclude '.git/*' 'build/*' '*/build/*' '.gradle/*' '*/.gradle/*'
KEY="nva-uac/migration-source-$(git rev-parse HEAD).zip"
aws s3api put-object --bucket "$BUCKET" --key "$KEY" --body migration-source.zip > /dev/null
./gradlew :app-lambda:build -x test
sam deploy --stack-name nva-uac-branch-<slug> --s3-bucket "$BUCKET" --capabilities CAPABILITY_IAM \
  --parameter-overrides CustomDomainBasePath=uac-<slug> SchemaName=branch_<slug> \
  MigrationSourceBucket="$BUCKET" MigrationSourceKey="$KEY"
sam delete --stack-name nva-uac-branch-<slug>
```
