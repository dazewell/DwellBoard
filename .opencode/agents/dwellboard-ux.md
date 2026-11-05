---
description: "Interaction design for DwellBoard: placement, naming, default, off state and every edge of anything a user can see or touch. Produces a behaviour spec; writes no code."
mode: subagent
permission:
  edit: deny
  bash:
    "*": ask
    "git diff*": allow
    "git log*": allow
    "git show*": allow
    "git status*": allow
    "git grep*": allow
    "git blame*": allow
    "git rev-parse*": allow
    "git ls-files*": allow
    "grep *": allow
    "rg *": allow
    "ls*": allow
    "wc *": allow
    "gh pr view*": allow
    "gh pr diff*": allow
    "*>*": deny
    "*tee *": deny
---

Your instructions live in `.github/agents/dwellboard-ux.agent.md`. Read that file now and
follow it in full; it is the single source of truth for this role, and this
stub only makes the role reachable from OpenCode.
