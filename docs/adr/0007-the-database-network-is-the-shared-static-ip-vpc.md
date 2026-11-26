# ADR 0007: The database network is in the shared static-IP VPC

Date: 2026-11-24

Status: Accepted

## Context

D14 puts the function, RDS Proxy and Aurora in a VPC and leaves it to Phase 0 to find out whether
the NVA accounts already have a shared VPC and subnets that nva-uac can use; if not, nva-uac owns
a network stack with private subnets and VPC endpoints. Aurora needs subnets in at least two
availability zones. The function needs a way out of the VPC to fetch the user pool's signing keys,
and the migration project needs the same to download its build dependencies and to reach S3, SSM
and CloudFormation.

The NVA templates define one network besides each account's default VPC: the static-IP VPC in
`nva-common-resources`, which gives the handle service a fixed outbound address through a NAT
gateway. It has one public and one private subnet, neither with an availability zone set, so the
zone differs between accounts. The Neptune network that section 11 of the plan mentions is not
defined in any template.

The alternatives considered:

- Sharing the existing private subnet and NAT gateway: one subnet cannot carry Aurora, and
  nva-uac's outbound traffic would leave from the handle service's fixed address.
- VPC endpoints instead of a NAT gateway, in this VPC or one of nva-uac's own: the migration
  downloads its build dependencies from the internet, and AWS PrivateLink does not serve a user
  pool that has a domain, so a NAT gateway is needed anyway and the endpoints would cost about as
  much on top.
- A network of nva-uac's own with a NAT gateway: the same result as the decision below, with a
  second VPC to run.

## Decision

nva-uac gets a network of its own inside the static-IP VPC. `nva-common-resources` gains two
private subnets in fixed availability zones, a route table of their own, and a NAT gateway with
its own address in the existing public subnet, all published in SSM. The existing subnets and the
handle service's NAT gateway stay as they are. The data tier reads the VPC and the two subnets
from SSM and places the writer and the reader in one zone each. This record supersedes the clause
of ADR 0001 that leaves D14 to the Phase 0 record.

## Consequences

- One additive change in `nva-common-resources`, deployed in every environment before nva-uac's
  data tier; the handle service is unaffected.
- nva-uac's outbound traffic leaves from an address of its own, apart from the handle service's.
- Every environment pays for one more NAT gateway and for nva-uac's traffic through it.
- The NAT gateway sits in one zone. If that zone fails, new function instances cannot fetch the
  signing keys and deploys fail until it returns; running instances carry on, and the database
  fails over to the other zone.
- No VPC endpoints are needed to begin with.
