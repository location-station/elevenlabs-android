# One-time issue import (GitHub Actions)

1. Copy these files into your repo (keep or delete after use):
   - `.github/workflows/import-issues.yml`
   - `scripts/import_issues_from_csv.py`

2. Commit & push. In GitHub → **Actions**, run **Import Sprint CSV to Issues** with inputs:
   - `csv_path`: absolute path within repo (e.g., `/data/sprint2_core_tasks.csv`)
   - `milestone`: `Sprint 2 — Core features` (or any name; the workflow will create it if missing)
   - `assignee`: *(optional)* your GitHub username
   - `dry_run`: run with `true` first to preview

3. If the preview looks good, re-run with `dry_run=false` to create issues.

Notes:
- The importer auto-creates the milestone and labels (`Type`, `Priority`, plus anything in `Labels`).
- Two-pass import: creates issues, then appends **Dependencies** and **Subtasks** cross-links in bodies.
- Uses built-in `GITHUB_TOKEN` with `issues:write` permissions.

Security tip: If you only need it once, delete the workflow after use or restrict who can run it.
