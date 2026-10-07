# Definition of Agent-Ready

A ticket can carry the `Agentic AI Approved` label only when every item below is true. The orchestrator checks most of them automatically and moves the ticket to `Blocked (Agent)` with the reasons if not.

## Checklist

- [ ] **Acceptance criteria** are a bullet list of testable statements, in the AC field or under an "Acceptance Criteria" heading.
- [ ] **Target repo** is set to `owner/repo` and the repo is onboarded.
- [ ] **Story points ≤ 5.** Larger work is split first.
- [ ] **Single repo.** Nothing labelled or componentised `multi-repo`.
- [ ] **No infrastructure, migration, CI or secrets work.**
- [ ] **Expected values or examples** are given wherever behaviour changes ("70% discount → capped at 50%").
- [ ] **Relevant specs or ADRs** are linked from the ticket.

## Good ticket

> **Cap discounts at 50%**
>
> Orders can currently receive discounts above 50% when multiple vouchers stack.
>
> **Acceptance Criteria**
> - A single order's total discount never exceeds 50% of the pre-discount total.
> - Stacking a 30% and a 40% voucher on a £100 order gives a £50 total.
> - The cap is applied before tax is calculated.
> - Existing single-voucher behaviour is unchanged.
>
> Target repo: `acme/payments` · Story points: 3 · Linked: ADR-021 Pricing rules

## Bad ticket

> **Improve discount handling**
>
> Discounts are sometimes wrong, please fix and make it better.

Why it fails: no testable criteria, vague verbs ("improve", "make it better"), no expected values, no scope boundary.
