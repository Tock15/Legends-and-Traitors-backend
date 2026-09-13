# Git Workflow & Pull Request Rules

## 1. Branch Strategy

- **Development Branch**: `dev` is the integration trunk for all sprint work. `main` is production-ready releases only.
- **Feature/Chore Branches**: Branch off latest `origin/dev`.
- **Branch Naming**: `<type>/LT-<id>/<short-description>`
  - `feat/LT-25/Room-code-generator-service`
  - `chore/LT-63/Setup-AGENT-md-and-antigravity`
  - `feat/LT-26/Implement-room-entity`

## 2. Commit Standards

- **Commit Messages**: Follow repo standard `<type>: LT-<id>/<Title>`:
  - `feat: LT-25/Add room code generator service`
  - `chore: LT-63/Setup AGENT.md and antigravity`
  - `chore: LT-57 CLAUDE.md setup`

## 3. Rebase & PR Hygiene

- **Linear History**: Never merge `dev` into your feature branch via a merge commit.
- **Mandatory Rebase**: Before pushing or creating a PR, rebase cleanly onto `origin/dev`:
  ```powershell
  git fetch origin dev
  git rebase origin/dev
  ```
- **PR Target**: Always target `dev` for pull requests.
- **Clean Diffs**: Verify `git diff origin/dev...HEAD` contains only files directly within the scope of the assigned ticket.
