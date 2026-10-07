# Path-scoped instructions

Files named `<area>.instructions.md` in this folder give Copilot rules for part of the codebase. Each starts with front matter saying which files it applies to:

```markdown
---
applyTo: "src/main/java/**/repository/**"
---

# data-access rules

- **Accessing the database from a service class:** Use the matching *Repository class; raw SQL is only allowed under */repository/*.
```

Promoted lessons arrive here through pull requests labelled `agentic-lesson-promotion`, opened by `agentic-bot`. Review them like any other change: merge to make the rule permanent, close to reject it.
