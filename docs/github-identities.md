# GitHub identities

Two identities act on GitHub. Each action uses exactly one of them.

| Identity | Credential | Used by | Actions |
|---|---|---|---|
| GitHub App `agentic-bot` | Installation token (JWT exchange, cached until 5 min before expiry) | `GitHubClient` | Create issues, comment status, read PRs/files/checks/reviews, mark PR ready, request reviewers, close agent PRs, open lesson-promotion PRs |
| Service user (e.g. `agentic-svc`) with a Copilot seat | Fine-grained PAT | `CopilotClient` only | Assign Copilot to an issue, post `@copilot` instructions |

## Why two identities

- Copilot coding agent sessions are billed to, and attributed to, the user who assigns Copilot. A GitHub App has no Copilot seat.
- Copilot only reacts to `@copilot` mentions from users with write access; bot authors are ignored.
- Everything else uses the App so permissions stay narrow and auditable.

## What neither identity does

- No code path merges a PR. `GitHubClient` has no merge method, and a unit test (`GitHubClientTest.hasNoMergeCapability`) fails if one is added.
- Branch protection requires an approving human review; neither identity can approve its own PR.

## Assigning Copilot

`CopilotClient.assignCopilot` uses GraphQL:

1. `repository.suggestedActors(capabilities: [CAN_BE_ASSIGNED])` → find the node with login `copilot-swe-agent` and take its `id`.
2. `replaceActorsForAssignable(input: {assignableId: <issue id>, actorIds: [<bot id>]})`.

If `copilot-swe-agent` is not among the suggested actors, `CopilotUnavailableException` is thrown (coding agent disabled for the repo, or the service user has no seat).

Verify this call against the current GitHub documentation ("Assigning an issue to Copilot via the API") during the Phase 0 exit test and record any change here.

## PAT rotation

1. Create a new fine-grained PAT for the service user with the same repository access and permissions (Issues R/W, Pull requests R/W, Contents Read).
2. `aws secretsmanager put-secret-value --secret-id agentic/github-service-user --secret-string '{"login":"agentic-svc","token":"<new>"}'`
3. Wait 5 minutes (secret cache TTL), confirm a run assigns Copilot, then revoke the old PAT.
