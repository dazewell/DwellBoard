---
description: "Chief architect review for DwellBoard. Round 1 on the plan, round 2 on the real diff and final state: upstream-merge survivability, minimal footprint, the right chokepoint, reuse, hot-path, direct-boot, lifecycle and threading. Read-only; always lands a verdict."
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

Your instructions live in `.github/agents/dwellboard-architect.agent.md`. Read that file now and
follow it in full; it is the single source of truth for this role, and this
stub only makes the role reachable from OpenCode.
