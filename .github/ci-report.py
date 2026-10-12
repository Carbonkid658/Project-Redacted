"""Publishes compiler errors from a failed build as a check run on the commit, so they can be read
through the GitHub API without downloading the raw log."""
import json, os, re, sys, urllib.request

def emit(title, text, level='notice'):
    """Publishes text as extra check runs on the commit (annotations are capped at 4 KB)."""
    chunks = [text[i:i + 60000] for i in range(0, len(text), 60000)] or ['(empty)']
    for n, c in enumerate(chunks[:20]):
        body = json.dumps({
            'name': f'report: {title} {n + 1}/{len(chunks)}',
            'head_sha': os.environ['GITHUB_SHA'],
            'status': 'completed',
            'conclusion': 'neutral',
            'output': {'title': title, 'summary': f'part {n + 1}', 'text': c},
        }).encode()
        req = urllib.request.Request(
            f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}/check-runs",
            data=body, method='POST', headers={
                'Authorization': f"Bearer {os.environ['GITHUB_TOKEN']}",
                'Accept': 'application/vnd.github+json'})
        urllib.request.urlopen(req).read()

mode = sys.argv[1]
if mode == 'errors':
    lines = open('build.log', errors='replace').read().splitlines()
    keep = []
    for i, l in enumerate(lines):
        if re.search(r': error:|: warning: \[removal\]|What went wrong|Could not |FAILURE', l):
            keep.append(l.replace(os.getcwd() + '/', ''))
            if 'What went wrong' in l:
                keep.extend('    ' + x for x in lines[i + 1:i + 15])
            for nxt in lines[i + 1:i + 4]:
                if nxt.strip().startswith(('symbol', 'location', '>', 'required', 'found', 'reason')):
                    keep.append('    ' + nxt.strip())
    emit('Errors', '\n'.join(keep + ['', '--- log tail ---'] + lines[-80:]), 'error')
