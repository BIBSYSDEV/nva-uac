# infrastructure

The AWS deployment, as two stacks per account.

- `data-tier.yaml` is deployed once per account and rarely changes: the security groups, the
  Aurora PostgreSQL Serverless v2 cluster with a writer and a reader, and RDS Proxy with a
  read-only endpoint. Storage and the master secret are encrypted with the AWS-managed keys. It
  publishes what the application needs under `/NVA/UAC/...` in SSM, following the house
  convention. The VPC and subnets are inputs (D14).

  Fixed at the first deployment, because a change replaces the cluster and starts from an empty
  database: `DatabaseName`, `MasterUsername`, the cluster identifier derived from `Environment`,
  and the subnet group. `UpdateReplacePolicy: Snapshot` keeps a snapshot of the old cluster if
  that happens by mistake, but the data has to be restored by hand.

  The proxy requires IAM authentication. Every caller, the application and the migration step
  alike, connects with a token from `rds generate-db-auth-token`, never with the master password.
  The data tier publishes a managed policy granting `rds-db:connect` on the proxy under
  `/NVA/UAC/Database/MigrationConnectPolicyArn`, for the migration CodeBuild project's role,
  which is defined with the pipeline outside this repository.
- `template.yaml` is the SAM application stack: the Lambda function, the HTTP API with its JWT
  authoriser, the API mapping on the shared domain, and the alarms. Branch stacks deploy this
  one against the shared data tier of the dev account, each with its own schema. The error-rate
  alarm counts the 5xx responses clients receive from the API, since the Quarkus adapter turns
  an exception into a 500 response that Lambda counts as a successful invocation.

The edge in front of the shared domain, CloudFront and WAF, is not part of either stack: it is
the platform's shared edge, where every NVA API sits behind the same distribution and rules.
Whether this API needs anything of its own there is a Phase 1 decision, to be taken with the
platform team.

`./gradlew lintInfrastructure` runs `sam validate --lint` on both, as the pipeline does;
it needs the SAM CLI, which the shared GitHub workflow does not have, so it is not part of
`check`. Deploying needs an account; see the pipeline for the commands.

Known follow-ups before Phase 1: the application connects through the proxy as the master user,
which must become a least-privilege database user with its own secret; and the proxy's IAM
authentication needs the Quarkiverse Amazon RDS credentials provider on the application side.

## Pipeline

- `buildspec.yml` builds and tests everything, lints the templates and packages the SAM
  application; the artefact is `packaged.yaml`. The CodeBuild project needs privileged mode,
  because the tests start PostgreSQL in Docker.
- `buildspec.migrate.yml` applies the migrations, or drops a branch schema, from a CodeBuild
  project that runs inside the data tier's VPC. The pipeline runs it before the function alias
  is shifted. `scripts/run-codebuild.sh` starts such a project and waits for it.
- `.github/workflows/build.yml` runs the shared BIBSYSDEV Java workflow on pull requests and
  `main`, with Java 25.
- `.github/workflows/branch-stack.yml` deploys every branch as its own stack in the dev account
  with a schema of its own on the shared cluster, smoke-tests `/q/health`, and removes both. The
  deploy role, region, artefact bucket and migration project are repository variables.

The pipeline itself, CodePipeline with these projects, is defined with the other NVA pipelines
outside this repository.
