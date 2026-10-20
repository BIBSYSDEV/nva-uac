# infrastructure

The AWS deployment, as two stacks per account.

- `data-tier.yaml` is deployed once per account and rarely changes: the KMS key, the security
  groups, the Aurora PostgreSQL Serverless v2 cluster with a writer and a reader, and RDS Proxy
  with a read-only endpoint. It publishes what the application needs under `/NVA/UAC/...` in SSM,
  following the house convention. The VPC and subnets are inputs (D14).
- `template.yaml` is the SAM application stack: the Lambda function, the HTTP API with its JWT
  authoriser, the API mapping on the shared domain, and the alarms. Branch stacks deploy this
  one against the shared data tier of the dev account, each with its own schema.

`./gradlew lintInfrastructure` runs `sam validate --lint` on both, as the pipeline does;
it needs the SAM CLI, which the shared GitHub workflow does not have, so it is not part of
`check`. Deploying needs an account; see the pipeline for the commands.

Known follow-ups before Phase 1: the application connects through the proxy as the master user,
which must become a least-privilege database user with its own secret; and the proxy's IAM
authentication needs the Quarkiverse Amazon RDS credentials provider on the application side.
