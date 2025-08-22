#!/usr/bin/env python3
import os, sys, csv, argparse, json, re
import requests
from collections import defaultdict

API = "https://api.github.com"

def gh_headers(token):
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
    }

def ensure_milestone(owner, repo, name, token):
    r = requests.get(f"{API}/repos/{owner}/{repo}/milestones?state=all&per_page=100", headers=gh_headers(token))
    r.raise_for_status()
    for m in r.json():
        if m["title"] == name:
            return m["number"]
    r = requests.post(f"{API}/repos/{owner}/{repo}/milestones",
                      headers=gh_headers(token),
                      data=json.dumps({"title": name, "state": "open"}))
    r.raise_for_status()
    return r.json()["number"]

def list_labels(owner, repo, token):
    labels = {}
    page = 1
    while True:
        r = requests.get(f"{API}/repos/{owner}/{repo}/labels?per_page=100&page={page}", headers=gh_headers(token))
        r.raise_for_status()
        data = r.json()
        if not data: break
        for l in data:
            labels[l["name"]] = l["color"]
        page += 1
    return labels

def ensure_labels(owner, repo, token, wanted):
    existing = list_labels(owner, repo, token)
    to_create = [w for w in wanted if w and w not in existing]
    for name in to_create:
        color = "ededed"
        try:
            requests.post(f"{API}/repos/{owner}/{repo}/labels",
                          headers=gh_headers(token),
                          data=json.dumps({"name": name, "color": color})).raise_for_status()
        except Exception as e:
            print(f"WARNING: could not create label '{name}': {e}", file=sys.stderr)

def create_issue(owner, repo, token, title, body, labels, milestone_number, assignees):
    payload = {"title": title, "body": body}
    if labels: payload["labels"] = labels
    if milestone_number: payload["milestone"] = milestone_number
    if assignees: payload["assignees"] = assignees
    r = requests.post(f"{API}/repos/{owner}/{repo}/issues", headers=gh_headers(token), data=json.dumps(payload))
    r.raise_for_status()
    return r.json()

def update_issue(owner, repo, token, number, **fields):
    r = requests.patch(f"{API}/repos/{owner}/{repo}/issues/{number}", headers=gh_headers(token), data=json.dumps(fields))
    r.raise_for_status()
    return r.json()

def parse_csv(path):
    with open(path, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        rows = [ {k: (v or '').strip() for k,v in row.items()} for row in reader ]
    return rows

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True, help="Absolute path to CSV file")
    parser.add_argument("--milestone", required=True, help="Milestone name")
    parser.add_argument("--assignee", default="", help="Optional GitHub username")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    repo_full = os.environ.get("GITHUB_REPOSITORY")
    token = os.environ.get("GITHUB_TOKEN")
    if not repo_full or not token:
        print("GITHUB_REPOSITORY and GITHUB_TOKEN must be set", file=sys.stderr)
        sys.exit(1)
    owner, repo = repo_full.split("/", 1)

    rows = parse_csv(args.csv)
    print(f"Loaded {len(rows)} rows from {args.csv}")

    # collect labels to ensure
    all_labels = set()
    for r in rows:
        if r.get("Labels"):
            for lab in [x.strip() for x in r["Labels"].split(",")]:
                if lab: all_labels.add(lab)
        if r.get("Type"): all_labels.add(r["Type"])
        if r.get("Priority"): all_labels.add(r["Priority"])

    milestone_number = None
    if args.milestone:
        if not args.dry_run:
            milestone_number = ensure_milestone(owner, repo, args.milestone, token)
        else:
            print(f"[DRY RUN] would ensure milestone '{args.milestone}'")
    if not args.dry_run:
        ensure_labels(owner, repo, token, all_labels)
    else:
        print(f"[DRY RUN] would ensure labels: {sorted(all_labels)}")

    id_to_issue = {}
    parent_to_children = defaultdict(list)
    deps_map = defaultdict(list)

    # First pass: create issues
    for r in rows:
        iid = r.get("ID")
        title = r.get("Title") or "(no title)"
        typ = r.get("Type", "")
        prio = r.get("Priority", "")
        est = r.get("Estimate", "")
        parent = r.get("ParentID", "")
        desc = r.get("Description", "")
        milestone = args.milestone

        header = []
        if typ: header.append(f"**Type:** {typ}")
        if prio: header.append(f"**Priority:** {prio}")
        if est: header.append(f"**Estimate:** {est} pts")
        if milestone: header.append(f"**Milestone:** {milestone}")

        body = ""
        if header:
            body += "\n".join(header) + "\n\n"
        body += desc

        labels = []
        if typ: labels.append(typ)
        if prio: labels.append(prio)
        if r.get("Labels"):
            labels += [x.strip() for x in r["Labels"].split(",") if x.strip()]

        deps = [x.strip() for x in re.split(r"[;,]", r.get("Dependencies","")) if x.strip()]
        if deps:
            deps_map[iid] = deps

        if parent:
            parent_to_children[parent].append(iid)

        api_title = title
        if args.dry_run:
            print(f"[DRY RUN] Would create issue: {api_title} | labels={labels}")
            id_to_issue[iid] = f"DRY-{iid}"
        else:
            issue = create_issue(owner, repo, token, api_title, body, labels, milestone_number, [args.assignee] if args.assignee else [])
            id_to_issue[iid] = issue["number"]
            print(f"Created #{issue['number']}: {api_title}")

    if args.dry_run:
        print("[DRY RUN] Skipping linking and body updates")
        return

    # Second pass: add dependencies and parent/child links
    for r in rows:
        iid = r.get("ID")
        issue_number = id_to_issue.get(iid)
        if not issue_number: continue

        deps = deps_map.get(iid, [])
        extra = ""
        if deps:
            issue_refs = [f"#{id_to_issue[d]}" for d in deps if d in id_to_issue]
            if issue_refs:
                extra += "\n\n### Dependencies\n" + "\n".join([f"- [ ] Blocked by {ref}" for ref in issue_refs])

        parent = r.get("ParentID", "")
        if parent and parent in id_to_issue:
            extra += f"\n\n**Parent:** #{id_to_issue[parent]}"

        if extra:
            get_r = requests.get(f"{API}/repos/{owner}/{repo}/issues/{issue_number}", headers=gh_headers(token))
            get_r.raise_for_status()
            current_body = get_r.json().get("body") or ""
            update_issue(owner, repo, token, issue_number, body=current_body + extra)

    # Update parent bodies with subtasks list
    for parent_id, children in parent_to_children.items():
        parent_num = id_to_issue.get(parent_id)
        if not parent_num: continue
        get_r = requests.get(f"{API}/repos/{owner}/{repo}/issues/{parent_num}", headers=gh_headers(token))
        get_r.raise_for_status()
        current = get_r.json()
        current_body = current.get("body") or ""
        lines = ["\n\n### Subtasks"]
        for child_id in children:
            child_num = id_to_issue.get(child_id)
            ct = next((r["Title"] for r in rows if r["ID"] == child_id), f"Issue {child_num}")
            lines.append(f"- [ ] #{child_num} — {ct}")
        update_issue(owner, repo, token, parent_num, body=current_body + "\n".join(lines))

if __name__ == "__main__":
    main()
